package org.label.translate.labeltranslate

import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
import java.io.File
import java.io.IOException
import javax.swing.*
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

data class PreviousState(val scrollPosition: Int, val errorFilter: Boolean)

class LabelTranslateToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        for (initialPath in TranslationSet.getResourcePaths()) {
            for (translationSet in TranslationSet.loadFromPath(project.basePath, initialPath)) {
                val toolWindowContent = LabelTranslateToolWindowContent(translationSet, project, toolWindow)
                val content = ContentFactory.getInstance().createContent(
                    toolWindowContent.contentPanel,
                    translationSet.displayName,
                    false
                )
                toolWindow.contentManager.addContent(content)
            }
        }
    }
}

class LabelTranslateToolWindowContent(
    private var translationSet: TranslationSet,
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val initialErrorFilter: Boolean = false,
) {
    val contentPanel = JPanel()
    private val mutationObserver = MutationObserver()
    private var table: JBTable? = null
    private var sorter: TableRowSorter<TableModel>? = null
    private var scrollPane: JBScrollPane? = null
    private var currentPath: String = ""
    val pathComboBox = ComboBox<String>()
    private var reloadDebounceTimer: Timer? = null
    private var errorFilterActive: Boolean = initialErrorFilter

    init {
        currentPath = translationSet.path
        contentPanel.layout = BorderLayout()

        table = createTable(translationSet)
        contentPanel.add(createButtonPanel(table!!), BorderLayout.NORTH)

        scrollPane = JBScrollPane(table)
        contentPanel.add(scrollPane!!, BorderLayout.CENTER)

        if (initialErrorFilter) getEmptyCellSorter()

        // Herlaad de tabel automatisch als vertaalbestanden op disk wijzigen (bv. na git branch switch)
        ApplicationManager.getApplication().messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    // Stale instances overslaan
                    val selectedContent = toolWindow.contentManager.selectedContent ?: return
                    if (selectedContent.component != contentPanel) return

                    val basePath = project.basePath ?: return
                    val translationRoot = if (File(currentPath).isAbsolute)
                        File(currentPath) else File(basePath, currentPath)

                    val hasRelevantChange = events.any { event ->
                        event.path.startsWith(translationRoot.path) && event.path.endsWith(".php")
                    }
                    if (!hasRelevantChange) return

                    // Debounce: bij een git branch switch komen er tientallen events tegelijk
                    reloadDebounceTimer?.stop()
                    reloadDebounceTimer = Timer(500) {
                        val selectedTabName = toolWindow.contentManager.selectedContent?.displayName
                        reloadTranslationsForPath(currentPath, selectedTabName)
                    }.also {
                        it.isRepeats = false
                        it.start()
                    }
                }
            }
        )
    }

    private fun addKey(key: String) {
        val model = this.table?.model as? GroupedTranslationTableModel ?: return
        mutationObserver.addRowMutation(key)
        model.addDataRow(key)

        val modelRow = (0 until model.rowCount).firstOrNull { model.getFullKey(it) == key } ?: return
        try {
            val viewRow = sorter!!.convertRowIndexToView(modelRow)
            val cellRect = table?.getCellRect(viewRow, 0, true)
            table?.scrollRectToVisible(cellRect)
        } catch (_: Exception) {}
    }

    private fun createButtonPanel(table: JBTable): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()

        val buttonContainer = JPanel(FlowLayout(FlowLayout.CENTER))
        panel.add(buttonContainer, BorderLayout.WEST)

        val checkboxContainer = JPanel(FlowLayout(FlowLayout.CENTER))
        panel.add(checkboxContainer, BorderLayout.EAST)

        val searchField = JTextField(15)
        searchField.toolTipText = PluginI18n.t("tooltip.search")
        searchField.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyReleased(e: KeyEvent) {
                val searchText = searchField.text.trim().lowercase()
                applyTableFilter(searchText)
            }
        })
        buttonContainer.add(JLabel(PluginI18n.t("label.search")))
        buttonContainer.add(searchField)

        val addButton = JButton(PluginI18n.t("button.add"))
        addButton.addActionListener {
            val tableModel = table.model as? GroupedTranslationTableModel
            val groups = tableModel?.getGroupNames() ?: emptyList()
            val result = AddDialogWrapper(groups, keyExists = { tableModel?.containsKey(it) == true })
            if (result.showAndGet()) {
                val key = result.fullKey
                if (key.isNotBlank()) {
                    addKey(key)
                }
            }
        }
        buttonContainer.add(addButton)

        val refreshButton = JButton(PluginI18n.t("button.refresh"))

        refreshButton.addActionListener {
            val currentContentManager = toolWindow.contentManager
            val selectedTabName = currentContentManager.selectedContent?.displayName ?: return@addActionListener
            reloadTranslationsForPath(currentPath, selectedTabName);
        }

        buttonContainer.add(refreshButton)

        val saveButton = JButton(PluginI18n.t("button.save"))
        saveButton.addActionListener {
            try {
                val saveContext = SaveContext(translationSet, mutationObserver)
                saveContext.overwriteChanges()
                val currentContentManager = toolWindow.contentManager
                val selectedTabName = currentContentManager.selectedContent?.displayName ?: return@addActionListener
                reloadTranslationsForPath(currentPath, selectedTabName)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(null, "${PluginI18n.t("error.save")} ${e.message}")
                e.printStackTrace()
            }
        }
        buttonContainer.add(saveButton)

        val translateButton = JButton(PluginI18n.t("button.translate"))
        translateButton.addActionListener {
            val tableModel = table.model as? GroupedTranslationTableModel ?: return@addActionListener
            val groups = tableModel.getGroupNames()
            val translateDialog = TranslateDialogWrapper(groups, keyExists = { tableModel.containsKey(it) })
            if (translateDialog.showAndGet()) {
                val key = translateDialog.fullKey
                val wordToTranslate = translateDialog.wordToTranslateField?.text ?: ""
                val sourceLang = translateDialog.selectedLanguage
                val selectedOption = translateDialog.langComboBox.selectedItem as? String ?: ""
                if (key.isNotBlank() && wordToTranslate.isNotBlank()) {
                    RecentLanguagesConfig().push(selectedOption)
                    translateWord(key, wordToTranslate, sourceLang, tableModel, mutationObserver)
                }
            }
        }

        if (useApiKey() != "false") {
            buttonContainer.add(translateButton)
        }

        val paths = listOf("resources/lang", "lang") + CustomFilePathConfig().folderPaths
        paths.forEach { pathComboBox.addItem(it) }

        pathComboBox.selectedItem = currentPath
        pathComboBox.addActionListener {
            currentPath = pathComboBox.selectedItem as String
            reloadTranslationsForPath(currentPath)
        }
        buttonContainer.add(JLabel(PluginI18n.t("label.path")))
        buttonContainer.add(pathComboBox)

        // Luister naar wijzigingen in settings via MessageBus.
        // We controleren of dit de geselecteerde tab is om dubbele reloads te voorkomen
        // (elke tab subscribet, maar alleen de actieve hoeft te handelen).
        com.intellij.openapi.application.ApplicationManager.getApplication().messageBus.connect().subscribe(
            SettingsChangedNotifier.TOPIC,
            object : SettingsChangedNotifier {
                override fun onFolderPathsChanged() {
                    // Stale subscriptions van vervangen content-instances overslaan
                    val selectedContent = toolWindow.contentManager.selectedContent ?: return
                    if (selectedContent.component != contentPanel) return

                    val basePath = project.basePath
                    val newPaths = listOf("resources/lang", "lang") + CustomFilePathConfig().folderPaths

                    // Bepaal een geldig pad: houd currentPath als dat nog bestaat, anders fallback
                    val pathToLoad = if (newPaths.contains(currentPath) &&
                        (if (File(currentPath).isAbsolute) File(currentPath) else File(basePath, currentPath)).exists()
                    ) {
                        currentPath
                    } else {
                        newPaths.firstOrNull {
                            val f = if (File(it).isAbsolute) File(it) else File(basePath, it)
                            f.exists()
                        }
                    }

                    if (pathToLoad == null) {
                        JOptionPane.showMessageDialog(null, PluginI18n.t("error.no_paths"))
                        return
                    }

                    currentPath = pathToLoad
                    val selectedTabName = toolWindow.contentManager.selectedContent?.displayName
                    reloadTranslationsForPath(currentPath, selectedTabName)
                }
            })

        val displayCheckbox = JBCheckBox(PluginI18n.t("checkbox.errors"))
        displayCheckbox.isSelected = errorFilterActive
        displayCheckbox.border = BorderFactory.createEmptyBorder(6, 0, 0, 0)
        displayCheckbox.addItemListener {
            errorFilterActive = it.stateChange == ItemEvent.SELECTED
            if (errorFilterActive) getEmptyCellSorter() else sorter?.rowFilter = null
        }
        checkboxContainer.add(displayCheckbox)
        checkboxContainer.add(Box.createRigidArea(Dimension(10, 0)))

        return panel
    }

    private fun applyTableFilter(searchText: String) {
        sorter?.rowFilter = if (searchText.isBlank()) {
            null
        } else {
            object : RowFilter<TableModel, Int>() {
                override fun include(entry: Entry<out TableModel, out Int>): Boolean {
                    // Groep-header rijen altijd verbergen bij zoeken (ze hebben geen waarden)
                    val m = entry.model as? GroupedTranslationTableModel
                    if (m?.isGroupHeader(entry.identifier) == true) return false
                    for (i in 0 until entry.valueCount) {
                        if (entry.getStringValue(i).lowercase().contains(searchText)) {
                            return true
                        }
                    }
                    return false
                }
            }
        }
    }

    private fun getEmptyCellSorter() {
        sorter?.rowFilter = object : RowFilter<TableModel, Any>() {
            override fun include(entry: Entry<out TableModel, out Any>?): Boolean {
                if (entry == null) return false
                // Groep-header rijen overslaan bij error-filter
                val m = entry.model as? GroupedTranslationTableModel
                val rowIdx = entry.identifier as? Int
                if (m != null && rowIdx != null && m.isGroupHeader(rowIdx)) return false

                for (i in 1 until entry.valueCount) { // kolom 0 (key) overslaan
                    if (entry.getStringValue(i).isBlank()) {
                        return true
                    }
                }
                return false
            }
        }
    }

    private fun handleEvent(tableModel: GroupedTranslationTableModel, row: Int, column: Int) {
        val key = tableModel.getFullKey(row) ?: return // groep-header rijen overslaan
        val oldValue = translationSet.listTranslationsFor(key)[column - 1]
        val newValue = tableModel.getValueAt(row, column) as String
        if (oldValue == newValue) {
            mutationObserver.removeIndex(key, column - 1)
        } else {
            mutationObserver.addMutation(key, column - 1, newValue)
        }
    }

    private fun createTable(translationSet: TranslationSet): JBTable {
        val columnNames = arrayOf("Key", *translationSet.listLanguages())
        val tableModel = GroupedTranslationTableModel(columnNames)
        tableModel.loadFrom(translationSet)

        tableModel.addTableModelListener {
            if (it.column > 0) {
                handleEvent(tableModel, it.firstRow, it.column)
            }
        }

        val table = object : JBTable(tableModel) {
            override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
                val modelRow = convertRowIndexToModel(row)
                val m = model as? GroupedTranslationTableModel
                return when {
                    m?.isGroupHeader(modelRow) == true -> GroupHeaderRenderer()
                    column == 0 -> KeyCellRenderer(mutationObserver)
                    else -> EmptyCellRenderer(mutationObserver)
                }
            }

            override fun isCellEditable(row: Int, column: Int): Boolean {
                if (column == 0) return false
                val modelRow = convertRowIndexToModel(row)
                return (model as? GroupedTranslationTableModel)?.isGroupHeader(modelRow) == false
            }
        }

        sorter = TableRowSorter(tableModel)
        sorter?.setSortable(0, false)
        sorter?.setComparator(0, Comparator { a: Any, b: Any ->
            val sep = SeparatorConfig().separator
            fun sortKey(s: String): String = when {
                s.startsWith("\u0001") -> s.substring(1).replace(sep, "\uFFFF") + "\uFFFF"
                s.contains(sep) -> s.replace(sep, "\uFFFF")
                else -> s
            }
            sortKey(a.toString()).compareTo(sortKey(b.toString()), ignoreCase = true)
        })
        table.rowSorter = sorter
        sorter?.sortKeys = listOf(RowSorter.SortKey(0, SortOrder.ASCENDING))

        val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)
        table.getInputMap(JComponent.WHEN_FOCUSED).put(keyStroke, "deleteAction")
        table.actionMap.put("deleteAction", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val modelRow = table.convertRowIndexToModel(table.selectedRow)
                val key = tableModel.getFullKey(modelRow) ?: return
                if (DeleteDialogWrapper(key).showAndGet()) {
                    mutationObserver.addDeletion(key)
                }
            }
        })

        // Right-click context menu voor hernoemen en verwijderen
        table.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) { handlePopup(e) }
            override fun mouseReleased(e: java.awt.event.MouseEvent) { handlePopup(e) }

            private fun handlePopup(e: java.awt.event.MouseEvent) {
                if (!e.isPopupTrigger) return
                val viewRow = table.rowAtPoint(e.point)
                if (viewRow < 0) return
                table.setRowSelectionInterval(viewRow, viewRow)
                val modelRow = table.convertRowIndexToModel(viewRow)

                val popup = JPopupMenu()
                val groupPrefix = tableModel.getGroupPrefix(modelRow)

                if (groupPrefix != null) {
                    // Groep-header rij: groep hernoemen
                    val renameGroupItem = JMenuItem(PluginI18n.t("menu.rename_group"))
                    renameGroupItem.addActionListener {
                        val sep = SeparatorConfig().separator
                        val lastSep = groupPrefix.lastIndexOf(sep)
                        val parentPrefix = if (lastSep >= 0) groupPrefix.substring(0, lastSep) else ""
                        val currentSegment = if (lastSep >= 0) groupPrefix.substring(lastSep + sep.length) else groupPrefix

                        val newSegment = javax.swing.JOptionPane.showInputDialog(
                            table, PluginI18n.t("dialog.rename_group.prompt"), currentSegment
                        )?.trim() ?: return@addActionListener

                        val newPrefix = if (parentPrefix.isEmpty()) newSegment else "$parentPrefix$sep$newSegment"
                        if (newSegment.isNotBlank() && newPrefix != groupPrefix) {
                            tableModel.getKeysForPrefix(groupPrefix).forEach { key ->
                                val newKey = newPrefix + key.substring(groupPrefix.length)
                                mutationObserver.renameKey(key, newKey)
                            }
                            tableModel.renameGroup(groupPrefix, newPrefix)
                        }
                    }
                    popup.add(renameGroupItem)
                    popup.show(e.component, e.x, e.y)
                    return
                }

                val key = tableModel.getFullKey(modelRow) ?: return

                val renameItem = JMenuItem(PluginI18n.t("menu.rename_key"))
                renameItem.addActionListener {
                    val groups = tableModel.getGroupNames()
                    val sep = SeparatorConfig().separator
                    val lastSep = key.lastIndexOf(sep)
                    val initialGroup = if (lastSep >= 0) key.substring(0, lastSep) else ""
                    val initialSubKey = if (lastSep >= 0) key.substring(lastSep + sep.length) else key
                    val dialog = AddDialogWrapper(
                        existingGroups = groups,
                        initialGroup = initialGroup,
                        initialKey = initialSubKey,
                        dialogTitle = PluginI18n.t("rename.title"),
                        keyExists = { it != key && tableModel.containsKey(it) }
                    )
                    if (dialog.showAndGet()) {
                        val newKey = dialog.fullKey
                        if (newKey.isNotBlank() && newKey != key) {
                            mutationObserver.renameKey(key, newKey)
                            tableModel.renameDataRow(key, newKey)
                        }
                    }
                }
                popup.add(renameItem)

                val deleteItem = JMenuItem(PluginI18n.t("menu.delete_key"))
                deleteItem.addActionListener {
                    if (DeleteDialogWrapper(key).showAndGet()) {
                        mutationObserver.addDeletion(key)
                    }
                }
                popup.add(deleteItem)

                popup.show(e.component, e.x, e.y)
            }
        })

        return table
    }

    fun getDefaultLang(): String {
        return DefaultLanguage().defaultLanguage
    }

    fun useApiKey(): String {
        val apiKeyConfig = ApiKeyConfig()
        val apiKey = apiKeyConfig.apiKey

        if (apiKey.isEmpty()) {
            return "false"
        }

        return apiKey
    }
//    "max_tokens": ${ApiKeyConfig().maxTokens}

    fun translateWord(
        key: String,
        wordToTranslate: String,
        sourceLang: String,
        tableModel: GroupedTranslationTableModel,
        mutationObserver: MutationObserver
    ) {
        val client = OkHttpClient()

        val sourceLangUpper = sourceLang.uppercase()
        val languages = mutableListOf<String>()
        for (col in 0 until tableModel.columnCount) {
            val columnName = tableModel.getColumnName(col).uppercase()
            if (columnName != "KEY") languages.add(columnName)
        }

        val startsWithCapital = wordToTranslate.firstOrNull()?.isUpperCase() == true
        val capitalizationInstruction = if (startsWithCapital)
            "The original word starts with a capital letter, so all translations must also start with a capital letter. "
        else
            "The original word starts with a lowercase letter, so all translations must also start with a lowercase letter. "

        val targetLanguages = languages.filter { it != sourceLangUpper }
        val languagesJson = languages.joinToString(", ") { "\\\"$it\\\": {\\\"$key\\\": \\\"translation\\\"}" }
        val jsonContent = """
        Translate the \"$sourceLangUpper\" word/sentence \"$wordToTranslate\" into the following languages: ${targetLanguages.joinToString(", ")}.
        Also return \"$sourceLangUpper\" in the response with the spelling-corrected version of the original word.
        $capitalizationInstruction
        Use this as a key \"$key\". Please return all languages in the following structured JSON format: {$languagesJson}
        and only return that, not other text.
    """.trimIndent().replace("\n", " ")

        val json = """
        {
            "model": "gpt-5.4-mini",
            "messages": [
                {"role": "system", "content": "You are a helpful assistant."},
                {"role": "user", "content": "$jsonContent"}
            ],
            "max_completion_tokens": ${ApiKeyConfig().maxTokens}
        }
    """.trimIndent()

        val requestBody: RequestBody = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(requestBody)
            .addHeader("Authorization", "Bearer ${useApiKey()}")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                JOptionPane.showMessageDialog(null, "Error during translation: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    try {
                        val responseBody = response.body?.string() ?: ""

                        if (!response.isSuccessful) {
                            JOptionPane.showMessageDialog(
                                null,
                                "Unexpected response code: ${response.code}\nResponse: $responseBody"
                            )
                            return
                        }

                        val jsonElement = JsonParser.parseString(responseBody)
                        val jsonObject = jsonElement.asJsonObject

                        val content =
                            jsonObject["choices"].asJsonArray[0].asJsonObject["message"].asJsonObject["content"].asString

                        val cleanedContent = content.trim().removePrefix("`json").removeSuffix("`").trim()

                        val parsedJson = JsonParser.parseString(cleanedContent).asJsonObject

                        SwingUtilities.invokeLater {
                            try {
                                mutationObserver.addRowMutation(key)

                                val rowArray = arrayOfNulls<Any>(tableModel.columnCount)

                                for (col in 0 until tableModel.columnCount) {
                                    val columnName = tableModel.getColumnName(col).uppercase()

                                    when {
                                        columnName == "KEY" -> rowArray[col] = key
                                        parsedJson.has(columnName) -> {
                                            var translation = parsedJson[columnName].asJsonObject[key].asString
                                            if (translation.isNotEmpty()) {
                                                translation = if (startsWithCapital)
                                                    translation[0].uppercaseChar() + translation.substring(1)
                                                else
                                                    translation[0].lowercaseChar() + translation.substring(1)
                                            }
                                            rowArray[col] = translation
                                        }
                                    }
                                }

                                // Voeg de rij toe aan het gegroepeerde model met initiële waarden
                                val valueArray = rowArray.drop(1).toTypedArray()
                                tableModel.addDataRow(key, valueArray)

                                for (i in 0 until tableModel.columnCount - 1) {
                                    mutationObserver.addMutation(key, i, rowArray.get(i + 1) as? String ?: "")
                                }

                                val modelRow = (0 until tableModel.rowCount).firstOrNull { tableModel.getFullKey(it) == key }
                                if (modelRow != null) {
                                    try {
                                        val viewRow = sorter?.convertRowIndexToView(modelRow) ?: modelRow
                                        val cellRect = table?.getCellRect(viewRow, 0, true)
                                        table?.scrollRectToVisible(cellRect)
                                    } catch (_: Exception) {}
                                }
                            } catch (e: Exception) {
                                JOptionPane.showMessageDialog(null, "Error updating table: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    } catch (e: Exception) {
                        JOptionPane.showMessageDialog(null, "Error processing response: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    private fun reloadTranslationsForPath(path: String, keepSelectedTab: String? = null) {
        val basePath = project.basePath
        val translationRoot = if (File(path).isAbsolute) File(path) else File(basePath, path)

        if (!translationRoot.exists()) {
            JOptionPane.showMessageDialog(null, "No translations found for path: $path")
            return
        }

        val loadedTranslationSets = TranslationSet.loadFromPath(basePath, translationRoot.path)

        if (loadedTranslationSets.isNotEmpty()) {
            toolWindow.contentManager.removeAllContents(true)

            for (translationSet in loadedTranslationSets) {
                val content = LabelTranslateToolWindowContent(translationSet, project, toolWindow, errorFilterActive)
                val tab = ContentFactory.getInstance().createContent(
                    content.contentPanel,
                    translationSet.displayName,
                    false
                )
                toolWindow.contentManager.addContent(tab)
            }

            keepSelectedTab?.let { displayName ->
                val tabToSelect = toolWindow.contentManager.contents.find {
                    it.displayName.equals(displayName, ignoreCase = true)
                }
                if (tabToSelect != null) {
                    toolWindow.contentManager.setSelectedContent(tabToSelect)
                }
            }
        } else {
            JOptionPane.showMessageDialog(null, "No translations found for path: ${translationRoot.path}")
        }
    }
}
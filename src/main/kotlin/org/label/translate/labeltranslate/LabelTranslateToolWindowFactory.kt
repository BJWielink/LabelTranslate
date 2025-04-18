package org.label.translate.labeltranslate

import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
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
import java.io.IOException
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

data class PreviousState(val scrollPosition: Int, val errorFilter: Boolean)

class LabelTranslateToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Load for *all* initial paths to create tabs:
        for (initialPath in TranslationSet.RESOURCE_PATHS) {
            val translationSets = TranslationSet.loadFromPath(project.basePath, initialPath)
            if (translationSets.isNotEmpty()) { // Handle cases where there might be no files
                val translationSet = translationSets[0]
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
    private var translationSet: TranslationSet, // Make translationSet mutable
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val previousState: PreviousState? = null
) {
    val contentPanel = JPanel()
    private val mutationObserver = MutationObserver()
    private var table: JBTable? = null
    private var sorter: TableRowSorter<TableModel>? = null
    private var scrollPane: JBScrollPane? = null
    private var currentPath: String = ""

    init {
        currentPath = translationSet.path
        contentPanel.layout = BorderLayout()

        table = createTable(translationSet)
        contentPanel.add(createButtonPanel(table!!), BorderLayout.NORTH)

        scrollPane = JBScrollPane(table)
        contentPanel.add(scrollPane!!, BorderLayout.CENTER)
    }

    private fun addKey(key: String) {
        val model = this.table?.model as DefaultTableModel? ?: return
        mutationObserver.addRowMutation(key)
        model.addRow(arrayOf(key))

        var index = -1
        for (i in 0 until model.rowCount) {
            val value = model.getValueAt(i, 0)
            if (value == key) {
                index = sorter!!.convertRowIndexToView(i)
                break
            }
        }

        if (index != -1) {
            val cellRect = table?.getCellRect(index, 0, true)
            table?.scrollRectToVisible(cellRect)
        }
    }

    private fun createButtonPanel(table: JBTable): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()

        val buttonContainer = JPanel(FlowLayout(FlowLayout.CENTER))
        panel.add(buttonContainer, BorderLayout.WEST)

        val checkboxContainer = JPanel(FlowLayout(FlowLayout.CENTER))
        panel.add(checkboxContainer, BorderLayout.EAST)

        val searchField = JTextField(15)
        searchField.toolTipText = "Search by key or translation"
        searchField.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyReleased(e: KeyEvent) {
                val searchText = searchField.text.trim().toLowerCase()
                applyTableFilter(searchText)
            }
        })
        buttonContainer.add(JLabel("Search: "))
        buttonContainer.add(searchField)

        val addButton = JButton("Add")
        addButton.addActionListener {
            val result = AddDialogWrapper()
            if (result.showAndGet()) {
                val key = result.textField?.text ?: ""
                if (key.isNotBlank()) {
                    addKey(key)
                }
            }
        }
        buttonContainer.add(addButton)

        val refreshButton = JButton("Refresh")

        refreshButton.addActionListener {
            val currentContentManager = toolWindow.contentManager
            val selectedTabName = currentContentManager.selectedContent?.displayName ?: return@addActionListener
            reloadTranslationsForPath(currentPath, selectedTabName);
        }

        buttonContainer.add(refreshButton)

        val saveButton = JButton("Save")
        saveButton.addActionListener {
            try {
                val saveContext = SaveContext(translationSet, mutationObserver)
                saveContext.overwriteChanges()
                val currentContentManager = toolWindow.contentManager
                val selectedTabName = currentContentManager.selectedContent?.displayName ?: return@addActionListener
                reloadTranslationsForPath(currentPath, selectedTabName)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(null, "Error during save: ${e.message}")
                e.printStackTrace()
            }
        }
        buttonContainer.add(saveButton)

        val translateButton = JButton("Translate")
        translateButton.addActionListener {
            val translateDialog = TranslateDialogWrapper()
            if (translateDialog.showAndGet()) {
                val key = translateDialog.keyField?.text ?: ""
                val wordToTranslate = translateDialog.wordToTranslateField?.text ?: ""
                if (key.isNotBlank() && wordToTranslate.isNotBlank()) {
                    translateWord(key, wordToTranslate, table!!.model as DefaultTableModel, mutationObserver)
                }
            }
        }

        if (useApiKey() != "false") {
            buttonContainer.add(translateButton)
        }

        val pathComboBox = ComboBox<String>()
        TranslationSet.RESOURCE_PATHS.forEach { pathComboBox.addItem(it) }
        pathComboBox.selectedItem = currentPath
        pathComboBox.addActionListener {
            currentPath = pathComboBox.selectedItem as String
            reloadTranslationsForPath(currentPath)
        }
        buttonContainer.add(JLabel("Path: "))
        buttonContainer.add(pathComboBox)

        val displayCheckbox = JBCheckBox("Errors")
        displayCheckbox.border = BorderFactory.createEmptyBorder(6, 0, 0, 0)
        displayCheckbox.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                getEmptyCellSorter()
            } else {
                sorter?.rowFilter = null
            }
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
                    for (i in 0 until entry.valueCount) {
                        if (entry.getStringValue(i).toLowerCase().contains(searchText)) {
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
                if (entry == null) {
                    return false
                }

                for (i in 0 until entry.valueCount) {
                    if (entry.getStringValue(i).isBlank()) {
                        return true
                    }
                }

                return false
            }
        }
    }

    private fun handleEvent(tableModel: DefaultTableModel, row: Int, column: Int) {
        val key = tableModel.getValueAt(row, 0) as String
        val oldValue = translationSet.listTranslationsFor(key)[column - 1]
        val newValue = tableModel.getValueAt(row, column) as String
        if (oldValue == newValue) {
            mutationObserver.removeIndex(key, column - 1)
        } else {
            mutationObserver.addMutation(key, column - 1, newValue)
        }
    }

    private fun createTable(translationSet: TranslationSet): JBTable {
        val keys = translationSet.getKeys().map { key ->
            arrayOf(key, *translationSet.listTranslationsFor(key))
        }.toTypedArray()
        val columnNames = arrayOf("Key", *translationSet.listLanguages())

        val tableModel = DefaultTableModel(keys, columnNames)
        tableModel.addTableModelListener {
            if (it.column > 0) {
                handleEvent(tableModel, it.firstRow, it.column)
            }
        }

        val table = object : JBTable(tableModel) {
            override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
                return EmptyCellRenderer(mutationObserver)
            }

            override fun isCellEditable(row: Int, column: Int): Boolean {
                return column != 0
            }
        }

        sorter = TableRowSorter(table.model)
        table.rowSorter = sorter
        sorter?.sortKeys = listOf(RowSorter.SortKey(0, SortOrder.ASCENDING))

        val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)
        table.getInputMap(JComponent.WHEN_FOCUSED).put(keyStroke, "deleteAction")
        table.actionMap.put("deleteAction", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val key = table.getValueAt(table.selectedRow, 0) as String
                if (DeleteDialogWrapper(key).showAndGet()) {
                    mutationObserver.addDeletion(key)
                }
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

    fun translateWord(
        key: String,
        wordToTranslate: String,
        tableModel: DefaultTableModel,
        mutationObserver: MutationObserver
    ) {
        val client = OkHttpClient()

        val languages = mutableListOf<String>()
        val totalLanguage = tableModel.columnCount - 1
        for (col in 0 until tableModel.columnCount) {
            val columnName = tableModel.getColumnName(col).toUpperCase()
            if (columnName != "KEY" && columnName != getDefaultLang()) {
                languages.add(columnName)
            }
        }

        val languagesJson = languages.joinToString(", ") { "\\\"$it\\\": {\\\"$key\\\": \\\"translation\\\"}" }
        val jsonContent = """
        Translate the \"${getDefaultLang()}\" word/sentence \"$wordToTranslate\" into the following languages: ${
            languages.joinToString(
                ", "
            )
        }.
        Use this as a key \"$key\". Please return the translations in the following structured JSON format: {$languagesJson}
        and only return that, not other text.
    """.trimIndent().replace("\n", " ")

        val json = """
        {
            "model": "gpt-4o-mini",
            "messages": [
                {"role": "system", "content": "You are a helpful assistant."},
                {"role": "user", "content": "$jsonContent"}
            ],
            "max_tokens": ${ApiKeyConfig().maxTokens}
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
                                    val columnName = tableModel.getColumnName(col).toUpperCase()

                                    when {
                                        columnName == "KEY" -> rowArray[col] = key
                                        columnName == getDefaultLang() -> rowArray[col] = wordToTranslate
                                        parsedJson.has(columnName) -> {
                                            rowArray[col] = parsedJson[columnName].asJsonObject[key].asString
                                        }
                                    }
                                }

                                tableModel.addRow(rowArray)

                                val newIndex = (0 until tableModel.rowCount).firstOrNull {
                                    val existingKey = tableModel.getValueAt(it, 0)
                                    existingKey.toString() >= key
                                } ?: tableModel.rowCount

                                for (i in 0 until totalLanguage) {
                                    mutationObserver.addMutation(key, i, rowArray.get(i + 1) as String)
                                }

                                val cellRect = table?.getCellRect(newIndex, 0, true)
                                table?.scrollRectToVisible(cellRect)
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
        val loadedTranslationSets = TranslationSet.loadFromPath(project.basePath, path)

        if (loadedTranslationSets.isNotEmpty()) {
            toolWindow.contentManager.removeAllContents(true)

            for (translationSet in loadedTranslationSets) {
                val content = LabelTranslateToolWindowContent(translationSet, project, toolWindow)
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
            JOptionPane.showMessageDialog(null, "No translations found for path: $path")
        }
    }
}
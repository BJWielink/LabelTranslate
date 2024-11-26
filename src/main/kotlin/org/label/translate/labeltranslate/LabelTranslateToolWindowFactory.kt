package org.label.translate.labeltranslate

import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
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
        val translationSet = TranslationSet.loadFromPath(project.basePath)

        for (tab in translationSet) {
            val toolWindowContent = LabelTranslateToolWindowContent(tab, project, toolWindow)
            val content =
                ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, tab.displayName, false)
            toolWindow.contentManager.addContent(content)
        }
    }
}

class LabelTranslateToolWindowContent(
    private val translationSet: TranslationSet,
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val previousState: PreviousState? = null
) {
    val contentPanel = JPanel()
    private val mutationObserver = MutationObserver()
    private var table: JBTable? = null
    private var sorter: TableRowSorter<TableModel>? = null
    private var scrollPane: JBScrollPane? = null

    init {
        contentPanel.layout = BorderLayout()
        table = createTable()
        contentPanel.add(createButtonPanel(table!!), BorderLayout.NORTH)
        contentPanel.add(createTablePanel(table!!), BorderLayout.CENTER)
    }

    private fun addKey(key: String) {
        val model = this.table?.model as DefaultTableModel? ?: return
        mutationObserver.addRowMutation(key)
        model.addRow(arrayOf(key))

        // Find index of the new record
        var index = -1
        for (i in 0 until model.rowCount) {
            val value = model.getValueAt(i, 0)

            if (value == key) {
                index = sorter!!.convertRowIndexToView(i)
                break
            }
        }

        // Scroll to the new record
        if (index != -1) {
            val cellRect = table?.getCellRect(index, 0, true)
            table?.scrollRectToVisible(cellRect)
        }
    }

    private fun reloadTabsAndData() {
        val previousScroll = scrollPane!!.verticalScrollBar.value
        val previousState = PreviousState(previousScroll, true)
        val previousDisplayName = toolWindow.contentManager.selectedContent?.displayName

        toolWindow.contentManager.removeAllContents(true)

        val translationSet = TranslationSet.loadFromPath(project.basePath)

        for (tab in translationSet) {
            val toolWindowContent = if (tab.displayName == previousDisplayName) {
                LabelTranslateToolWindowContent(tab, project, toolWindow, previousState)
            } else {
                LabelTranslateToolWindowContent(tab, project, toolWindow)
            }
            val content =
                ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, tab.displayName, false)
            toolWindow.contentManager.addContent(content)
        }

        val contentBefore = toolWindow.contentManager.findContent(previousDisplayName)

        contentBefore?.let {
            toolWindow.contentManager.setSelectedContent(it)
        }
    }

    private fun createButtonPanel(table: JBTable): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()

        val buttonContainer = JPanel(FlowLayout(FlowLayout.CENTER))
        panel.add(buttonContainer, BorderLayout.WEST)

        val checkboxContainer = JPanel(FlowLayout(FlowLayout.CENTER))
        panel.add(checkboxContainer, BorderLayout.EAST)

        // Search Field
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

        // Add button
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

        // Refresh button
        val refreshButton = JButton("Refresh")
        refreshButton.addActionListener {
            reloadTabsAndData()
        }
        buttonContainer.add(refreshButton)

        // Save button
        val saveButton = JButton("Save")
        saveButton.addActionListener {
            try {
                val saveContext = SaveContext(translationSet, mutationObserver)
                saveContext.overwriteChanges()
                reloadTabsAndData()
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(null, "Error during save: ${e.message}")
                e.printStackTrace()
            }
        }
        buttonContainer.add(saveButton)

        // Translate button
        val translateButton = JButton("Translate")
        translateButton.addActionListener {
            val translateDialog = TranslateDialogWrapper()
            if (translateDialog.showAndGet()) {
                val key = translateDialog.keyField?.text ?: ""
                val dutchWord = translateDialog.dutchWordField?.text ?: ""
                if (key.isNotBlank() && dutchWord.isNotBlank()) {
                    translateWord(key, dutchWord, table.model as DefaultTableModel, mutationObserver)
                }
            }
        }

        if(useApiKey() != "false") {
            buttonContainer.add(translateButton)
        }

        // Display checkbox for errors
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
            null // Clear the filter if the search text is empty
        } else {
            object : RowFilter<TableModel, Int>() {
                override fun include(entry: Entry<out TableModel, out Int>): Boolean {
                    // Check if any column contains the search text
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

    fun useApiKey(): String {
        val apiKeyConfig = ApiKeyConfig()
        val apiKey = apiKeyConfig.apiKey // Access the API key

        if (!apiKey.isNotEmpty()) {
            return "false"
        }

        return apiKey
    }

    fun translateWord(
        key: String,
        dutchWord: String,
        tableModel: DefaultTableModel,
        mutationObserver: MutationObserver
    ) {
        val client = OkHttpClient()

        // Identify languages present in the table
        val languages = mutableListOf<String>()
        for (col in 0 until tableModel.columnCount) {
            val columnName = tableModel.getColumnName(col).toUpperCase()
            if (columnName != "KEY" && columnName != "NL") {
                languages.add(columnName)
            }
        }

        // Construct the JSON request dynamically based on these languages
        val languagesJson = languages.joinToString(", ") { "\\\"$it\\\": {\\\"$key\\\": \\\"translation\\\"}" }
        val jsonContent = """
        Translate the Dutch word \"$dutchWord\" into the following languages: ${languages.joinToString(", ")}.
        Use this as a key \"$key\". Please return the translations in the following structured JSON format: {$languagesJson}
        and only return that, not other text.
    """.trimIndent().replace("\n", " ")

        // Construct the JSON payload
        val json = """
        {
            "model": "gpt-4o-mini",
            "messages": [
                {"role": "system", "content": "You are a helpful assistant."},
                {"role": "user", "content": "$jsonContent"}
            ],
            "max_tokens": 50
        }
    """.trimIndent()

        // Convert JSON string to RequestBody
        val requestBody: RequestBody = json.toRequestBody("application/json".toMediaTypeOrNull())

        // Create the request
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(requestBody)
            .addHeader("Authorization", "Bearer ${useApiKey()}")
            .addHeader("Content-Type", "application/json")
            .build()

        // Send the request
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                JOptionPane.showMessageDialog(null, "Error during translation: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    try {
                        val responseBody = response.body?.string() ?: ""
                        println("Response code: ${response.code}")
                        println("Response body: $responseBody")

                        if (!response.isSuccessful) {
                            JOptionPane.showMessageDialog(
                                null,
                                "Unexpected response code: ${response.code}\nResponse: $responseBody"
                            )
                            return
                        }

                        // Parse the JSON response using Gson
                        val jsonElement = JsonParser.parseString(responseBody)
                        val jsonObject = jsonElement.asJsonObject

                        // Access the nested content field
                        val content =
                            jsonObject["choices"].asJsonArray[0].asJsonObject["message"].asJsonObject["content"].asString

                        // Clean the content if needed (e.g., strip backticks or extraneous text)
                        val cleanedContent = content.trim().removePrefix("```json").removeSuffix("```").trim()

                        // Parse the cleaned content as JSON
                        val parsedJson = JsonParser.parseString(cleanedContent).asJsonObject

                        // Prepare to add the key and translations to the table
                        SwingUtilities.invokeLater {
                            try {
                                mutationObserver.addRowMutation(key)

                                // Create a row array with nulls to represent all columns
                                val rowArray = arrayOfNulls<Any>(tableModel.columnCount)

                                // Fill the array with the correct translations in the correct columns
                                for (col in 0 until tableModel.columnCount) {
                                    val columnName = tableModel.getColumnName(col).toUpperCase()

                                    when {
                                        columnName == "KEY" -> rowArray[col] = key
                                        columnName == "NL" -> rowArray[col] = dutchWord
                                        parsedJson.has(columnName) -> {
                                            rowArray[col] = parsedJson[columnName].asJsonObject[key].asString
                                        }
                                    }
                                }

                                // Add the row to the table
                                tableModel.addRow(rowArray)

                                // Get the key from the newly added row
                                val newKey = key // Assuming `key` is the value you added

                                // Determine the correct index based on sorting
                                val newIndex = (0 until tableModel.rowCount).firstOrNull {
                                    val existingKey =
                                        tableModel.getValueAt(it, 0) // Assuming "KEY" is in the first column
                                    existingKey.toString() >= newKey
                                } ?: tableModel.rowCount // If not found, it goes to the end

                                //todo make this dynamic
                                mutationObserver.addMutation(key, 0, rowArray.get(1) as String)
                                mutationObserver.addMutation(key, 1, rowArray.get(2) as String)
                                mutationObserver.addMutation(key, 2, rowArray.get(3) as String)

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

    private fun getEmptyCellSorter() {
        // Sorter
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

    private fun createTable(): JBTable {
        val keys = translationSet.getKeys().map { arrayOf(it, *translationSet.listTranslationsFor(it)) }.toTypedArray()

        val tableModel = DefaultTableModel(keys, arrayOf("Key", *translationSet.listLanguages()))
        tableModel.addTableModelListener {
            if (it.column > 0) { // Skip new row added
                handleEvent(tableModel, it.firstRow, it.column)
            }
        }

        val table = object : JBTable(tableModel) {
            override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
                return EmptyCellRenderer(mutationObserver)
            }

            override fun isCellEditable(row: Int, column: Int): Boolean {
                if (column == 0) {
                    return false
                }

                return super.isCellEditable(row, column)
            }
        }

        sorter = TableRowSorter(table.model)
        table.rowSorter = sorter
        sorter?.sortKeys = listOf(RowSorter.SortKey(0, SortOrder.ASCENDING))

        // Delete button
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

        previousState?.scrollPosition?.let {
            scrollPane?.verticalScrollBar?.value = it
        }

        return table
    }

    private fun createTablePanel(table: JBTable): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        scrollPane = JBScrollPane(table)
        panel.add(scrollPane!!, BorderLayout.CENTER)

        return panel
    }
}

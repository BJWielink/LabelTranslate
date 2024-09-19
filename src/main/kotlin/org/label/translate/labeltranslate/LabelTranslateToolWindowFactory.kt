package org.label.translate.labeltranslate

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
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
            val content = ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, tab.displayName, false)
            toolWindow.contentManager.addContent(content)
        }
    }
}

class LabelTranslateToolWindowContent(private val translationSet: TranslationSet, private val project: Project, private val toolWindow: ToolWindow, private val previousState: PreviousState? = null) {
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
            val content = ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, tab.displayName, false)
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

        // Add search field for filtering
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
            val saveContext = SaveContext(translationSet, mutationObserver)
            saveContext.overwriteChanges()
            reloadTabsAndData()
        }
        buttonContainer.add(saveButton)

        // Display button for errors
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
                    // Check if any column contains the search text (case-insensitive)
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
        // Sorter
        sorter?.rowFilter = object: RowFilter<TableModel, Any>() {
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
                val key = tableModel.getValueAt(it.firstRow, 0) as String
                val oldValue = translationSet.listTranslationsFor(key)[it.column - 1]
                val newValue = tableModel.getValueAt(it.firstRow, it.column) as String
                if (oldValue == newValue) {
                    mutationObserver.removeIndex(key, it.column - 1)
                } else {
                    mutationObserver.addMutation(key, it.column - 1, newValue)
                }
            }
        }

        val table = object: JBTable(tableModel) {
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
        table.actionMap.put("deleteAction", object: AbstractAction() {
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
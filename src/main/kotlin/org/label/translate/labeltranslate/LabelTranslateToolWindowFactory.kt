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
import java.util.*
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel
import javax.swing.table.TableRowSorter

class LabelTranslateToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val translationSet = TranslationSet.loadFromPath(project.basePath)

        for (tab in translationSet) {
            val toolWindowContent = LabelTranslateToolWindowContent(tab)
            val content = ContentFactory.getInstance().createContent(toolWindowContent.contentPanel, tab.displayName, false)
            toolWindow.contentManager.addContent(content)
        }
    }
}

class LabelTranslateToolWindowContent(private val translationSet: TranslationSet) {
    val contentPanel = JPanel()
    private val mutationObserver = MutationObserver()
    private var table: JBTable? = null
    private var sorter: TableRowSorter<TableModel>? = null

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

    private fun createButtonPanel(table: JBTable): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()

        val buttonContainer = JPanel(FlowLayout(FlowLayout.CENTER))
        panel.add(buttonContainer, BorderLayout.WEST)

        val checkboxContainer = JPanel(FlowLayout(FlowLayout.CENTER))
        panel.add(checkboxContainer, BorderLayout.EAST)

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
        buttonContainer.add(refreshButton)

        // Save button
        val saveButton = JButton("Save")
        saveButton.addActionListener {
            val saveContext = SaveContext(translationSet, mutationObserver)
            saveContext.overwriteChanges()
        }
        buttonContainer.add(saveButton)

        // Display button
        val displayCheckbox = JBCheckBox("Errors")
        displayCheckbox.border = BorderFactory.createEmptyBorder(6, 0, 0, 0)
        displayCheckbox.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                getEmptyCellSorter(table.model)
            } else {
                table.rowSorter = null
            }
        }
        checkboxContainer.add(displayCheckbox)
        checkboxContainer.add(Box.createRigidArea(Dimension(10, 0)))

        return panel
    }

    private fun getEmptyCellSorter(model: TableModel) {
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
                println("Old value $oldValue, new value $newValue")
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
                    println("Deleting...")
                }
            }
        })

        return table
    }

    private fun createTablePanel(table: JBTable): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        val pane = JBScrollPane(table)
        panel.add(pane, BorderLayout.CENTER)

        return panel
    }
}
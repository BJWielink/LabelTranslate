package org.label.translate.labeltranslate

import com.intellij.ui.JBColor
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

class EmptyCellRenderer(private val mutationObserver: MutationObserver) : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if (value !is String) {
            component.background = JBColor.decode("#5E3838")
            return component
        }

        val key = table?.getValueAt(row, 0)

        if (mutationObserver.isDeleted(key) && component is JLabel) {
            component.text = "<html><strike>${component.text}</strike></html>"
        }

        if (mutationObserver.isMutatedCell(key, column)) {
            component.background = JBColor.decode("#56925C")
        } else if (mutationObserver.isMutatedRow(key)) {
            component.background = JBColor.decode("#56925C")
        }

        if (value.isBlank()) {
            component.background = JBColor.decode("#5E3838")
        }

        return component
    }
}
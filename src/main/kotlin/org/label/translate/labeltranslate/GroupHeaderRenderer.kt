package org.label.translate.labeltranslate

import com.intellij.ui.JBColor
import java.awt.Component
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JTable
import javax.swing.UIManager
import javax.swing.table.DefaultTableCellRenderer

class GroupHeaderRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val raw = value as? String ?: ""
        val prefix = if (raw.startsWith("\u0001")) raw.substring(1) else raw
        val sep = SeparatorConfig().separator
        val depth = prefix.split(sep).size - 1
        val displayText = "    ".repeat(depth) + prefix.substringAfterLast(sep)
        val comp = super.getTableCellRendererComponent(table, displayText, isSelected, hasFocus, row, column)
        if (!isSelected) {
            comp.background = UIManager.getColor("TableHeader.background")
                ?: JBColor(java.awt.Color(0xEEEEEE), java.awt.Color(0x3C3F41))
            comp.foreground = UIManager.getColor("TableHeader.foreground")
                ?: UIManager.getColor("Label.foreground")
        }
        comp.font = comp.font.deriveFont(Font.BOLD)
        border = BorderFactory.createEmptyBorder(0, 6, 0, 6)
        return comp
    }
}

class KeyCellRenderer(private val mutationObserver: MutationObserver) : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val fullKey = value as? String ?: ""
        val sep = SeparatorConfig().separator
        val depth = fullKey.split(sep).size - 1
        val displayText = if (sep in fullKey) "    ".repeat(depth) + fullKey.substringAfterLast(sep) else fullKey

        val comp = super.getTableCellRendererComponent(table, displayText, isSelected, hasFocus, row, column)

        if (!isSelected) {
            if (mutationObserver.isModifiedRow(fullKey)) {
                comp.background = JBColor.decode("#56925C")
            }
            if (mutationObserver.isDeleted(fullKey)) {
                text = "<html><strike>$displayText</strike></html>"
            }
        }
        return comp
    }
}

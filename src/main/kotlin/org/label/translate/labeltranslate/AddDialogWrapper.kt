package org.label.translate.labeltranslate

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class AddDialogWrapper(
    existingGroups: List<String> = emptyList(),
    initialGroup: String = "",
    initialKey: String = "",
    dialogTitle: String = PluginI18n.t("add.title"),
    private val keyExists: (String) -> Boolean = { false }
) : DialogWrapper(true) {

    companion object {
        val NONE get() = PluginI18n.t("combo.none")
    }

    private val groupComboBox = ComboBox<String>()
    private val keyField = JBTextField()
    private val previewLabel = JBLabel()

    /** De volledige key die wordt aangemaakt, bijv. "between->numeric" of "accepted" */
    val fullKey: String
        get() {
            val group = (groupComboBox.editor?.item as? String)?.trim() ?: ""
            val sub = keyField.text.trim()
            val sep = SeparatorConfig().separator
            return when {
                group.isEmpty() || group == NONE -> sub
                sub.isEmpty() -> group
                else -> "$group$sep$sub"
            }
        }

    init {
        title = dialogTitle

        groupComboBox.addItem(NONE)
        existingGroups.forEach { groupComboBox.addItem(it) }
        groupComboBox.isEditable = true

        // Pre-fill bij hernoemen
        if (initialGroup.isNotEmpty()) {
            if (existingGroups.contains(initialGroup)) {
                groupComboBox.selectedItem = initialGroup
            } else {
                groupComboBox.selectedItem = NONE
                (groupComboBox.editor?.editorComponent as? javax.swing.text.JTextComponent)?.text = initialGroup
            }
        }
        if (initialKey.isNotEmpty()) {
            keyField.text = initialKey
        }

        val onChange = { updatePreview() }
        (groupComboBox.editor?.editorComponent as? javax.swing.text.JTextComponent)
            ?.document?.addDocumentListener(docListener(onChange))
        groupComboBox.addItemListener { onChange() }
        keyField.document.addDocumentListener(docListener(onChange))

        init()
    }

    private fun updatePreview() {
        val key = fullKey
        previewLabel.text = if (key.isBlank()) "—" else key
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.preferredSize = Dimension(430, 120)

        val label = GridBagConstraints().apply {
            gridx = 0; fill = GridBagConstraints.NONE
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 4, 4, 8)
        }
        val field = GridBagConstraints().apply {
            gridx = 1; fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(4, 0, 4, 4)
        }

        val tooltipKeys = PluginI18n.t("tooltip.keys")
        val groupLabel = JBLabel(PluginI18n.t("field.group"))
        groupLabel.toolTipText = tooltipKeys
        groupComboBox.toolTipText = tooltipKeys

        val keyLabel = JBLabel(PluginI18n.t("field.key"))
        keyField.toolTipText = PluginI18n.t("tooltip.key")

        label.gridy = 0; panel.add(groupLabel, label)
        field.gridy = 0; panel.add(groupComboBox, field)

        label.gridy = 1; panel.add(keyLabel, label)
        field.gridy = 1; panel.add(keyField, field)

        label.gridy = 2; panel.add(JBLabel(PluginI18n.t("field.full_key")), label)
        field.gridy = 2; panel.add(previewLabel, field)

        updatePreview()
        return panel
    }

    override fun doOKAction() {
        if (keyExists(fullKey)) {
            JOptionPane.showMessageDialog(contentPane, "Key '$fullKey' already exists.")
            return
        }
        super.doOKAction()
    }

    override fun getPreferredFocusedComponent() = keyField

    private fun docListener(onChange: () -> Unit) = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) = onChange()
        override fun removeUpdate(e: DocumentEvent?) = onChange()
        override fun changedUpdate(e: DocumentEvent?) = onChange()
    }
}

package org.label.translate.labeltranslate

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class TranslateDialogWrapper(
    existingGroups: List<String> = emptyList(),
    private val keyExists: (String) -> Boolean = { false }
) : DialogWrapper(true) {

    private companion object {
        const val NONE = "(none)"
        const val HEADER_RECENT = "\u00A0__HEADER_RECENT__"
        const val HEADER_ALL    = "\u00A0__HEADER_ALL__"
        const val SEPARATOR     = "\u00A0__SEPARATOR__"

        val allLanguageOptions: List<String> = Locale.getAvailableLocales()
            .filter { it.displayLanguage.isNotBlank() }
            .distinctBy { it.language }
            .sortedBy { it.displayLanguage }
            .map { "${it.language.uppercase()} - ${it.displayLanguage}" }
    }

    private val groupComboBox = ComboBox<String>()
    private val keyField = JBTextField()
    val langComboBox = ComboBox<String>()
    val wordToTranslateField = JBTextField()
    private val previewLabel = JBLabel()

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

    val selectedLanguage: String
        get() = (langComboBox.selectedItem as? String)?.substringBefore(" -")?.trim()
            ?: DefaultLanguage().defaultLanguage

    init {
        title = PluginI18n.t("translate.title")

        groupComboBox.addItem(NONE)
        existingGroups.forEach { groupComboBox.addItem(it) }
        groupComboBox.isEditable = true

        buildLangCombo()

        val onChange = { updatePreview() }
        (groupComboBox.editor?.editorComponent as? javax.swing.text.JTextComponent)
            ?.document?.addDocumentListener(docListener(onChange))
        groupComboBox.addItemListener { onChange() }
        keyField.document.addDocumentListener(docListener(onChange))

        init()
    }

    private fun buildLangCombo() {
        val recent = RecentLanguagesConfig().recentLanguages
        val defaultKey = DefaultLanguage().defaultLanguage.uppercase()

        langComboBox.removeAllItems()

        if (recent.isNotEmpty()) {
            langComboBox.addItem(HEADER_RECENT)
            recent.forEach { langComboBox.addItem(it) }
            langComboBox.addItem(SEPARATOR)
        }
        langComboBox.addItem(HEADER_ALL)
        allLanguageOptions.forEach { langComboBox.addItem(it) }

        // Pre-selecteer: eerst kijken in recent, anders in de volledige lijst
        val toSelect = recent.firstOrNull { it.startsWith("$defaultKey -") }
            ?: allLanguageOptions.firstOrNull { it.startsWith("$defaultKey -") }
        if (toSelect != null) langComboBox.selectedItem = toSelect

        langComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                return when (val item = value as? String ?: "") {
                    HEADER_RECENT, HEADER_ALL -> {
                        val lbl = JLabel(if (item == HEADER_RECENT) "Last used" else "Languages")
                        lbl.font = lbl.font.deriveFont(Font.BOLD)
                        lbl.foreground = JBColor.GRAY
                        lbl.border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
                        lbl.isEnabled = false
                        lbl
                    }
                    SEPARATOR -> {
                        val sep = JSeparator()
                        sep.preferredSize = Dimension(sep.preferredSize.width, 4)
                        sep
                    }
                    else -> super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                }
            }
        }

        // Headers en separator niet selecteerbaar maken
        langComboBox.addActionListener {
            val selected = langComboBox.selectedItem as? String ?: return@addActionListener
            if (selected == HEADER_RECENT || selected == HEADER_ALL || selected == SEPARATOR) {
                val fallback = recent.firstOrNull { it.startsWith("$defaultKey -") }
                    ?: allLanguageOptions.firstOrNull { it.startsWith("$defaultKey -") }
                if (fallback != null) langComboBox.selectedItem = fallback
            }
        }
    }

    private fun updatePreview() {
        val key = fullKey
        previewLabel.text = if (key.isBlank()) "—" else key
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.preferredSize = Dimension(420, 140)

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
        val langCell = GridBagConstraints().apply {
            gridx = 0; fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 4, 4, 8)
        }

        val tooltipKeys = PluginI18n.t("tooltip.keys")
        val groupLabel = JBLabel(PluginI18n.t("field.group"))
        groupLabel.toolTipText = tooltipKeys
        groupComboBox.toolTipText = tooltipKeys
        keyField.toolTipText = PluginI18n.t("tooltip.key")
        wordToTranslateField.toolTipText = PluginI18n.t("tooltip.word")

        label.gridy = 0; panel.add(groupLabel, label)
        field.gridy = 0; panel.add(groupComboBox, field)

        label.gridy = 1; panel.add(JBLabel(PluginI18n.t("field.key")), label)
        field.gridy = 1; panel.add(keyField, field)

        langCell.gridy = 2; panel.add(langComboBox, langCell)
        field.gridy = 2; panel.add(wordToTranslateField, field)

        label.gridy = 3; panel.add(JBLabel(PluginI18n.t("field.full_key")), label)
        field.gridy = 3; panel.add(previewLabel, field)

        updatePreview()
        return panel
    }

    override fun doOKAction() {
        if (fullKey.isNotBlank() && keyExists(fullKey)) {
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

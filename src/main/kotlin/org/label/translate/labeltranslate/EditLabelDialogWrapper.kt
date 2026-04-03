package org.label.translate.labeltranslate

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField

class EditLabelDialogWrapper(
    private val project: Project,
    private val translationSet: TranslationSet,
    private val translationKey: String
) : DialogWrapper(project) {

    // translationKey from blade uses dots (e.g. "auth.nested.key").
    // TranslationSet stores keys without the group prefix and uses SeparatorConfig as delimiter.
    // So "auth.nested.key" → group "auth" is stripped, then dots replaced with separator → "nested->key".
    private val localKey: String = run {
        val sep = SeparatorConfig().separator
        val afterGroup = if (translationKey.contains(".")) translationKey.substringAfter(".") else translationKey
        if (sep != ".") afterGroup.replace(".", sep) else afterGroup
    }

    private val languages = translationSet.listLanguages().toList()
    private val fields = mutableMapOf<String, JTextField>()
    private var sourceLangCombo: ComboBox<String>? = null
    private var translateButton: JButton? = null

    init {
        title = "Edit Label: $translationKey"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(4, 8, 4, 8)

        // Key label at top
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
        panel.add(JLabel("<html><b>Key:</b> $translationKey</html>"), gbc)
        gbc.gridwidth = 1

        for ((row, language) in languages.withIndex()) {
            val currentValue = translationSet.getTranslationMap(language)?.get(localKey) ?: ""

            gbc.gridx = 0; gbc.gridy = row + 1; gbc.weightx = 0.0
            panel.add(JLabel("${language.toUpperCase()}:"), gbc)

            gbc.gridx = 1; gbc.weightx = 1.0
            val field = JTextField(currentValue, 40)
            fields[language] = field
            panel.add(field, gbc)
        }

        // Auto-translate row — only shown when an OpenAI API key is configured
        if (OpenAiService.hasApiKey()) {
            val nextRow = languages.size + 1

            gbc.gridx = 0; gbc.gridy = nextRow; gbc.weightx = 0.0
            val combo = ComboBox(languages.map { it.toUpperCase() }.toTypedArray())
            // Pre-select the configured default language, or first language with a non-empty value
            val defaultLang = DefaultLanguage().defaultLanguage.toUpperCase()
            val preselect = languages.firstOrNull { it.toUpperCase() == defaultLang }
                ?: languages.firstOrNull { (translationSet.getTranslationMap(it)?.get(localKey) ?: "").isNotBlank() }
                ?: languages.firstOrNull()
            if (preselect != null) combo.selectedItem = preselect.toUpperCase()
            sourceLangCombo = combo
            panel.add(combo, gbc)

            gbc.gridx = 1; gbc.weightx = 1.0
            val btn = JButton("Auto-translate via OpenAI")
            btn.addActionListener { runAutoTranslate() }
            translateButton = btn
            panel.add(btn, gbc)
        }

        val scrollPane = JScrollPane(panel)
        scrollPane.preferredSize = Dimension(520, minOf(480, languages.size * 38 + 100))
        scrollPane.border = null
        return scrollPane
    }

    private fun runAutoTranslate() {
        val sourceLang = sourceLangCombo?.selectedItem as? String ?: return
        val sourceField = fields[languages.firstOrNull { it.toUpperCase() == sourceLang }] ?: return
        val wordToTranslate = sourceField.text.trim()

        if (wordToTranslate.isBlank()) {
            JOptionPane.showMessageDialog(
                contentPane,
                "Fill in the $sourceLang field first — that value will be used as the source for translation.",
                "Label Translate",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        translateButton?.isEnabled = false
        translateButton?.text = "Translating…"

        OpenAiService.translate(
            key = localKey,
            wordToTranslate = wordToTranslate,
            sourceLang = sourceLang,
            languages = languages,
            onSuccess = { results ->
                for ((lang, translation) in results) {
                    val fieldKey = languages.firstOrNull { it.toUpperCase() == lang }
                    fieldKey?.let { fields[it]?.text = translation }
                }
                translateButton?.isEnabled = true
                translateButton?.text = "Auto-translate via OpenAI"
            },
            onError = { msg ->
                JOptionPane.showMessageDialog(contentPane, msg, "Translation error", JOptionPane.ERROR_MESSAGE)
                translateButton?.isEnabled = true
                translateButton?.text = "Auto-translate via OpenAI"
            }
        )
    }

    fun saveChanges() {
        val observer = MutationObserver()

        for ((colIndex, language) in languages.withIndex()) {
            val originalValue = translationSet.getTranslationMap(language)?.get(localKey) ?: ""
            val newValue = fields[language]?.text ?: continue

            if (newValue != originalValue) {
                observer.addMutation(localKey, colIndex, newValue)
            }
        }

        if (!observer.unchanged()) {
            SaveContext(translationSet, observer).overwriteChanges()
        }
    }
}

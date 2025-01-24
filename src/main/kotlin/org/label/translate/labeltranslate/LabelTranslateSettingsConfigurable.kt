package org.label.translate.labeltranslate

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import java.awt.Component
import java.util.*
import javax.swing.*

class LabelTranslateSettingsConfigurable : Configurable {
    private lateinit var apiKeyField: JTextField
    private lateinit var tokenField: JTextField
    private lateinit var apiKeyConfig: ApiKeyConfig
    private lateinit var languageComboBox: ComboBox<String> // Updated to use IntelliJ's ComboBox
    private lateinit var defaultLanguage: DefaultLanguage // To manage the default language

    override fun createComponent(): JComponent {
        apiKeyConfig = ApiKeyConfig() // Initialize the ApiKeyConfig
        defaultLanguage = DefaultLanguage() // Initialize DefaultLanguage

        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

        // Add sections
        mainPanel.add(createApiKeySection())
        mainPanel.add(Box.createRigidArea(java.awt.Dimension(0, 20)))
        mainPanel.add(createMaxTokenSection())
        mainPanel.add(Box.createRigidArea(java.awt.Dimension(0, 20)))
        mainPanel.add(createLanguageDropdownSection())

        return mainPanel
    }

    private fun createApiKeySection(): JPanel {
        val apiKeyPanel = JPanel()
        apiKeyPanel.layout = BoxLayout(apiKeyPanel, BoxLayout.Y_AXIS)
        apiKeyPanel.add(JLabel("API Key:"))

        apiKeyField = JTextField(apiKeyConfig.apiKey, 20)
        apiKeyField.minimumSize = java.awt.Dimension(400, 40)
        apiKeyField.maximumSize = java.awt.Dimension(400, 40)
        apiKeyPanel.add(apiKeyField)

        apiKeyPanel.alignmentX = Component.LEFT_ALIGNMENT
        return apiKeyPanel
    }

    private fun createMaxTokenSection(): JPanel {
        val tokenPanel = JPanel()
        tokenPanel.layout = BoxLayout(tokenPanel, BoxLayout.Y_AXIS)
        tokenPanel.add(JLabel("Max API Tokens:"))

        tokenField = JTextField(apiKeyConfig.maxTokens, 20)
        tokenField.minimumSize = java.awt.Dimension(400, 40)
        tokenField.maximumSize = java.awt.Dimension(400, 40)
        tokenPanel.add(tokenField)

        tokenPanel.alignmentX = Component.LEFT_ALIGNMENT
        return tokenPanel
    }

    private fun createLanguageDropdownSection(): JPanel {
        val languagePanel = JPanel()
        languagePanel.layout = BoxLayout(languagePanel, BoxLayout.Y_AXIS)
        languagePanel.add(JLabel("Default Language:"))

        val languages = Locale.getAvailableLocales()
            .filter { it.displayLanguage.isNotBlank() }
            .distinctBy { it.language }
            .sortedBy { it.displayLanguage }

        val languageOptions = languages.map { "${it.language.toUpperCase()} - ${it.displayLanguage}" }
        languageComboBox = ComboBox(languageOptions.toTypedArray())

        val defaultKey = defaultLanguage.defaultLanguage.ifEmpty { "EN" }
        val defaultOption = languageOptions.find { it.startsWith("$defaultKey -") } ?: "EN - English"
        languageComboBox.selectedItem = defaultOption

        languageComboBox.minimumSize = java.awt.Dimension(400, 40)
        languageComboBox.maximumSize = java.awt.Dimension(400, 40)
        languagePanel.add(languageComboBox)

        languagePanel.alignmentX = Component.LEFT_ALIGNMENT
        return languagePanel
    }

    override fun isModified(): Boolean {
        return apiKeyField.text != apiKeyConfig.apiKey ||
                tokenField.text != apiKeyConfig.maxTokens ||
                languageComboBox.selectedItem.toString().substringBefore(" -") != defaultLanguage.defaultLanguage
    }

    override fun apply() {
        apiKeyConfig.apiKey = apiKeyField.text
        apiKeyConfig.maxTokens = tokenField.text
        defaultLanguage.defaultLanguage = languageComboBox.selectedItem.toString().substringBefore(" -")
    }

    override fun reset() {
        apiKeyField.text = apiKeyConfig.apiKey
        tokenField.text = apiKeyConfig.maxTokens
        val defaultOption = (0 until languageComboBox.itemCount).map { languageComboBox.getItemAt(it) }
            .find { it.startsWith("${defaultLanguage.defaultLanguage} -") } ?: "EN - English"
        languageComboBox.selectedItem = defaultOption
    }

    override fun getDisplayName(): String {
        return "Label Translate Settings"
    }
}

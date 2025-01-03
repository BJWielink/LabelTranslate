package org.label.translate.labeltranslate

import com.intellij.openapi.options.Configurable
import javax.swing.*

class LabelTranslateSettingsConfigurable : Configurable {
    private lateinit var apiKeyField: JTextField
    private lateinit var apiKeyConfig: ApiKeyConfig
    private lateinit var languageComboBox: JComboBox<String>
    private lateinit var defaultLanguage: DefaultLanguage // To manage the default language

    override fun createComponent(): JComponent? {
        apiKeyConfig = ApiKeyConfig() // Initialize the ApiKeyConfig
        defaultLanguage = DefaultLanguage() // Initialize DefaultLanguage

        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // API Key Field
        panel.add(JLabel("API Key:"))
        apiKeyField = JTextField(apiKeyConfig.apiKey, 20)
        apiKeyField.maximumSize = java.awt.Dimension(300, 30)
        apiKeyField.preferredSize = java.awt.Dimension(300, 30)
        apiKeyField.minimumSize = java.awt.Dimension(100, 30)
        panel.add(apiKeyField)

        // Language Dropdown
        panel.add(JLabel("Default Language:"))
        val languages = listOf("English", "Spanish", "French", "German", "Chinese") // Example languages
        languageComboBox = JComboBox(languages.toTypedArray())

        languageComboBox.maximumSize = java.awt.Dimension(300, 30)
        languageComboBox.preferredSize = java.awt.Dimension(300, 30)
        languageComboBox.minimumSize = java.awt.Dimension(100, 30)

        languageComboBox.selectedItem = defaultLanguage.defaultLanguage.ifEmpty { "English" } // Default to "English" if no language is set
        panel.add(languageComboBox)

        return panel
    }

    override fun isModified(): Boolean {
        // Check if either the API key or the selected language has been modified
        return apiKeyField.text != apiKeyConfig.apiKey || languageComboBox.selectedItem != defaultLanguage.defaultLanguage
    }

    override fun apply() {
        // Save the current API key and selected language
        apiKeyConfig.apiKey = apiKeyField.text
        defaultLanguage.defaultLanguage = languageComboBox.selectedItem.toString()
    }

    override fun reset() {
        // Reset the API key field and language combo box to their stored values
        apiKeyField.text = apiKeyConfig.apiKey
        languageComboBox.selectedItem = defaultLanguage.defaultLanguage.ifEmpty { "English" }
    }

    override fun getDisplayName(): String {
        return "Label Translate Settings"
    }
}

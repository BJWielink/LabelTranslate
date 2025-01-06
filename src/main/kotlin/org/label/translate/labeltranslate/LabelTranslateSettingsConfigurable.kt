package org.label.translate.labeltranslate

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox // Use IntelliJ's ComboBox
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

        // Main panel to hold all sections
        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

        // Add the API Key section
        mainPanel.add(createApiKeySection())

        // Add some spacing between sections
        mainPanel.add(Box.createRigidArea(java.awt.Dimension(0, 20)))

        mainPanel.add(createMaxTokenSection())

        // Add some spacing between sections
        mainPanel.add(Box.createRigidArea(java.awt.Dimension(0, 20)))

        // Add the Language Dropdown section
        mainPanel.add(createLanguageDropdownSection())

        return mainPanel
    }

    private fun createApiKeySection(): JPanel {
        val apiKeyPanel = JPanel()
        apiKeyPanel.layout = BoxLayout(apiKeyPanel, BoxLayout.Y_AXIS)

        apiKeyPanel.add(JLabel("API Key:"))

        apiKeyField = JTextField(apiKeyConfig.apiKey, 20)
        apiKeyField.maximumSize = java.awt.Dimension(300, 30)
        apiKeyField.preferredSize = java.awt.Dimension(300, 30)
        apiKeyField.minimumSize = java.awt.Dimension(100, 30)
        apiKeyPanel.add(apiKeyField)

        return apiKeyPanel
    }

    private fun createMaxTokenSection(): JPanel {
        val tokenPanel = JPanel()
        tokenPanel.layout = BoxLayout(tokenPanel, BoxLayout.Y_AXIS)

        tokenPanel.add(JLabel("Max api tokens:"))

        tokenField = JTextField(apiKeyConfig.maxTokens, 20)
        tokenField.maximumSize = java.awt.Dimension(300, 30)
        tokenField.preferredSize = java.awt.Dimension(300, 30)
        tokenField.minimumSize = java.awt.Dimension(100, 30)
        tokenPanel.add(tokenField)

        return tokenPanel
    }


    private fun createLanguageDropdownSection(): JPanel {
        val languagePanel = JPanel()
        languagePanel.layout = BoxLayout(languagePanel, BoxLayout.Y_AXIS)

        languagePanel.add(JLabel("Default Language:"))

        // Get all available languages from Locale
        val languages = Locale.getAvailableLocales()
            .filter { it.displayLanguage.isNotBlank() } // Exclude empty entries
            .distinctBy { it.language } // Ensure no duplicate language codes
            .sortedBy { it.displayLanguage } // Sort alphabetically by language name

        // Map languages to "Code - Name" format
        val languageOptions = languages.map { "${it.language.toUpperCase()} - ${it.displayLanguage}" }

        // Use IntelliJ's ComboBox instead of Swing's JComboBox
        languageComboBox = ComboBox(languageOptions.toTypedArray())

        // Set dimensions for the ComboBox
        languageComboBox.maximumSize = java.awt.Dimension(300, 30)
        languageComboBox.preferredSize = java.awt.Dimension(300, 30)
        languageComboBox.minimumSize = java.awt.Dimension(100, 30)

        // Set the default language
        val defaultKey = defaultLanguage.defaultLanguage.ifEmpty { "EN" } // Default to "EN" if no language is set
        val defaultOption = languageOptions.find { it.startsWith("$defaultKey -") } ?: "EN - English"
        languageComboBox.selectedItem = defaultOption

        languagePanel.add(languageComboBox)

        return languagePanel
    }

    override fun isModified(): Boolean {
        // Check if either the API key or the selected language has been modified
        return apiKeyField.text != apiKeyConfig.apiKey ||
                languageComboBox.selectedItem.toString().substringBefore(" -") != defaultLanguage.defaultLanguage
    }

    override fun apply() {
        // Save the current API key and selected language
        apiKeyConfig.apiKey = apiKeyField.text
        defaultLanguage.defaultLanguage = languageComboBox.selectedItem.toString().substringBefore(" -")
    }

    override fun reset() {
        // Reset the API key field and language combo box to their stored values
        apiKeyField.text = apiKeyConfig.apiKey
        val defaultOption = (0 until languageComboBox.itemCount).map { languageComboBox.getItemAt(it) }
            .find { it.startsWith("${defaultLanguage.defaultLanguage} -") } ?: "EN - English"
        languageComboBox.selectedItem = defaultOption
    }

    override fun getDisplayName(): String {
        return "Label Translate Settings"
    }
}

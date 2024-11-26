package org.label.translate.labeltranslate

import com.intellij.openapi.options.Configurable
import javax.swing.*

class ApiKeySettingsConfigurable : Configurable {
    private lateinit var apiKeyField: JTextField
    private lateinit var apiKeyConfig: ApiKeyConfig // Declare it as a class property

    override fun createComponent(): JComponent? {
        apiKeyConfig = ApiKeyConfig() // Initialize the ApiKeyConfig here

        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // API Key Field
        panel.add(JLabel("API Key:"))

        apiKeyField = JTextField(apiKeyConfig.apiKey, 20) // Set initial text from config and columns for field width
        apiKeyField.maximumSize = java.awt.Dimension(300, 30) // Set max size for the field (300px width)
        apiKeyField.preferredSize = java.awt.Dimension(300, 30) // Set preferred size to control field width

        // Ensuring that the field doesn't expand further than necessary
        apiKeyField.minimumSize = java.awt.Dimension(100, 30)

        panel.add(apiKeyField)

        return panel
    }

    override fun isModified(): Boolean {
        // Compare current text field content with config value
        return apiKeyField.text != apiKeyConfig.apiKey
    }

    override fun apply() {
        // Save current text in the config
        apiKeyConfig.apiKey = apiKeyField.text
    }

    override fun reset() {
        // Reset field to the config value
        apiKeyField.text = apiKeyConfig.apiKey
    }

    override fun getDisplayName(): String {
        return "Label Translate Settings"
    }
}

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
    private lateinit var languageComboBox: ComboBox<String>
    private lateinit var defaultLanguage: DefaultLanguage
    private lateinit var customFilePathConfig: CustomFilePathConfig
    private val folderListModel = DefaultListModel<String>() // Model to manage folder paths in the UI
    private lateinit var folderList: JList<String> // List component for displaying folder paths
    private lateinit var addFolderButton: JButton
    private lateinit var removeFolderButton: JButton

    override fun createComponent(): JComponent {
        apiKeyConfig = ApiKeyConfig()
        defaultLanguage = DefaultLanguage()
        customFilePathConfig = CustomFilePathConfig()

        val mainPanel = JPanel()
        mainPanel.layout = BoxLayout(mainPanel, BoxLayout.Y_AXIS)

        // Add sections
        mainPanel.add(createApiKeySection())
        mainPanel.add(Box.createRigidArea(java.awt.Dimension(0, 20)))
        mainPanel.add(createMaxTokenSection())
        mainPanel.add(Box.createRigidArea(java.awt.Dimension(0, 20)))
        mainPanel.add(createLanguageDropdownSection())
        mainPanel.add(Box.createRigidArea(java.awt.Dimension(0, 20)))
        mainPanel.add(createCustomFolderSection())

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

    private fun createCustomFolderSection(): JPanel {
        val folderPanel = JPanel()
        folderPanel.layout = BoxLayout(folderPanel, BoxLayout.Y_AXIS)
        folderPanel.add(JLabel("Custom Folder Paths:"))

        folderList = JList(folderListModel)
        folderList.visibleRowCount = 5
        folderList.setFixedCellWidth(400)
        val scrollPane = JScrollPane(folderList)
        folderPanel.add(scrollPane)

        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        addFolderButton = JButton("Add Folder")
        removeFolderButton = JButton("Remove Selected")

        addFolderButton.addActionListener {
            val fileChooser = JFileChooser()
            fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            fileChooser.dialogTitle = "Select Folder"

            val returnValue = fileChooser.showOpenDialog(null)
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                val selectedFolder = fileChooser.selectedFile.absolutePath
                folderListModel.addElement(selectedFolder)
            }
        }

        removeFolderButton.addActionListener {
            val selectedIndices = folderList.selectedIndices
            for (i in selectedIndices.reversed()) {
                folderListModel.remove(i)
            }
        }

        buttonPanel.add(addFolderButton)
        buttonPanel.add(Box.createRigidArea(java.awt.Dimension(10, 0)))
        buttonPanel.add(removeFolderButton)
        folderPanel.add(buttonPanel)

        folderPanel.alignmentX = Component.LEFT_ALIGNMENT
        return folderPanel
    }

    override fun isModified(): Boolean {
        return apiKeyField.text != apiKeyConfig.apiKey ||
                tokenField.text != apiKeyConfig.maxTokens ||
                languageComboBox.selectedItem.toString().substringBefore(" -") != defaultLanguage.defaultLanguage ||
                !customFilePathConfig.folderPaths.containsAll(folderListModel.elements().toList()) ||
                !folderListModel.elements().toList().containsAll(customFilePathConfig.folderPaths)
    }

    override fun apply() {
        apiKeyConfig.apiKey = apiKeyField.text
        apiKeyConfig.maxTokens = tokenField.text
        defaultLanguage.defaultLanguage = languageComboBox.selectedItem.toString().substringBefore(" -")
        customFilePathConfig.folderPaths = folderListModel.elements().toList()

        val bus = com.intellij.openapi.application.ApplicationManager.getApplication().messageBus
        bus.syncPublisher(SettingsChangedNotifier.TOPIC).onFolderPathsChanged()
    }

    override fun reset() {
        apiKeyField.text = apiKeyConfig.apiKey
        tokenField.text = apiKeyConfig.maxTokens
        val defaultOption = (0 until languageComboBox.itemCount).map { languageComboBox.getItemAt(it) }
            .find { it.startsWith("${defaultLanguage.defaultLanguage} -") } ?: "EN - English"
        languageComboBox.selectedItem = defaultOption

        folderListModel.clear()
        customFilePathConfig.folderPaths.forEach { folderListModel.addElement(it) }
    }

    override fun getDisplayName(): String {
        return "Label Translate Settings"
    }

    private fun <E> DefaultListModel<E>.elements(): List<E> {
        return (0 until size).map { getElementAt(it) }
    }
}

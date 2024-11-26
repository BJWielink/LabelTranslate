import com.intellij.openapi.ui.DialogWrapper
import org.label.translate.labeltranslate.ApiKeyConfig
import javax.swing.*

class SettingsDialog(private val apiKeyConfig: ApiKeyConfig) : DialogWrapper(true) {
    private lateinit var apiKeyField: JTextField

    init {
        title = "Settings"
        init()
    }

    override fun createCenterPanel(): JComponent? {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // API Key Field
        panel.add(JLabel("API Key:"))
        apiKeyField = JTextField(apiKeyConfig.apiKey) // Initialize with current value
        panel.add(apiKeyField)

        return panel
    }

    override fun doOKAction() {
        // Update the apiKeyConfig with the new value
        apiKeyConfig.apiKey = apiKeyField.text
        super.doOKAction()
    }
}

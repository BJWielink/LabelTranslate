package org.label.translate.labeltranslate

import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class DeleteDialogWrapper(private val translationKey: String) : DialogWrapper(true) {
    init {
        title = PluginI18n.t("delete.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val dialogPanel = JPanel(BorderLayout())

        val label = JLabel(PluginI18n.tf("delete.message", translationKey))
        label.preferredSize = Dimension(100, 30)
        dialogPanel.add(label, BorderLayout.CENTER)

        return dialogPanel
    }
}
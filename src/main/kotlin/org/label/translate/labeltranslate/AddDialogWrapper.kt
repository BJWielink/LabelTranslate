package org.label.translate.labeltranslate

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

class AddDialogWrapper : DialogWrapper(true) {
    var textField: JBTextField? = null

    init {
        title = "Add Translation Key"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(400, 30)

        textField = JBTextField()
        val label = JBLabel("Translation key: ")

        panel.add(label, BorderLayout.WEST)
        panel.add(textField!!, BorderLayout.CENTER)
        return panel
    }
}
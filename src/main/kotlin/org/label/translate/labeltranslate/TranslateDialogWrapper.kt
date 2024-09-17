package org.label.translate.labeltranslate

import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class TranslateDialogWrapper : DialogWrapper(true) {
    var keyField: JTextField? = null
    var dutchWordField: JTextField? = null

    init {
        init()
        title = "Translate Label"
    }

    override fun createCenterPanel(): JComponent? {
        val panel = JPanel(GridLayout(2, 2))
        panel.preferredSize = Dimension(400, 30)

        panel.add(JLabel("Key:"))
        keyField = JTextField()
        panel.add(keyField)

        panel.add(JLabel("Dutch Word:"))
        dutchWordField = JTextField()
        panel.add(dutchWordField)

        return panel
    }
}

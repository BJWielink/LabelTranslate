package org.label.translate.labeltranslate

import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import java.awt.GridLayout
import java.util.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class TranslateDialogWrapper : DialogWrapper(true) {
    var keyField: JTextField? = null
    var wordToTranslateField: JTextField? = null

    init {
        init()
        title = "Translate Label"
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridLayout(2, 2))
        panel.preferredSize = Dimension(400, 30)

        panel.add(JLabel("Key:"))
        keyField = JTextField()
        panel.add(keyField)

        val defaultLanguageCode = DefaultLanguage().defaultLanguage.uppercase() // Ensure it's uppercase

        // Create a locale for the region using forLanguageTag
        val countryLocale = Locale.forLanguageTag("und-" + defaultLanguageCode) // Region-only locale

        // Get the full country name in English
        val fullCountryName = countryLocale.getDisplayCountry(Locale.ENGLISH)

        panel.add(JLabel("$fullCountryName word:"))
        wordToTranslateField = JTextField()
        panel.add(wordToTranslateField)

        return panel
    }
}

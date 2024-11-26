package org.label.translate.labeltranslate

import com.intellij.ide.util.PropertiesComponent

class ApiKeyConfig {
    private val properties = PropertiesComponent.getInstance()
    var apiKey: String
        get() = properties.getValue("label_translate.api_key", "") // Default is an empty string if not found
        set(value) {
            properties.setValue("label_translate.api_key", value)
        }
}

class CustomFilePathConfig {

}

class DefaultLanguage {
    var defaultLanguage: String = ""
}
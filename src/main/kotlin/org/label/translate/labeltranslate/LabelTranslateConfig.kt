package org.label.translate.labeltranslate

import com.intellij.ide.util.PropertiesComponent

class ApiKeyConfig {
    private val properties = PropertiesComponent.getInstance()
    var apiKey: String
        get() = properties.getValue("label_translate.api_key", "") // Default is an empty string if not found
        set(value) {
            properties.setValue("label_translate.api_key", value)
        }

    var maxTokens: String
        get() = properties.getValue("label_translate.max_tokens", "150") // Default is an empty string if not found
        set(value) {
            properties.setValue("label_translate.max_tokens", value)
        }
}

class DefaultLanguage {
    private val properties = PropertiesComponent.getInstance()
    var defaultLanguage: String
        get() = properties.getValue("label_translate.default_language", "EN") // Default is an empty string if not found
        set(value) {
            properties.setValue("label_translate.default_language", value)
        }
}

//todo this is for later
class CustomFilePathConfig {

}
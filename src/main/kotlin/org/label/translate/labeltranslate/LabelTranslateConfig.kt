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

class RecentLanguagesConfig {
    private val properties = PropertiesComponent.getInstance()

    var recentLanguages: List<String>
        get() = properties.getValue("label_translate.recent_languages", "")
            .split(";").filter { it.isNotBlank() }
        set(value) {
            properties.setValue("label_translate.recent_languages", value.joinToString(";"))
        }

    fun push(langOption: String) {
        val updated = (listOf(langOption) + recentLanguages.filter { it != langOption }).take(5)
        recentLanguages = updated
    }
}

class CustomFilePathConfig {
    private val properties = PropertiesComponent.getInstance()

    var folderPaths: List<String>
        get() = properties.getValue("label_translate.folder_paths", "")
            .split(";") // Use ";" as a delimiter for multiple paths
            .filter { it.isNotBlank() } // Remove any empty entries
        set(value) {
            properties.setValue("label_translate.folder_paths", value.joinToString(";")) // Serialize as ";" delimited string
        }
}

class SeparatorConfig {
    private val properties = PropertiesComponent.getInstance()
    var separator: String
        get() = properties.getValue("label_translate.separator", "->")
        set(value) {
            properties.setValue("label_translate.separator", value)
        }
}
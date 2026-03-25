package org.label.translate.labeltranslate

import com.google.gson.JsonParser

object PluginI18n {
    private val cache = mutableMapOf<String, Map<String, String>>()

    private fun loadStrings(lang: String): Map<String, String> {
        return cache.getOrPut(lang) {
            val stream = PluginI18n::class.java.getResourceAsStream("/lang/$lang.json")
                ?: PluginI18n::class.java.getResourceAsStream("/lang/en.json")
            if (stream != null) {
                JsonParser.parseString(stream.bufferedReader().readText()).asJsonObject
                    .entrySet().associate { it.key to it.value.asString }
            } else emptyMap()
        }
    }

    private fun strings(): Map<String, String> {
        val lang = DefaultLanguage().defaultLanguage.lowercase()
        return loadStrings(lang)
    }

    fun t(key: String): String = strings()[key] ?: key

    fun tf(key: String, vararg args: Any): String = String.format(strings()[key] ?: key, *args)
}

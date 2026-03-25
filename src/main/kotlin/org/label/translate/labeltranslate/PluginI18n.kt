package org.label.translate.labeltranslate

import com.google.gson.JsonParser
import java.util.Locale

object PluginI18n {
    private val strings: Map<String, String>

    init {
        val lang = Locale.getDefault().language
        val stream = PluginI18n::class.java.getResourceAsStream("/lang/$lang.json")
            ?: PluginI18n::class.java.getResourceAsStream("/lang/en.json")
        strings = if (stream != null) {
            JsonParser.parseString(stream.bufferedReader().readText()).asJsonObject
                .entrySet().associate { it.key to it.value.asString }
        } else emptyMap()
    }

    fun t(key: String): String = strings[key] ?: key

    fun tf(key: String, vararg args: Any): String = String.format(strings[key] ?: key, *args)
}

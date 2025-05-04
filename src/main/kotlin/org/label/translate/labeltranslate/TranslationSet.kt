package org.label.translate.labeltranslate

import com.intellij.util.containers.MultiMap
import java.io.File
import java.util.*

data class TranslationSet(val displayName: String, val translationFiles: Collection<File>) {
    private val contentMap: SortedMap<String, LinkedHashMap<String, String>> by lazy {
        val result = sortedMapOf<String, LinkedHashMap<String, String>>(compareBy { it.lowercase() })

        for (translationFile in translationFiles) {
            val languageKey = translationFile.parentFile.name
            val translationMap = linkedMapOf<String, String>()
            translationFile.forEachLine {
                val matchResults = KEY_VALUE_PAIR_REGEX.findAll(it)
                if (matchResults.count() == 2) {
                    var translationKey = matchResults.elementAt(0).value
                    translationKey = parse(translationKey)
                    var translationValue = matchResults.elementAt(1).value
                    translationValue = parse(translationValue)
                    translationMap[translationKey] = translationValue
                }
            }
            result[languageKey] = translationMap
        }

        result
    }

    private fun parse(input: String): String {
        // Remove quotation marks from regex obtained values
        var result = input.substring(1, input.lastIndex)

        // Unescape \' for visualization. On save, they will be added again
        result = result.replace("\\'", "'")

        return result
    }

    fun getTranslationMap(lang: String): Map<String, String>? {
        return contentMap[lang]
    }

    fun listLanguages(): Array<String> {
        return contentMap.keys.toTypedArray()
    }

    fun listTranslationsFor(key: String): Array<String> {
        val result = mutableListOf<String>()

        for (entry in contentMap.entries) {
            result.add(entry.value[key] ?: "")
        }

        return result.toTypedArray()
    }

    fun getKeys(): List<String> {
        val keys = mutableSetOf<String>()
        for (translationEntry in contentMap.entries) {
            for (keyValuePair in translationEntry.value) {
                keys.add(keyValuePair.key)
            }
        }
        return keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    companion object {
        /*
         * Location from root where we should look for resources / translation
         * files.
         */
        private val RESOURCE_PATHS = listOf("resources/lang", "lang")

        /*
         * All files with this suffix and in the resource path, with exclusion of
         * the EXCLUDED_LANGUAGE_FOLDERS will be parsed.
         */
        private const val TRANSLATION_FILE_SUFFIX = ".php"
        private val KEY_VALUE_PAIR_REGEX = Regex("""(['"])(?:(?<=\\)\1|.)*?(?<!\\)\1(?!=>)""")
        private val EXCLUDED_LANGUAGE_FOLDERS = listOf("vendor")

        fun getResourcePath(project: String?): String {
            if (project.isNullOrBlank()) {
                return RESOURCE_PATHS[0]
            }

            for (resourcePath in RESOURCE_PATHS) {
                val file = File(project, resourcePath)

                if (file.exists()) {
                    return resourcePath
                }
            }

            return RESOURCE_PATHS[0]
        }

        fun loadFromPath(project: String?): List<TranslationSet> {
            if (project == null) {
                return listOf()
            }

            val translationRoot = File(project, getResourcePath(project))
            val languageFolders = translationRoot.listFiles { _, name -> name !in EXCLUDED_LANGUAGE_FOLDERS } ?: return listOf()
            val translationMap = MultiMap<String, File>()
            for (languageFolder in languageFolders) {
                val languageFiles = languageFolder.listFiles { _, name -> name.endsWith(TRANSLATION_FILE_SUFFIX) } ?: arrayOf()
                for (languageFile in languageFiles) {
                    translationMap.putValue(languageFile.nameWithoutExtension, languageFile)
                }
            }

            val result = mutableListOf<TranslationSet>()
            for ((name, files) in translationMap.entrySet()) {
                val sortedFiles = files.sortedBy { it.parentFile.name.lowercase() }
                result.add(TranslationSet(name.replaceFirstChar { it.uppercaseChar() }, sortedFiles))
            }

            return result
        }
    }
}
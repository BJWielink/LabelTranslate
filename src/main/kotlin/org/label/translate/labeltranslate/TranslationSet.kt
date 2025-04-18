package org.label.translate.labeltranslate

import java.io.File
import java.util.*

data class TranslationSet(val displayName: String, val translationFiles: Collection<File>, val path: String) {
    private val contentMap: SortedMap<String, LinkedHashMap<String, String>> by lazy {
        val result = sortedMapOf<String, LinkedHashMap<String, String>>(compareBy { it.toLowerCase() })

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
         * files. Combines default resource paths and custom paths.
         */
        val RESOURCE_PATHS = listOf("resources/lang", "lang") + CustomFilePathConfig().folderPaths

        /*
         * All files with this suffix and in the resource path, with exclusion of
         * the EXCLUDED_LANGUAGE_FOLDERS will be parsed.
         */
        private const val TRANSLATION_FILE_SUFFIX = ".php"
        private val KEY_VALUE_PAIR_REGEX = Regex("""(['"])(?:(?<=\\)\1|.)*?(?<!\\)\1(?!=>)""")
        private val EXCLUDED_LANGUAGE_FOLDERS = listOf("vendor")

        fun loadFromPath(project: String?, path: String): List<TranslationSet> {
            if (project == null) return listOf()
            val translationRoot = if (File(path).isAbsolute) File(path) else File(project, path)

            if (!translationRoot.exists()) return listOf()

            val languageFolders =
                translationRoot.listFiles { file -> file.isDirectory && file.name !in EXCLUDED_LANGUAGE_FOLDERS }
                    ?: return listOf()

            val allFilesPerName = mutableMapOf<String, MutableList<File>>()

            for (languageFolder in languageFolders) {
                val translationFiles = languageFolder.listFiles { _, name ->
                    name.endsWith(TRANSLATION_FILE_SUFFIX)
                } ?: continue

                for (file in translationFiles) {
                    val nameWithoutExt = file.nameWithoutExtension
                    allFilesPerName.computeIfAbsent(nameWithoutExt) { mutableListOf() }.add(file)
                }
            }

            return allFilesPerName.map { (name, files) ->
                val sortedFiles = files.sortedBy { it.parentFile.name.toLowerCase() }
                TranslationSet(name.capitalize(), sortedFiles, path)
            }
        }

    }
}
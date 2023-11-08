package org.label.translate.labeltranslate

import com.intellij.util.containers.MultiMap
import java.io.File

data class TranslationSet(val displayName: String, val translationFiles: Collection<File>) {
    private val contentMap: HashMap<String, Map<String, String>> by lazy {
        val result = HashMap<String, Map<String, String>>()

        for (translationFile in translationFiles) {
            val languageKey = translationFile.parentFile.name
            val translationMap = mutableMapOf<String, String>()
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

        // Unescape \' for visualisation. On save, they will be added again
        result = result.replace("\\'", "'")

        return result
    }

    fun getTranslationMap(lang: String): Map<String, String>? {
        return contentMap[lang]
    }

    fun listLanguages(): Array<String> {
        return translationFiles.map { it.parentFile.name }.toTypedArray()
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
            for (keyValurPair in translationEntry.value) {
                keys.add(keyValurPair.key)
            }
        }
        return keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    companion object {
        /*
         * Location from root where we should look for resources / translation
         * files.
         */
        private const val RESOURCE_PATH = "resources/lang"

        /*
         * All files with this suffix and in the resource path, with exclusion of
         * the EXCLUDED_LANGUAGE_FOLDERS will be parsed.
         */
        private const val TRANSLATION_FILE_SUFFIX = ".php"

        /*
         * Regex that searches for everything between ' or ", ignoring \' and \".
         * The match will include the quotation marks itself.
         */
        private val KEY_VALUE_PAIR_REGEX = Regex("""(['"])(?:(?<=\\)\1|.)*?(?<!\\)\1(?!=>)""")

        /*
         * Folders in which we don't look for translation files.
         */
        private val EXCLUDED_LANGUAGE_FOLDERS = listOf("vendor")

        fun loadFromPath(project: String?): List<TranslationSet> {
            if (project == null) {
                return listOf()
            }

            val translationRoot = File(project, RESOURCE_PATH)
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
                result.add(TranslationSet(name.capitalize(), files))
            }

            return result
        }
    }
}

package org.label.translate.labeltranslate

import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

class SaveContext(private val translationSet: TranslationSet, private val mutationObserver: MutationObserver) {
    fun overwriteChanges() {
        for ((col, language) in translationSet.listLanguages().withIndex()) {
            val file = translationSet.translationFiles.firstOrNull { it.parentFile.name == language } ?: continue
            val translationMap = translationSet.getTranslationMap(language) ?: continue
            saveLanguage(col, file, translationMap)
        }
    }

    private fun saveLanguage(col: Int, translationFile: File, translationMap: Map<String, String>) {
        if (mutationObserver.unchanged()) {
            return
        }

        var mapToSave = HashMap(translationMap)

        // Hernoemingen toepassen: verplaats de originele waarde naar de nieuwe key
        for ((oldKey, newKey) in mutationObserver.keyRenames) {
            val value = mapToSave.remove(oldKey) ?: continue
            mapToSave[newKey] = value
        }

        val translationMutations = mutationObserver.getTranslationsForCol(col)

        // Nieuwe vertalingen toevoegen en bestaande overschrijven
        for (translationMutation in translationMutations) {
            mapToSave[translationMutation.key] = translationMutation.value
        }

        // Verwijderde keys eruit halen
        mapToSave = mapToSave.filterKeys { it !in mutationObserver.deletions } as HashMap<String, String>

        val sortedMapToSave = mapToSave.toSortedMap(String.CASE_INSENSITIVE_ORDER)

//        translationFile.bufferedWriter().use {
//            it.write("<?php")
//            it.newLine()
//            it.newLine()
//            it.write("return [")
//            it.newLine()
//
//            for (entry in sortedMapToSave) {
//                val regex = Regex("(?<!\\\\)'")
//                val key = entry.key.replace(regex, "\\\\'") // Escape ' in the string
//                val value = entry.value.replace(regex, "\\\\'") // Escape ' in the string
//                it.write("    '${key}' => '${value}',")
//                it.newLine()
//            }
//
//            it.write("];")
//            it.newLine()
//        }


        val nestedMap = unflattenMap(sortedMapToSave)
        val phpArray = serializePhpArray(nestedMap)

        translationFile.bufferedWriter().use {
            it.write("<?php\n\nreturn $phpArray;\n")
        }


        LocalFileSystem.getInstance().refreshIoFiles(listOf(translationFile))
    }

    private fun unflattenMap(flatMap: Map<String, String>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        for ((compoundKey, value) in flatMap) {
            val parts = compoundKey.split(".")
            var current = result
            for ((i, part) in parts.withIndex()) {
                if (i == parts.lastIndex) {
                    current[part] = value
                } else {
                    val existing = current[part]
                    @Suppress("UNCHECKED_CAST")
                    current = if (existing is MutableMap<*, *>) {
                        existing as MutableMap<String, Any>
                    } else {
                        val nested = mutableMapOf<String, Any>()
                        current[part] = nested
                        nested
                    }
                }
            }
        }
        return result
    }

    private fun serializePhpArray(map: Map<String, Any>, indent: String = "    "): String {
        val sb = StringBuilder("[\n")
        for ((key, value) in map) {
            val escapedKey = key.replace("'", "\\'")
            sb.append(indent).append("'$escapedKey' => ")
            if (value is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                sb.append(serializePhpArray(value as Map<String, Any>, indent + "    "))
            } else {
                val escaped = value.toString().replace("'", "\\'")
                sb.append("'$escaped'")
            }
            sb.append(",\n")
        }
        sb.append(indent.dropLast(4)).append("]")
        return sb.toString()
    }

}
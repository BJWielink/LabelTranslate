package org.label.translate.labeltranslate

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

        val translationMutations = mutationObserver.getTranslationsForCol(col)

        // Add new translation and override existing ones
        for (translationMutation in translationMutations) {
            mapToSave[translationMutation.key] = translationMutation.value
        }

        // Delete keys
        mapToSave = mapToSave.filterKeys { it !in mutationObserver.deletions } as HashMap<String, String>

        val sortedMapToSave = mapToSave.toSortedMap(String.CASE_INSENSITIVE_ORDER)

        translationFile.bufferedWriter().use {
            it.write("<?php")
            it.newLine()
            it.newLine()
            it.write("return [")
            it.newLine()

            for (entry in sortedMapToSave) {
                val regex = Regex("(?<!\\\\)'")
                val key = entry.key.replace(regex, "\\\\'") // Escape ' in the string
                val value = entry.value.replace(regex, "\\\\'") // Escape ' in the string
                it.write("    '${key}' => '${value}',")
                it.newLine()
            }

            it.write("];")
            it.newLine()
        }
    }
}
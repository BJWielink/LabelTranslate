package org.label.translate.labeltranslate

data class RowMutation(val key: String)
data class Mutation(val key: String, val index: Int, val value: String)
data class MutationResult(val key: String, val value: String)

class MutationObserver {
    private val mutations = mutableListOf<Mutation>()
    private val rowMutations = mutableListOf<RowMutation>()
    val deletions = mutableListOf<String>()

    fun unchanged(): Boolean {
        return mutations.isEmpty() && rowMutations.isEmpty() && deletions.isEmpty()
    }

    fun isDeleted(key: Any?): Boolean {
        return deletions.contains(key);
    }

    fun addDeletion(key: String) {
        deletions.add(key);
    }

    fun getTranslationsForCol(col: Int): List<MutationResult> {
        return mutations.filter { it.index == col }.map { MutationResult(it.key, it.value) }
    }

    fun addRowMutation(key: String) {
        rowMutations.add(RowMutation(key))
    }

    fun addMutation(key: String, index: Int, value: String) {
        mutations.add(Mutation(key, index, value))
    }

    fun removeIndex(key: String, index: Int) {
        mutations.removeIf { it.key == key && it.index == index }
    }

    fun isMutatedCell(key: Any?, col: Int): Boolean {
        val translationIndex = col - 1
        return mutations.any { it.key == key && it.index == translationIndex }
    }

    fun isMutatedRow(key: Any?): Boolean {
        return rowMutations.any { it.key == key}
    }
}
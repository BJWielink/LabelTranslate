package org.label.translate.labeltranslate

data class RowMutation(val key: String)
data class Mutation(val key: String, val index: Int, val value: String)
data class MutationResult(val key: String, val value: String)

class MutationObserver {
    private val mutations = mutableListOf<Mutation>()
    private val rowMutations = mutableListOf<RowMutation>()
    val deletions = mutableListOf<String>()
    /** oldKey → newKey, ondersteunt ketens (A→B→C wordt opgeslagen als A→C) */
    val keyRenames = mutableMapOf<String, String>()

    fun unchanged(): Boolean {
        return mutations.isEmpty() && rowMutations.isEmpty() && deletions.isEmpty() && keyRenames.isEmpty()
    }

    fun renameKey(oldKey: String, newKey: String) {
        // Ketens bijhouden: als oldKey al een rename-doel was, update de bron
        val origin = keyRenames.entries.firstOrNull { it.value == oldKey }?.key ?: oldKey
        if (origin == newKey) {
            keyRenames.remove(origin) // terugdraaien naar origineel
        } else {
            keyRenames[origin] = newKey
        }
        // Bestaande mutations updaten naar de nieuwe key
        mutations.filter { it.key == oldKey }.toList().forEach { m ->
            mutations.remove(m)
            mutations.add(Mutation(newKey, m.index, m.value))
        }
        rowMutations.filter { it.key == oldKey }.toList().forEach { r ->
            rowMutations.remove(r)
            rowMutations.add(RowMutation(newKey))
        }
    }

    fun isDeleted(key: Any?): Boolean {
        return deletions.contains(key)
    }

    fun addDeletion(key: String) {
        deletions.add(key)
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
        return rowMutations.any { it.key == key }
    }

    /** Geeft true als IETS aan deze rij gewijzigd is: waarde, naam of nieuw toegevoegd. */
    fun isModifiedRow(key: Any?): Boolean {
        val k = key as? String ?: return false
        return rowMutations.any { it.key == k } ||
               mutations.any { it.key == k } ||
               keyRenames.containsKey(k) ||
               keyRenames.containsValue(k)
    }

    fun clear() {
        mutations.clear()
        rowMutations.clear()
        deletions.clear()
        keyRenames.clear()
    }
}
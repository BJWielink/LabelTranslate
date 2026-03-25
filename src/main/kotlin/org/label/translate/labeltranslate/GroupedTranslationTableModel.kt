package org.label.translate.labeltranslate

import javax.swing.table.AbstractTableModel

class GroupedTranslationTableModel(private val columnNames: Array<String>) : AbstractTableModel() {

    sealed class Row {
        data class GroupHeader(val prefix: String) : Row()
        class DataRow(val fullKey: String, val values: Array<Any?>) : Row()
    }

    private val rows: MutableList<Row> = mutableListOf()

    fun loadFrom(translationSet: TranslationSet) {
        rows.clear()
        val shownPrefixes = mutableSetOf<String>()

        for (key in translationSet.getKeys()) {
            val parts = key.split('.')
            for (depth in 1 until parts.size) {
                val prefix = parts.take(depth).joinToString(".")
                if (shownPrefixes.add(prefix)) {
                    rows.add(Row.GroupHeader(prefix))
                }
            }
            val values: Array<Any?> = Array(columnNames.size - 1) { i ->
                translationSet.listTranslationsFor(key).getOrElse(i) { "" }
            }
            rows.add(Row.DataRow(key, values))
        }
        fireTableDataChanged()
    }

    override fun getRowCount() = rows.size
    override fun getColumnCount() = columnNames.size
    override fun getColumnName(column: Int) = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        return when (val row = rows.getOrNull(rowIndex)) {
            is Row.GroupHeader -> if (columnIndex == 0) row.prefix else ""
            is Row.DataRow -> if (columnIndex == 0) row.fullKey else row.values.getOrElse(columnIndex - 1) { "" } ?: ""
            null -> ""
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val row = rows.getOrNull(rowIndex) as? Row.DataRow ?: return
        row.values[columnIndex - 1] = aValue
        fireTableCellUpdated(rowIndex, columnIndex)
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        if (columnIndex == 0) return false
        return rows.getOrNull(rowIndex) is Row.DataRow
    }

    fun isGroupHeader(rowIndex: Int) = rows.getOrNull(rowIndex) is Row.GroupHeader

    fun getFullKey(rowIndex: Int): String? = (rows.getOrNull(rowIndex) as? Row.DataRow)?.fullKey

    fun getGroupPrefix(rowIndex: Int): String? = (rows.getOrNull(rowIndex) as? Row.GroupHeader)?.prefix

    fun getGroupNames(): List<String> = rows.filterIsInstance<Row.GroupHeader>().map { it.prefix }

    fun containsKey(key: String): Boolean = rows.filterIsInstance<Row.DataRow>().any { it.fullKey == key }

    fun getKeysForPrefix(prefix: String): List<String> =
        rows.filterIsInstance<Row.DataRow>()
            .filter { it.fullKey.startsWith("$prefix.") }
            .map { it.fullKey }

    fun renameGroup(oldPrefix: String, newPrefix: String) {
        for (i in rows.indices) {
            when (val row = rows[i]) {
                is Row.GroupHeader -> when {
                    row.prefix == oldPrefix ->
                        rows[i] = Row.GroupHeader(newPrefix)
                    row.prefix.startsWith("$oldPrefix.") ->
                        rows[i] = Row.GroupHeader(newPrefix + row.prefix.substring(oldPrefix.length))
                }
                is Row.DataRow -> if (row.fullKey.startsWith("$oldPrefix."))
                    rows[i] = Row.DataRow(newPrefix + row.fullKey.substring(oldPrefix.length), row.values)
            }
        }
        fireTableDataChanged()
    }

    fun renameDataRow(oldKey: String, newKey: String) {
        val rowIdx = (0 until rows.size).firstOrNull { getFullKey(it) == oldKey } ?: return
        val oldRow = rows[rowIdx] as? Row.DataRow ?: return

        rows.removeAt(rowIdx)
        fireTableRowsDeleted(rowIdx, rowIdx)

        // Verwijder lege groep-headers van diepste naar ondiepste
        val oldParts = oldKey.split('.')
        for (depth in oldParts.size - 1 downTo 1) {
            val prefix = oldParts.take(depth).joinToString(".")
            val headerIdx = rows.indexOfFirst { it is Row.GroupHeader && (it as Row.GroupHeader).prefix == prefix }
            if (headerIdx >= 0) {
                val hasChildren = rows.any { r ->
                    (r is Row.GroupHeader && r.prefix.startsWith("$prefix.")) ||
                    (r is Row.DataRow && r.fullKey.startsWith("$prefix."))
                }
                if (!hasChildren) {
                    rows.removeAt(headerIdx)
                    fireTableRowsDeleted(headerIdx, headerIdx)
                }
            }
        }

        addDataRow(newKey, oldRow.values)
    }

    fun addDataRow(key: String, initialValues: Array<Any?> = arrayOfNulls(columnNames.size - 1)) {
        val parts = key.split('.')
        val newRow = Row.DataRow(key, initialValues.copyOf(columnNames.size - 1))

        if (parts.size == 1) {
            rows.add(newRow)
            fireTableRowsInserted(rows.size - 1, rows.size - 1)
            return
        }

        val prefixChain = (1 until parts.size).map { parts.take(it).joinToString(".") }

        // Zoek de diepste bestaande prefix-header
        var deepestMatchDepth = 0
        var insertAt = rows.size
        for (depth in prefixChain.indices.reversed()) {
            val prefix = prefixChain[depth]
            val idx = rows.indexOfFirst { it is Row.GroupHeader && (it as Row.GroupHeader).prefix == prefix }
            if (idx >= 0) {
                deepestMatchDepth = depth + 1
                insertAt = idx + 1
                while (insertAt < rows.size) {
                    val r = rows[insertAt]
                    val belongs = (r is Row.GroupHeader && r.prefix.startsWith("$prefix.")) ||
                                  (r is Row.DataRow && r.fullKey.startsWith("$prefix."))
                    if (belongs) insertAt++ else break
                }
                break
            }
        }

        // Voeg ontbrekende prefix-headers toe
        for (depth in deepestMatchDepth until prefixChain.size) {
            rows.add(insertAt, Row.GroupHeader(prefixChain[depth]))
            fireTableRowsInserted(insertAt, insertAt)
            insertAt++
        }

        rows.add(insertAt, newRow)
        fireTableRowsInserted(insertAt, insertAt)
    }
}

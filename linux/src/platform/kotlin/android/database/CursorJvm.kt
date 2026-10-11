package android.database

/**
 * Android 的 Cursor 在桌面端只被用来读文件名（contentResolver.query(...) 拿 DISPLAY_NAME），
 * 所以给一个基于内存行的最小实现。
 */
interface Cursor : java.io.Closeable {
    fun moveToFirst(): Boolean
    fun moveToNext(): Boolean
    fun getColumnIndex(columnName: String): Int
    fun getString(index: Int): String?
    fun getLong(index: Int): Long
    val count: Int
}

class MatrixCursor(private val columns: List<String>) : Cursor {
    private val rows = mutableListOf<Array<Any?>>()
    private var position = -1

    fun addRow(values: Array<Any?>) {
        rows += values
    }

    override fun moveToFirst(): Boolean {
        if (rows.isEmpty()) return false
        position = 0
        return true
    }

    override fun moveToNext(): Boolean {
        if (position + 1 >= rows.size) return false
        position++
        return true
    }

    override fun getColumnIndex(columnName: String): Int = columns.indexOf(columnName)

    override fun getString(index: Int): String? =
        rows.getOrNull(position)?.getOrNull(index)?.toString()

    override fun getLong(index: Int): Long =
        rows.getOrNull(position)?.getOrNull(index)?.toString()?.toLongOrNull() ?: 0L

    override val count: Int get() = rows.size

    override fun close() {
        rows.clear()
    }
}

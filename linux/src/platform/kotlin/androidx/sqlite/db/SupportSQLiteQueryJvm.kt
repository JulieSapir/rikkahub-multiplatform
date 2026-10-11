package androidx.sqlite.db

/**
 * Room 的 KMP(jvm) 栈没有 Android 那套 SupportSQLite* 抽象，但上游 MessageNodeDAO 的
 * `@RawQuery`（json_each 统计）用的是它，这里给同名实现。
 */
interface SupportSQLiteQuery {
    val sql: String
    val argCount: Int

    fun bindTo(program: androidx.sqlite.SQLiteStatement)
}

class SimpleSQLiteQuery(
    override val sql: String,
    private val bindArgs: Array<out Any?> = emptyArray(),
) : SupportSQLiteQuery {
    override val argCount: Int get() = bindArgs.size

    override fun bindTo(program: androidx.sqlite.SQLiteStatement) {
        bindArgs.forEachIndexed { index, value ->
            when (value) {
                null -> program.bindNull(index + 1)
                is Long -> program.bindLong(index + 1, value)
                is Int -> program.bindLong(index + 1, value.toLong())
                is Double -> program.bindDouble(index + 1, value)
                is ByteArray -> program.bindBlob(index + 1, value)
                else -> program.bindText(index + 1, value.toString())
            }
        }
    }
}

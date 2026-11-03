package androidx.sqlite.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

/** The SupportSQLite calls used by upstream migrations and database callbacks. */
class SupportSQLiteDatabase(private val connection: SQLiteConnection) {
    private val transactions = ArrayDeque<Boolean>()

    fun execSQL(sql: String, bindArgs: Array<out Any?> = emptyArray()) {
        connection.prepare(sql).use { statement ->
            statement.bindArguments(bindArgs)
            statement.step()
        }
    }

    fun query(sql: String, bindArgs: Array<out Any?> = emptyArray()): SQLiteCursor =
        connection.prepare(sql).use { statement ->
            statement.bindArguments(bindArgs)
            statement.readCursor()
        }

    fun beginTransaction() {
        execSQL("SAVEPOINT upstream_${transactions.size}")
        transactions.addLast(false)
    }

    fun setTransactionSuccessful() {
        check(transactions.isNotEmpty()) { "No transaction is active" }
        transactions.removeLast()
        transactions.addLast(true)
    }

    fun endTransaction() {
        val successful = transactions.removeLast()
        val name = "upstream_${transactions.size}"
        if (!successful) execSQL("ROLLBACK TO SAVEPOINT $name")
        execSQL("RELEASE SAVEPOINT $name")
    }
}

internal fun SQLiteStatement.bindArguments(arguments: Array<out Any?>) {
    arguments.forEachIndexed { index, value ->
        when (value) {
            null -> bindNull(index + 1)
            is ByteArray -> bindBlob(index + 1, value)
            is Boolean -> bindLong(index + 1, if (value) 1 else 0)
            is Float -> bindDouble(index + 1, value.toDouble())
            is Double -> bindDouble(index + 1, value)
            is Number -> bindLong(index + 1, value.toLong())
            else -> bindText(index + 1, value.toString())
        }
    }
}

internal fun SQLiteStatement.readCursor(): SQLiteCursor {
    val rows = buildList {
        while (step()) {
            add(List(getColumnCount()) { column ->
                when (getColumnType(column)) {
                    1 -> getLong(column)
                    2 -> getDouble(column)
                    3 -> getText(column)
                    4 -> getBlob(column)
                    else -> null
                }
            })
        }
    }
    return SQLiteCursor(rows)
}

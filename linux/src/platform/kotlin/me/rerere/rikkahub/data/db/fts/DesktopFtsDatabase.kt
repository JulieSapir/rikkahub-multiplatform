package me.rerere.rikkahub.data.db.fts

import androidx.room.Transactor
import androidx.room.immediateTransaction
import androidx.sqlite.db.SQLiteCursor
import androidx.sqlite.db.bindArguments
import androidx.sqlite.db.readCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.AppDatabase

/** Runs upstream FTS SQL on Room's own writer/reader, including nested transactions. */
internal class DesktopFtsDatabase(private val connection: Transactor) {
    suspend fun execSQL(sql: String, bindArgs: Array<out Any?> = emptyArray()) {
        connection.usePrepared(sql) { statement ->
            statement.bindArguments(bindArgs)
            statement.step()
        }
    }

    suspend fun query(sql: String, bindArgs: Array<out Any?> = emptyArray()): SQLiteCursor =
        connection.usePrepared(sql) { statement ->
            statement.bindArguments(bindArgs)
            statement.readCursor()
        }
}

internal suspend fun <R> AppDatabase.withFtsWriter(block: suspend (DesktopFtsDatabase) -> R): R =
    withContext(Dispatchers.IO) {
        useConnection(isReadOnly = false) { connection ->
            connection.immediateTransaction { block(DesktopFtsDatabase(connection)) }
        }
    }

internal suspend fun <R> AppDatabase.withFtsReader(block: suspend (DesktopFtsDatabase) -> R): R =
    withContext(Dispatchers.IO) {
        useConnection(isReadOnly = true) { connection -> block(DesktopFtsDatabase(connection)) }
    }

package me.rerere.rikkahub.data.sync

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.DesktopSQLiteDriver
import java.io.File

internal object DatabaseBackup {
    const val ARCHIVE_DATABASE = "rikka_hub.db"
    const val WAL = "rikka_hub-wal"
    const val SHM = "rikka_hub-shm"

    /** Use Room's writer so the snapshot includes committed WAL data without racing other writers. */
    suspend fun createSnapshot(database: AppDatabase, destination: File) {
        check(!destination.exists()) { "Backup snapshot already exists" }
        database.useConnection(isReadOnly = false) { connection ->
            connection.usePrepared("VACUUM main INTO ?") { statement ->
                statement.bindText(1, destination.absolutePath)
                statement.step()
            }
        }
    }

    /** Open only the staged copy, with the same extensions as the live database. */
    fun normalize(databaseFile: File) {
        require(databaseFile.isFile && databaseFile.length() > 0) { "Backup database is missing or empty" }
        DesktopSQLiteDriver.create().open(databaseFile.absolutePath).use { connection ->
            checkpoint(connection)
            SupportSQLiteDatabase(connection).query("PRAGMA integrity_check").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) {
                    "Backup database failed its integrity check"
                }
            }
        }
        removeSidecars(databaseFile)
    }

    /** Opening the Room writer also runs schema validation and migrations before publication. */
    suspend fun checkpoint(database: AppDatabase) {
        database.useConnection(isReadOnly = false) { connection ->
            connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { statement ->
                check(statement.step() && statement.getLong(0) == 0L) {
                    "Could not checkpoint the backup database"
                }
            }
        }
    }

    private fun checkpoint(connection: SQLiteConnection) {
        connection.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use { statement ->
            check(statement.step() && statement.getLong(0) == 0L) {
                "Could not checkpoint the backup database"
            }
        }
    }

    fun removeSidecars(databaseFile: File) {
        check(File(databaseFile.path + "-wal").length() == 0L) { "Backup WAL was not fully checkpointed" }
        // Room JVM leaves a lock file beside the staged database after closing.
        for (suffix in listOf("-wal", "-shm", ".lck")) {
            val sidecar = File(databaseFile.path + suffix)
            check(!sidecar.exists() || sidecar.delete()) { "Could not remove staged database sidecar" }
        }
    }
}

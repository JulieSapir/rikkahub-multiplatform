package me.rerere.rikkahub.data.sync

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.DesktopSQLiteDriver
import me.rerere.rikkahub.data.db.createUpstreamSchema
import me.rerere.rikkahub.data.db.useDatabase
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupCompatibilityTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Application
    private lateinit var live: AppDatabase
    private lateinit var manager: BackupManager
    private lateinit var scope: CoroutineScope

    @Before fun setUp() = runBlocking {
        context = Application(File(temporary.root, "data"), File(temporary.root, "cache").apply { mkdirs() })
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(temporary.root, "settings.preferences_pb")
        }
        live = AppDatabaseFactory.create(context)
        live.exec("CREATE TABLE backup_probe (text TEXT)")
        live.exec("INSERT INTO backup_probe VALUES ('live')")
        manager = BackupManager(context, live, SettingsStore(dataStore, scope), JsonInstant)
    }

    @After fun tearDown() {
        live.close()
        scope.cancel()
    }

    @Test fun `new archive is standalone and preserves upstream entry names and FTS`() = runBlocking {
        live.exec("CREATE VIRTUAL TABLE search_probe USING fts5(text, tokenize='simple')")
        live.exec("INSERT INTO search_probe VALUES ('你好 backup')")
        val archive = manager.createBackup(includeDatabase = true, includeFiles = false)
        val snapshot = File(temporary.root, "snapshot")
        ZipFile(archive).use { zip ->
            assertEquals(setOf("settings.json", "rikka_hub.db"), zip.entries().asSequence().map { it.name }.toSet())
            zip.getInputStream(zip.getEntry(DatabaseBackup.ARCHIVE_DATABASE)).use { input ->
                snapshot.outputStream().use { input.copyTo(it) }
            }
        }
        live.exec("INSERT INTO backup_probe VALUES ('after snapshot')")
        DatabaseBackup.normalize(snapshot)
        DesktopSQLiteDriver.create().open(snapshot.absolutePath).use { connection ->
            val db = SupportSQLiteDatabase(connection)
            assertEquals(1, db.query("SELECT count(*) FROM backup_probe").use {
                it.moveToFirst()
                it.getInt(0)
            })
            assertEquals(1, db.query("SELECT count(*) FROM search_probe WHERE search_probe MATCH '你好'").use {
                it.moveToFirst()
                it.getInt(0)
            })
        }
        assertFalse(File(snapshot.path + "-wal").exists())
        assertFalse(File(snapshot.path + "-shm").exists())
        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        assertEquals("live", live.probe())
    }

    @Test fun `legacy WAL archive ignores SHM and replaces data only at startup`() = runBlocking {
        val source = File(temporary.root, "source")
        val archive = File(temporary.root, "legacy.zip")
        AppDatabaseFactory.createForRestore(source.path).useDatabase { database ->
            database.exec("CREATE TABLE backup_probe (text TEXT)")
            database.exec("PRAGMA wal_autocheckpoint=0")
            DatabaseBackup.checkpoint(database)
            database.exec("INSERT INTO backup_probe VALUES ('committed only in WAL')")
            assertTrue(File(source.path + "-wal").length() > 0)
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.add(DatabaseBackup.ARCHIVE_DATABASE, source.readBytes())
                zip.add(DatabaseBackup.WAL, File(source.path + "-wal").readBytes())
                zip.add(DatabaseBackup.SHM, "garbage shared memory".toByteArray())
            }
        }
        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        assertEquals("live", live.probe())
        live.close()
        assertTrue(BackupManager.applyPendingRestore(context, JsonInstant))
        assertFalse(BackupManager.applyPendingRestore(context, JsonInstant))
        AppDatabaseFactory.create(context).useDatabase { assertEquals("committed only in WAL", it.probe()) }
    }

    @Test fun `settings launch count is preserved in archive and staging`() = runBlocking {
        val current = manager.createBackup(includeDatabase = false, includeFiles = false)
        val settingsJson = ZipFile(current).use { zip ->
            zip.getInputStream(zip.getEntry("settings.json")).readBytes().decodeToString()
        }
        manager.stageRestore(current, includeDatabase = false, includeFiles = false)
        val pending = File(context.noBackupFilesDir, "backup-restore/pending/settings.json")
        val original = JsonInstant.parseToJsonElement(settingsJson).jsonObject["launchCount"]!!.jsonPrimitive.int
        assertEquals(
            original,
            JsonInstant.parseToJsonElement(pending.readText()).jsonObject["launchCount"]!!.jsonPrimitive.int
        )
    }

    @Test fun `archives without launch count stage the upstream zero default`() = runBlocking {
        val current = manager.createBackup(includeDatabase = false, includeFiles = false)
        val settingsJson = ZipFile(current).use { zip ->
            zip.getInputStream(zip.getEntry("settings.json")).readBytes().decodeToString()
        }
        val pending = File(context.noBackupFilesDir, "backup-restore/pending/settings.json")
        val archive = File(temporary.root, "old-settings.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.add(
                "settings.json",
                JsonObject(
                    JsonInstant.parseToJsonElement(settingsJson).jsonObject - "launchCount"
                ).toString().toByteArray()
            )
        }
        manager.stageRestore(archive, includeDatabase = false, includeFiles = false)
        assertEquals(
            0,
            JsonInstant.parseToJsonElement(pending.readText()).jsonObject["launchCount"]!!.jsonPrimitive.int
        )
    }

    @Test fun `nested attachments survive while partial downloads are excluded`() = runBlocking {
        val session = File(context.filesDir, "media_creation/session")
        File(session, "record").mkdirs()
        File(session, "draft").mkdirs()
        File(session, "record/out.png").writeText("image")
        File(session, "record/out.mp4.part").writeText("unfinished")
        File(session, "draft/asset.jpg").writeText("asset")
        val archive = manager.createBackup(includeDatabase = false, includeFiles = true)
        ZipFile(archive).use { zip ->
            assertTrue(zip.getEntry("media_creation/session/record/out.png") != null)
            assertTrue(zip.getEntry("media_creation/session/draft/asset.jpg") != null)
            assertEquals(null, zip.getEntry("media_creation/session/record/out.mp4.part"))
        }
        session.deleteRecursively()
        manager.stageRestore(archive, includeDatabase = false, includeFiles = true)
        assertFalse(session.exists())
        // Keep the global settings store untouched; settings staging is covered separately.
        assertTrue(File(context.noBackupFilesDir, "backup-restore/pending/settings.json").delete())
        assertTrue(BackupManager.applyPendingRestore(context, JsonInstant))
        assertEquals("image", File(session, "record/out.png").readText())
        assertEquals("asset", File(session, "draft/asset.jpg").readText())
        assertFalse(File(session, "record/out.mp4.part").exists())
    }

    @Test fun `old upstream database migrates on staging copy`() = runBlocking {
        val source = File(temporary.root, "old")
        createUpstreamSchema(source, 30) { db ->
            db.execSQL(
                "INSERT INTO ConversationEntity (id, title, nodes, create_at, update_at) VALUES ('old', 'old title', '[]', 1, 1)"
            )
        }
        val archive = File(temporary.root, "old.zip")
        ZipOutputStream(archive.outputStream()).use { it.add(DatabaseBackup.ARCHIVE_DATABASE, source.readBytes()) }
        manager.stageRestore(archive, includeDatabase = true, includeFiles = false)
        val staged = File(context.noBackupFilesDir, "backup-restore/pending/payload/database/database")
        DesktopSQLiteDriver.create().open(staged.path).use { connection ->
            val db = SupportSQLiteDatabase(connection)
            assertEquals(32, db.query("PRAGMA user_version").use {
                it.moveToFirst()
                it.getInt(0)
            })
            assertEquals("old title", db.query("SELECT title FROM ConversationEntity").use {
                it.moveToFirst()
                it.getString(0)
            })
        }
        assertEquals("live", live.probe())
    }

    @Test fun `unsupported and corrupt databases are rejected before publication`() = runBlocking {
        val future = File(temporary.root, "future")
        createUpstreamSchema(future, 32) { it.execSQL("PRAGMA user_version=9999") }
        for ((name, bytes) in listOf("future" to future.readBytes(), "corrupt" to "broken sqlite".toByteArray())) {
            val archive = File(temporary.root, "$name.zip")
            ZipOutputStream(archive.outputStream()).use { it.add(DatabaseBackup.ARCHIVE_DATABASE, bytes) }
            assertRejected { manager.stageRestore(archive, includeDatabase = true, includeFiles = false) }
            assertFalse(File(context.noBackupFilesDir, "backup-restore/pending").exists())
            assertEquals("live", live.probe())
        }
    }

    @Test fun `unsafe paths and orphan WAL are rejected without changing live data`() = runBlocking {
        for ((name, entry) in listOf("path" to "skills/../../outside", "wal" to DatabaseBackup.WAL)) {
            val archive = File(temporary.root, "$name.zip")
            ZipOutputStream(archive.outputStream()).use { it.add(entry, "payload".toByteArray()) }
            assertRejected { manager.stageRestore(archive, includeDatabase = true, includeFiles = true) }
            assertFalse(File(context.noBackupFilesDir, "backup-restore/pending").exists())
            assertEquals("live", live.probe())
        }
        assertFalse(File(context.noBackupFilesDir, "outside").exists())
    }

    private suspend fun assertRejected(block: suspend () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue("Invalid backup must be rejected", rejected)
    }

    private fun ZipOutputStream.add(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private suspend fun AppDatabase.exec(sql: String) = useConnection(isReadOnly = false) {
        it.usePrepared(sql) { statement ->
            statement.step()
            Unit
        }
    }

    private suspend fun AppDatabase.probe(): String = useConnection(isReadOnly = true) {
        it.usePrepared("SELECT text FROM backup_probe ORDER BY rowid LIMIT 1") { statement ->
            check(statement.step())
            statement.getText(0)
        }
    }
}

package me.rerere.rikkahub.data.db

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.fts.MessageFtsManager
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DatabaseCompatibilityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `all upstream historical schemas migrate without destructive fallback`() = runBlocking {
        for (version in 1..31) {
            val file = File(temporary.root, "schema-$version")
            createUpstreamSchema(file, version)
            AppDatabaseFactory.createForRestore(file.absolutePath).useDatabase { database ->
                assertEquals("schema $version", 0, database.conversationDao().countAll())
            }
            DesktopSQLiteDriver.create().open(file.absolutePath).use { connection ->
                val db = SupportSQLiteDatabase(connection)
                assertEquals(32L, db.query("PRAGMA user_version").use {
                    it.moveToFirst()
                    it.getLong(0)
                })
                assertTrue(db.query("PRAGMA integrity_check").use {
                    it.moveToFirst()
                    it.getString(0) == "ok"
                })
            }
        }
    }

    @Test
    fun `upstream usage migration preserves rows and backfills usage`() = runBlocking {
        val file = File(temporary.root, "usage")
        createUpstreamSchema(file, 30) { db ->
            db.execSQL(
                "INSERT INTO ConversationEntity (id, title, nodes, create_at, update_at) VALUES ('conv', 'title', '[]', 1000, 1000)"
            )
            db.execSQL(
                "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) VALUES ('node', 'conv', 0, ?, 0)",
                arrayOf(
                    """[{"role":"assistant","createdAt":"2026-08-12T09:01:00","modelId":"model-a","usage":{"promptTokens":100,"completionTokens":40,"cachedTokens":20}}]"""
                ),
            )
        }
        AppDatabaseFactory.createForRestore(file.absolutePath).useDatabase { database ->
            assertEquals(1, database.conversationDao().countAll())
        }
        DesktopSQLiteDriver.create().open(file.absolutePath).use { connection ->
            val db = SupportSQLiteDatabase(connection)
            db.query(
                "SELECT source, model_id, prompt_tokens, completion_tokens, cached_tokens FROM usage_record"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("CHAT", cursor.getString(0))
                assertEquals("model-a", cursor.getString(1))
                assertEquals(100, cursor.getInt(2))
                assertEquals(40, cursor.getInt(3))
                assertEquals(20, cursor.getInt(4))
                assertFalse(cursor.moveToNext())
            }
        }
    }

    @Test
    fun `existing Linux index is rebuilt from original message text without changing messages`() = runBlocking {
        val context = Application(File(temporary.root, "data"), File(temporary.root, "cache"))
        val file = context.getDatabasePath(SQLiteConfiguration.DATABASE_NAME)
        file.parentFile!!.mkdirs()
        val messages = JsonInstant.encodeToString(listOf(UIMessage.assistant("你好世界 hello world")))
        createUpstreamSchema(file, 32) { db ->
            db.execSQL(
                "INSERT INTO ConversationEntity (id, title, nodes, create_at, update_at) VALUES ('conv', 'original title', '[]', 1000, 1000)"
            )
            db.execSQL(
                "INSERT INTO message_node (id, conversation_id, node_index, messages, select_index) VALUES ('node', 'conv', 0, ?, 0)",
                arrayOf(messages),
            )
            db.execSQL(
                "CREATE VIRTUAL TABLE message_fts USING fts5(text, node_id UNINDEXED, message_id UNINDEXED, conversation_id UNINDEXED, title UNINDEXED, update_at UNINDEXED, tokenize='unicode61')"
            )
            db.execSQL("INSERT INTO message_fts(text, conversation_id) VALUES ('旧 的 预 分词', 'conv')")
        }
        repeat(2) {
            AppDatabaseFactory.create(context).useDatabase { database ->
                val fts = MessageFtsManager(database)
                assertEquals("original title", fts.search("你好世界").single().title)
                assertTrue(fts.search("hello").single().snippet.contains("hello"))
            }
        }
        DesktopSQLiteDriver.create().open(file.absolutePath).use { connection ->
            val db = SupportSQLiteDatabase(connection)
            assertEquals(messages, db.query("SELECT messages FROM message_node").use {
                it.moveToFirst()
                it.getString(0)
            })
            assertEquals("你好世界 hello world", db.query("SELECT text FROM message_fts").use {
                it.moveToFirst()
                it.getString(0)
            })
        }
    }

    @Test
    fun `unsupported schema is rejected without replacing existing data`() = runBlocking {
        val file = File(temporary.root, "future")
        createUpstreamSchema(file, 32) { db ->
            db.execSQL(
                "INSERT INTO ConversationEntity (id, title, nodes, create_at, update_at) VALUES ('kept', 'keep me', '[]', 1000, 1000)"
            )
            db.execSQL("PRAGMA user_version = 9999")
        }
        var rejected = false
        try {
            AppDatabaseFactory.createForRestore(file.absolutePath).useDatabase { it.conversationDao().countAll() }
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
        // Room may update journal mode, but the schema version and stored tables survive.
        DesktopSQLiteDriver.create().open(file.absolutePath).use { connection ->
            val db = SupportSQLiteDatabase(connection)
            assertEquals(9999L, db.query("PRAGMA user_version").use {
                it.moveToFirst()
                it.getLong(0)
            })
            assertEquals("keep me", db.query("SELECT title FROM ConversationEntity WHERE id = 'kept'").use {
                it.moveToFirst()
                it.getString(0)
            })
        }
    }
}

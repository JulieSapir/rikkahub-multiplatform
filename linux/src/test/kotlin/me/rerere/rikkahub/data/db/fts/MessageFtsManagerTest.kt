package me.rerere.rikkahub.data.db.fts

import android.app.Application
import androidx.room.withTransaction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.uuid.Uuid

class MessageFtsManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Application
    private lateinit var database: AppDatabase
    private lateinit var manager: MessageFtsManager

    @Before
    fun setUp(): Unit = runBlocking {
        context = Application(
            File(temporaryFolder.root, "data"),
            File(temporaryFolder.root, "cache"),
        )
        database = AppDatabaseFactory.create(context)
        manager = MessageFtsManager(database)
        database.conversationDao().countAll()
        // Open the reader before exercising contention.
        manager.search("warmup")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `index waits for Room writer and persists after commit`() = runBlocking {
        withTimeout(10_000) {
            supervisorScope {
                val conversation = conversation("searchable")
                database.conversationDao().insert(conversation.entity())
                val acquired = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val writer = async(Dispatchers.IO) {
                    database.withTransaction {
                        database.conversationDao().updatePinStatus(conversation.id.toString(), true)
                        acquired.complete(Unit)
                        release.await()
                    }
                }
                acquired.await()
                val indexing = async(Dispatchers.IO) { manager.indexConversation(conversation) }
                try {
                    delay(150)
                    assertFalse("索引应等待 Room 释放写连接", indexing.isCompleted)
                } finally {
                    release.complete(Unit)
                }
                writer.await()
                indexing.await()
                assertEquals(listOf(conversation.id.toString()), manager.search("searchable").map { it.conversationId })
                assertTrue(database.conversationDao().getConversationById(conversation.id.toString())!!.isPinned)
            }
        }
    }

    @Test
    fun `concurrent Room writes indexing and search share the connection pool`() = runBlocking {
        withTimeout(10_000) {
            val conversations = List(12) { conversation("parallelmessage") }
            conversations.forEach { database.conversationDao().insert(it.entity()) }
            val updates = conversations.map { conversation ->
                async(Dispatchers.IO) {
                    database.conversationDao().updatePinStatus(conversation.id.toString(), true)
                    manager.indexConversation(conversation)
                }
            }
            val searches = async(Dispatchers.IO) { repeat(12) { manager.search("parallelmessage") } }
            updates.awaitAll()
            searches.await()
            assertEquals(
                conversations.map { it.id.toString() }.toSet(),
                manager.search("parallelmessage").map { it.conversationId }.toSet(),
            )

            val updated = conversations.first().copy(
                messageNodes = listOf(MessageNode.of(UIMessage.assistant("replacementmessage"))),
            )
            manager.indexConversation(updated)
            assertEquals(11, manager.search("parallelmessage").size)
            assertEquals(1, manager.search("replacementmessage").size)
            manager.deleteConversation(updated.id.toString())
            assertTrue(manager.search("replacementmessage").isEmpty())
            manager.deleteAll()
            assertTrue(manager.search("parallelmessage").isEmpty())
        }
    }

    @Test
    fun `index participates in outer Room transaction rollback`() = runBlocking {
        withTimeout(10_000) {
            val original = conversation("originalmessage")
            database.conversationDao().insert(original.entity())
            manager.indexConversation(original)
            val replacement = original.copy(
                messageNodes = listOf(MessageNode.of(UIMessage.assistant("replacementmessage"))),
            )
            try {
                database.withTransaction {
                    manager.indexConversation(replacement)
                    error("Rollback index replacement")
                }
            } catch (expected: IllegalStateException) {
                assertEquals("Rollback index replacement", expected.message)
            }
            assertEquals(1, manager.search("originalmessage").size)
            assertTrue(manager.search("replacementmessage").isEmpty())
        }
    }

    @Test
    fun `upstream incremental indexing preserves unchanged rowids and joins current titles`() = runBlocking {
        val apple = UIMessage.user("apple pie")
        val banana = UIMessage.assistant("banana split")
        val cherry = UIMessage.user("cherry tart")
        val original = conversation("unused").copy(
            title = "first",
            messageNodes = listOf(apple, banana, cherry).map { MessageNode.of(it) },
        )
        database.conversationDao().insert(original.entity())
        manager.indexConversation(original)
        val before = indexedRowids()
        val durian = banana.copy(parts = UIMessage.assistant("durian cake").parts)
        val elderberry = UIMessage.assistant("elderberry jam")
        val updated = original.copy(
            title = "renamed",
            messageNodes = original.messageNodes.take(1) +
                MessageNode.of(durian).copy(id = original.messageNodes[1].id) + MessageNode.of(elderberry),
        )
        database.conversationDao().update(updated.entity())
        manager.indexConversation(updated)
        val after = indexedRowids()
        assertEquals(before[apple.id.toString()], after[apple.id.toString()])
        assertNotEquals(before[banana.id.toString()], after[banana.id.toString()])
        assertEquals(setOf(apple.id, banana.id, elderberry.id).map { it.toString() }.toSet(), after.keys)
        assertTrue(manager.search("banana").isEmpty())
        assertTrue(manager.search("cherry").isEmpty())
        val result = manager.search("durian").single()
        assertEquals(banana.id.toString(), result.messageId)
        assertEquals("renamed", result.title)
        assertEquals(indexRows("SELECT rowid, conversation_id FROM message_fts ORDER BY rowid"),
            indexRows("SELECT fts_rowid, conversation_id FROM message_fts_rowid ORDER BY fts_rowid"))
    }

    @Test
    fun `simple tokenizer supports pinyin ASCII prefixes and mixed Chinese queries`() = runBlocking {
        val conversation = conversation("你好世界 hello Kotlin")
        database.conversationDao().insert(conversation.entity())
        manager.indexConversation(conversation)
        for (keyword in listOf("你好世界", "nihaoshijie", "HELLO", "kot", "你好 hello")) {
            assertEquals(keyword, listOf(conversation.id.toString()), manager.search(keyword).map { it.conversationId })
        }
        assertTrue(manager.search("你好 absent").isEmpty())
        assertTrue(manager.search("   ").isEmpty())
    }

    @Test
    fun `opening an upstream index without rowid mappings preserves existing entries`() = runBlocking {
        val first = conversation("legacy apple")
        val second = conversation("legacy banana")
        for (conversation in listOf(first, second)) {
            database.conversationDao().insert(conversation.entity())
            manager.indexConversation(conversation)
        }
        val before = indexedRowids()
        database.withFtsWriter { it.execSQL("DROP TABLE message_fts_rowid") }
        database.close()
        database = AppDatabaseFactory.create(context)
        manager = MessageFtsManager(database)
        assertEquals(before, indexedRowids())
        manager.deleteConversation(first.id.toString())
        assertEquals(listOf(second.id.toString()), manager.search("legacy").map { it.conversationId })
        assertEquals(indexRows("SELECT rowid, conversation_id FROM message_fts ORDER BY rowid"),
            indexRows("SELECT fts_rowid, conversation_id FROM message_fts_rowid ORDER BY fts_rowid"))
    }

    private suspend fun indexedRowids(): Map<String, Long> = database.withFtsReader { db ->
        db.query("SELECT message_id, rowid FROM message_fts").use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getLong(1)) }
        }
    }

    private suspend fun indexRows(sql: String): List<Pair<Long, String>> = database.withFtsReader { db ->
        db.query(sql).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getString(1)) }
        }
    }

    private fun conversation(text: String): Conversation = Conversation.ofId(
        id = Uuid.random(),
        messages = listOf(MessageNode.of(UIMessage.assistant(text))),
    )

    private fun Conversation.entity() = ConversationEntity(
        id = id.toString(),
        assistantId = assistantId.toString(),
        title = title,
        nodes = "[]",
        createAt = createAt.toEpochMilli(),
        updateAt = updateAt.toEpochMilli(),
        chatSuggestions = "[]",
        isPinned = false,
    )
}

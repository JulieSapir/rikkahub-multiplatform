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

    private lateinit var database: AppDatabase
    private lateinit var manager: MessageFtsManager

    @Before
    fun setUp(): Unit = runBlocking {
        val context = Application(
            File(temporaryFolder.root, "data"),
            File(temporaryFolder.root, "cache"),
        )
        database = AppDatabaseFactory.create(context)
        manager = MessageFtsManager(database)
        database.conversationDao().countAll()
        // 先加载词典，让并发测试直接覆盖连接竞争。
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

package me.rerere.rikkahub.data.db.fts

import android.content.Context
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.huaban.analysis.jieba.JiebaSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.AppDatabaseFactory
import me.rerere.rikkahub.data.model.Conversation
import java.io.File
import java.time.Instant

/**
 * 消息全文搜索的桌面端版本（上游那份在 linux/build.gradle.kts 里 exclude）。
 *
 * 与 Android 版的实质差别是分词的落点：上游用他们 fork 的 sqlite-android 自带的 jieba 扩展
 * （`jieba_query()` / `simple_snippet()`），JVM 侧只有 SQLite 自带的 fts5，所以这里在写入与
 * 查询两侧用 huaban 的 jieba-analysis 先切词，token 用空格 join 后交给 `unicode61` 按空格/标点
 * 切 token，等效中文按词索引。表结构、方法签名与返回类型保持一致。代价有两个：
 * snippet 会按分词结果显示（词与词之间多出空格）；查询是各 token 的 OR 并集召回，
 * 召回比 Android 端 jieba_query 的精确匹配宽（可能带出只命中其中一个词的行），交给 rank 排序兜底。
 */
enum class MessageSearchSort(val orderBy: String) {
    RELEVANCE("rank, update_at DESC"),
    NEWEST_FIRST("update_at DESC, rank"),
    OLDEST_FIRST("update_at ASC, rank"),
}

data class MessageSearchResult(
    val nodeId: String,
    val messageId: String,
    val conversationId: String,
    val title: String,
    val updateAt: Instant,
    val snippet: String,
)

class MessageFtsManager(private val database: AppDatabase) {

    // 词典是 JVM 静态加载的，共享一个实例即可；不确定其 process 是否并发安全，调用处串行
    private val jieba = lazy { JiebaSegmenter() }

    /** 中文按词、英文按原样切，空格 join 后 fts5 的 unicode61 就能按 token 建倒排 */
    private fun tokenize(text: String): String = synchronized(jieba) {
        jieba.value.process(text, JiebaSegmenter.SegMode.SEARCH)
            .joinToString(" ") { it.word }
            .trim()
    }

    private suspend fun <R> withConnection(block: (SQLiteConnection) -> R): R =
        withContext(Dispatchers.IO) {
            val path = requireNotNull(AppDatabaseFactory.databasePath) {
                "还没建过数据库，全文搜索不可用"
            }
            BundledSQLiteDriver().open(path).use { connection -> block(connection) }
        }

    suspend fun indexConversation(conversation: Conversation) = withConnection { db ->
        val conversationId = conversation.id.toString()
        // 先删后插必须是一个事务，中断在中间会让这条会话在全文索引里只剩一半
        db.exec("BEGIN IMMEDIATE")
        try {
            db.exec("DELETE FROM message_fts WHERE conversation_id = ?") { bindText(1, conversationId) }
            conversation.messageNodes.forEach { node ->
                node.messages.forEach { message ->
                    // 存进去的是分词后的文本，snippet 因此也会带分词空格（可接受的代价）
                    val text = tokenize(message.extractFtsText())
                    if (text.isNotBlank()) {
                        db.exec(
                            """
                            INSERT INTO message_fts(text, node_id, message_id, conversation_id, title, update_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """.trimIndent(),
                        ) {
                            bindText(1, text)
                            bindText(2, node.id.toString())
                            bindText(3, message.id.toString())
                            bindText(4, conversationId)
                            bindText(5, conversation.title)
                            bindText(6, conversation.updateAt.toEpochMilli().toString())
                        }
                    }
                }
            }
            db.exec("COMMIT")
        } catch (t: Throwable) {
            runCatching { db.exec("ROLLBACK") }
            throw t
        }
    }

    suspend fun deleteConversation(conversationId: String) = withConnection { db ->
        db.exec("DELETE FROM message_fts WHERE conversation_id = ?") { bindText(1, conversationId) }
    }

    suspend fun deleteAll() = withConnection { db ->
        db.exec("DELETE FROM message_fts")
    }

    suspend fun search(
        keyword: String,
        sort: MessageSearchSort = MessageSearchSort.RELEVANCE,
        assistantId: String? = null,
    ): List<MessageSearchResult> = withConnection { db ->
        val assistantFilter = if (assistantId != null) {
            """
            AND EXISTS (
                SELECT 1 FROM conversationentity AS conversation
                WHERE conversation.id = message_fts.conversation_id
                  AND conversation.assistant_id = ?
            )
            """.trimIndent()
        } else {
            ""
        }
        val sql = """
            SELECT node_id, message_id, conversation_id, title, update_at,
                   snippet(message_fts, 0, '[', ']', '...', 30) AS snippet
            FROM message_fts
            WHERE text MATCH ?
            $assistantFilter
            ORDER BY ${sort.orderBy}
            LIMIT 50
        """.trimIndent()

        val results = mutableListOf<MessageSearchResult>()
        // 关键词同样先分词：每个 token 双引号包裹后用 OR 连接（并集召回，见类注释的语义差异说明）。
        // 纯标点的 token 在 unicode61 下切不出词，直接丢掉；全被丢掉时退回上游那种整词匹配。
        val match = tokenize(keyword)
            .split(' ')
            .map { it.trim() }
            .filter { token -> token.any { it.isLetterOrDigit() } }
            .joinToString(" OR ") { "\"" + it.replace("\"", " ") + "\"" }
            .ifEmpty { "\"" + keyword.replace("\"", " ") + "\"" }
        db.prepare(sql).use { statement ->
            statement.bindText(1, match)
            if (assistantId != null) statement.bindText(2, assistantId)
            while (statement.step()) {
                results.add(
                    MessageSearchResult(
                        nodeId = statement.getText(0).orEmpty(),
                        messageId = statement.getText(1).orEmpty(),
                        conversationId = statement.getText(2).orEmpty(),
                        title = statement.getText(3).orEmpty(),
                        updateAt = Instant.ofEpochMilli(statement.getLong(4)),
                        snippet = statement.getText(5).orEmpty(),
                    )
                )
            }
        }
        results
    }
}

/** 上游用它解压 jieba 词典，桌面端用不到，只为保持 API 完整 */
object SimpleDictManager {
    fun extractDict(context: Context): File = File(context.cacheDir, "jieba_dict").apply { mkdirs() }
}

/** SQLiteConnection 只提供 prepare，这里补一个“执行不取结果”的辅助函数 */
private inline fun SQLiteConnection.exec(sql: String, bind: SQLiteStatement.() -> Unit = {}) {
    prepare(sql).use { statement ->
        statement.bind()
        statement.step()
    }
}

private fun UIMessage.extractFtsText(): String =
    parts.filterIsInstance<UIMessagePart.Text>()
        .joinToString("\n") { it.text }
        .take(10_000)

package me.rerere.rikkahub.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.fts.createMessageFtsTables
import me.rerere.rikkahub.data.db.fts.extractFtsText
import me.rerere.rikkahub.utils.JsonInstant

/** Rebuild only the old Linux-derived index from authoritative message JSON. */
internal fun migrateDesktopFts(db: SupportSQLiteDatabase) {
    val legacy = db.query("SELECT sql FROM sqlite_master WHERE name = 'message_fts'").use { cursor ->
        cursor.moveToFirst() && cursor.getString(0).contains("unicode61", ignoreCase = true)
    }
    if (!legacy) return
    db.beginTransaction()
    try {
        db.execSQL("DROP TABLE message_fts")
        db.execSQL("DROP TABLE IF EXISTS message_fts_rowid")
        createMessageFtsTables(db)
        db.query("SELECT id, conversation_id, messages FROM message_node").use { cursor ->
            while (cursor.moveToNext()) {
                val nodeId = cursor.getString(0)
                val conversationId = cursor.getString(1)
                val messages = JsonInstant.decodeFromString<List<UIMessage>>(cursor.getString(2))
                messages.forEach { message ->
                    val text = message.extractFtsText()
                    if (text.isNotBlank()) {
                        db.execSQL(
                            "INSERT INTO message_fts(text, node_id, message_id, conversation_id) VALUES (?, ?, ?, ?)",
                            arrayOf(text, nodeId, message.id.toString(), conversationId),
                        )
                        db.execSQL(
                            "INSERT INTO message_fts_rowid(fts_rowid, conversation_id) VALUES (last_insert_rowid(), ?)",
                            arrayOf(conversationId),
                        )
                    }
                }
            }
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

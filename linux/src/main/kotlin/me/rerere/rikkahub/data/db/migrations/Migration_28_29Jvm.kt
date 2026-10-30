package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection

/**
 * 上游这份 AutoMigrationSpec 绑 Android 的 SupportSQLiteDatabase（原文件已 exclude）。
 * AppDatabase 的 @AutoMigration 声明按类名引用它，桌面端保留同名类，回调换成 Room KMP 的
 * SQLiteConnection；语义不变：已有的对话视为整理过，否则升级后第一次整理会把整段历史都交给模型。
 * （桌面端库走 fallbackToDestructiveMigration，真正触发升级的场景只有备份恢复，同样需要这张表的回填。）
 */
class Migration_28_29 : AutoMigrationSpec {
    override fun onPostMigrate(db: SQLiteConnection) {
        db.prepare(
            "INSERT OR IGNORE INTO memory_consolidation (conversation_id, consolidated_at) " +
                "SELECT id, update_at FROM ConversationEntity"
        ).use { it.step() }
    }
}

package me.rerere.rikkahub.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.databaseBuilder
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

/**
 * 建库工厂的桌面端版本（上游那份绑 requery 驱动与 Android 的 SupportSQLiteDatabase 回调）。
 *
 * 与 Android 版的差异：
 * - 驱动用 Room 自带的 BundledSQLiteDriver
 * - 不跑 Android 那 5 个手写迁移；桌面端库文件独立，版本对不上时按当前 schema 重建
 * - message_fts 用 SQLite 内置 unicode61 分词器；Android 端的 simple(jieba) 依赖他们 fork 的
 *   sqlite-android 扩展，JVM 侧没有
 * 
 * MPTODO: full jieba sqlite
 */
internal object AppDatabaseFactory {
    const val DATABASE_NAME = "database"

    /** 全文搜索要自己开一条连接跑 fts5 语句，这里记下建库用的文件路径 */
    var databasePath: String? = null
        private set

    fun create(context: Context, name: String = DATABASE_NAME): AppDatabase {
        val file = File(context.dataDir, "database/$name").apply { parentFile?.mkdirs() }
        databasePath = file.absolutePath
        return Room.databaseBuilder(context, AppDatabase::class.java, name)
            .setDriver(BundledSQLiteDriver())
            .addCallback(CreateFtsTable)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    /**
     * 恢复备份时按指定路径开一份临时库，只用来验证 schema：这里故意不挂
     * fallbackToDestructiveMigration，版本对不上会直接抛异常，暂存的副本不会被清空。
     */
    fun createForRestore(path: String): AppDatabase =
        Room.databaseBuilder(AppDatabase::class.java, path)
            .setDriver(BundledSQLiteDriver())
            .build()

    private object CreateFtsTable : RoomDatabase.Callback() {
        override fun onCreate(connection: SQLiteConnection) = ensureFts(connection)

        override fun onOpen(connection: SQLiteConnection) = ensureFts(connection)

        private fun ensureFts(connection: SQLiteConnection) {
            connection.prepare(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS message_fts USING fts5(
                    text,
                    node_id UNINDEXED,
                    message_id UNINDEXED,
                    conversation_id UNINDEXED,
                    title UNINDEXED,
                    update_at UNINDEXED,
                    tokenize = 'unicode61'
                )
                """.trimIndent()
            ).use { it.step() }
        }
    }
}

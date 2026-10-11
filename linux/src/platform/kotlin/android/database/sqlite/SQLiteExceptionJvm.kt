package android.database.sqlite

/** 上游 ConversationRepository 会捕获这个异常来判断“单行过大”，桌面端由 SQLite 驱动抛通用异常 */
class SQLiteBlobTooBigException(message: String? = null) : RuntimeException(message)

class SQLiteException(message: String? = null) : RuntimeException(message)

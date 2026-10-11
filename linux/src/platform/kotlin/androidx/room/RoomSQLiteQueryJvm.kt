package androidx.room

import androidx.sqlite.db.SupportSQLiteQuery

/**
 * Room 生成 `@RawQuery` 代码时会调 `RoomSQLiteQuery.copyFrom(query).toRoomRawQuery()`，
 * 这个辅助类只存在于 room 的 Android 产物里；KMP 侧的 RoomRawQuery 是有的，这里补上转换。
 */
class RoomSQLiteQuery private constructor(private val query: SupportSQLiteQuery) {

    fun toRoomRawQuery(): RoomRawQuery = RoomRawQuery(query.sql) { statement ->
        query.bindTo(statement)
    }

    companion object {
        @JvmStatic
        fun copyFrom(query: SupportSQLiteQuery): RoomSQLiteQuery = RoomSQLiteQuery(query)
    }
}

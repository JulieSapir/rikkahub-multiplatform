package androidx.room

/**
 * room-ktx 的 `RoomDatabase.withTransaction` 没有 jvm 产物（Android 端上游大量用它包事务）。
 *
 * Room 的 KMP 运行时把事务入口放在 useConnection(readonly) { transactor -> } 上，
 * 这里按 Android 的语义组合出同名扩展：拿一条可写连接，在 deferred 事务里执行块。
 */
suspend fun <R> RoomDatabase.withTransaction(block: suspend TransactionScope<R>.() -> R): R =
    useConnection(isReadOnly = false) { transactor ->
        transactor.deferredTransaction(block)
    }

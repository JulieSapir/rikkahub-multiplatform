package androidx.room

/**
 * 按绝对路径创建数据库，应用启动与备份恢复校验共用此入口。
 * 工厂实例化 KSP 生成的同包 `<类名>_Impl`。
 */
inline fun <reified T : RoomDatabase> Room.databaseBuilder(
    databaseClass: Class<T>,
    path: String,
): RoomDatabase.Builder<T> = Room.databaseBuilder<T>(name = path) {
    newGeneratedInstance(databaseClass)
}

@PublishedApi
internal fun <T : RoomDatabase> newGeneratedInstance(databaseClass: Class<T>): T {
    val impl = Class.forName("${databaseClass.packageName}.${databaseClass.simpleName}_Impl")
    @Suppress("UNCHECKED_CAST")
    return impl.getDeclaredConstructor().newInstance() as T
}

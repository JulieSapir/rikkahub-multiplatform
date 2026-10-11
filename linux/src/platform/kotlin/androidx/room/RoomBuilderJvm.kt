package androidx.room

import android.content.Context
import java.io.File

/**
 * 上游建库代码用的是 Android 版 `Room.databaseBuilder(context, klass, name)`；Room 的 KMP 版签名是
 * `databaseBuilder(path) { factory }`。补两个同名重载，上游调用点不用改。
 *
 * factory 里实例化的是 KSP 生成的 `<类名>_Impl`（与数据库类同包），这和 Android 版
 * Room 内部的做法一致：直接反射数据库抽象类会失败。
 */
inline fun <reified T : RoomDatabase> Room.databaseBuilder(
    context: Context,
    databaseClass: Class<T>,
    name: String,
): RoomDatabase.Builder<T> = Room.databaseBuilder<T>(
    name = File(context.dataDir, "database/$name").apply { parentFile?.mkdirs() }.absolutePath,
) {
    newGeneratedInstance(databaseClass)
}

/** 按绝对路径建库，恢复备份时校验暂存副本用这个入口。 */
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

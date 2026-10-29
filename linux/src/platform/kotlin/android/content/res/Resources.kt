package android.content.res

import android.content.Context
import java.util.Locale

/**
 * android.content.res.Resources 的桌面端替身，只覆盖上游用到的字符串读取；
 * 真正的表由 linux/gradle/android-res.gradle.kts 从 app/src/main/res 生成。
 */
/** Android 的 LocaleList 在桌面端只有当前这一个语言。 */
class LocaleList(private val first: Locale) {
    val size: Int get() = 1

    fun size(): Int = size

    val isEmpty: Boolean get() = false

    operator fun get(index: Int): Locale = if (index == 0) first else throw IndexOutOfBoundsException(index)
}

class Resources(private val context: Context = Context()) {
    val configuration: Configuration = Configuration()

    fun getString(id: Int): String = context.getString(id)

    fun getString(id: Int, vararg args: Any?): String = context.getString(id, *args)

    class Configuration {
        val locale: Locale get() = Locale.getDefault()

        // 与 locale 保持同源：语言是活值，构造时冻结的快照会在改语言后互相矛盾
        val locales: LocaleList get() = LocaleList(Locale.getDefault())
    }
}

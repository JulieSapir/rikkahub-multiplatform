package android.content

import android.util.Log
import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Android 的 SharedPreferences 在桌面端落成 `<dataDir>/preferences/<name>.properties`。
 *
 * 有了它，上游的 ui/hooks/SharedPreferences.kt、ColorMode.kt 就能原样编译，
 * 主题模式、amoled、调试开关这些偏好也和 Android 端走同一套代码。
 */
interface SharedPreferences {
    fun getString(key: String, defValue: String?): String?

    fun getBoolean(key: String, defValue: Boolean): Boolean

    fun getInt(key: String, defValue: Int): Int

    fun contains(key: String): Boolean

    fun all(): Map<String, *>

    fun edit(action: Editor.() -> Unit)

    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)

    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)

    interface Editor {
        fun putString(key: String, value: String?): Editor

        fun putBoolean(key: String, value: Boolean): Editor

        fun putInt(key: String, value: Int): Editor

        fun remove(key: String): Editor

        fun clear(): Editor

        fun apply()
    }

    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?)
    }
}

/**
 * 实现按文件名共享同一份内存状态：Android 上同名 SharedPreferences 是同一个实例，
 * 上游有的地方缓存了实例、有的地方重新 getSharedPreferences，共享才能保持一致。
 */
internal class FileSharedPreferences(private val file: File) : SharedPreferences {
    private val values = ConcurrentHashMap<String, String>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    init {
        runCatching {
            if (file.isFile) {
                Properties().let { props ->
                    file.inputStream().use(props::load)
                    props.stringPropertyNames().forEach { values[it] = props.getProperty(it) }
                }
            }
        }
        synchronized(instances) { instances[file.absolutePath] = this }
    }

    override fun getString(key: String, defValue: String?): String? = values[key] ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        values[key]?.toBooleanStrictOrNull() ?: defValue

    override fun getInt(key: String, defValue: Int): Int = values[key]?.toIntOrNull() ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun all(): Map<String, *> = values.toMap()

    override fun edit(action: SharedPreferences.Editor.() -> Unit) {
        EditorImpl().apply(action).apply()
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listeners) { listeners += listener }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listeners) { listeners -= listener }
    }

    private inner class EditorImpl : SharedPreferences.Editor {
        private val writes = linkedMapOf<String, String?>()
        private var wipe = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            apply { writes[key] = value }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            apply { writes[key] = value.toString() }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor =
            apply { writes[key] = value.toString() }

        override fun remove(key: String): SharedPreferences.Editor = apply { writes[key] = null }

        override fun clear(): SharedPreferences.Editor = apply { wipe = true }

        override fun apply() {
            val changed = mutableListOf<String>()
            if (wipe) {
                changed += values.keys
                values.clear()
            }
            writes.forEach { (key, value) ->
                if (value == null) {
                    if (values.remove(key) != null) changed += key
                } else {
                    if (values[key] != value) changed += key
                    values[key] = value
                }
            }
            persist()
            val snapshot = synchronized(listeners) { listeners.toList() }
            changed.forEach { key -> snapshot.forEach { it.onSharedPreferenceChanged(this@FileSharedPreferences, key) } }
        }
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val props = Properties()
            values.entries.forEach { entry -> props.setProperty(entry.key, entry.value) }
            file.outputStream().use { props.store(it, "rikka-desktop") }
        }.onFailure { Log.w(TAG, "写偏好文件失败: ${it.message}") }
    }

    companion object {
        private const val TAG = "DesktopSharedPreferences"
        private val instances = mutableMapOf<String, FileSharedPreferences>()

        /** 同名偏好文件复用一个实例，与 Android 行为对齐。 */
        fun of(file: File): SharedPreferences = synchronized(instances) {
            instances[file.absolutePath] ?: FileSharedPreferences(file)
        }
    }
}

/** Context.getSharedPreferences(name, mode) */
internal fun contextSharedPreferences(context: Context, name: String): SharedPreferences =
    FileSharedPreferences.of(File(context.noBackupFilesDir, "preferences/$name.properties"))


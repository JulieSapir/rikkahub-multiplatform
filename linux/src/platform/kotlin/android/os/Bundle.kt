package android.os

/** Android 的状态包，桌面端只用来在 Activity 生命周期替身里传几个键值。 */
class Bundle {
    private val values = linkedMapOf<String, Any?>()

    fun putString(key: String, value: String?) {
        values[key] = value
    }

    fun getString(key: String): String? = values[key] as? String

    fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean = values[key] as? Boolean ?: default

    fun containsKey(key: String): Boolean = key in values
}

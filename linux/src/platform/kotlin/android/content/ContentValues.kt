package android.content

/** MediaStore 插入行用的键值对，桌面端只是个 Map。 */
class ContentValues {
    private val values = linkedMapOf<String, Any?>()

    fun put(key: String, value: String?) { values[key] = value }

    fun put(key: String, value: Int?) { values[key] = value }

    fun put(key: String, value: Long?) { values[key] = value }

    operator fun get(key: String): Any? = values[key]

    fun valueSet(): Set<Map.Entry<String, Any?>> = values.entries

    fun size(): Int = values.size

    fun keySet(): Set<String> = values.keys
}

package android.util

/** android.util.LruCache 的等价实现：按条目数限额，访问顺序淘汰，方法线程安全 */
open class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>(0, 0.75f, true)

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V): V? {
        val previous = map.put(key, value)
        while (map.size > maxSize) {
            map.remove(map.keys.first())
        }
        return previous
    }

    @Synchronized
    fun remove(key: K): V? = map.remove(key)

    @Synchronized
    fun evictAll() = map.clear()

    @Synchronized
    fun size(): Int = map.size
}

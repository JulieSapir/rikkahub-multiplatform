package android.os

import android.content.Context

/**
 * 桌面端读不到系统电量：PlaceholderTransformer 会用 `{battery_level}` 占位符，
 * 这里给一个固定值，避免为一个小占位符把整个文件 fork 成 JVM 版。
 */
class BatteryManager(private val context: Context) {
    fun getIntProperty(key: String): Int = if (key == BATTERY_PROPERTY_CAPACITY) FULL_LEVEL else 0

    companion object {
        const val BATTERY_PROPERTY_CAPACITY = "capacity"
        const val FULL_LEVEL = 100
    }
}

/** 上游只用它做节流计时。 */
object SystemClock {
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000
}

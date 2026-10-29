package android.os

/**
 * ASR provider 的分段计时与通知限频拿 elapsedRealtime 当单调时钟（只比较差值）。
 * 桌面端没有 Android 的开机时刻，用类加载时的 nanoTime 锚点，同样单调递增。
 */
object SystemClock {
    private val bootNanos = System.nanoTime()

    fun elapsedRealtime(): Long = (System.nanoTime() - bootNanos) / 1_000_000
}

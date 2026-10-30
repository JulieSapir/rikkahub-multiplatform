package me.rerere.rikkahub.service

import android.content.Context
import me.rerere.rikkahub.data.ai.MemoryDreamKeepAlive

/**
 * 上游的保活靠 MemoryDreamForegroundService 前台服务（Android 专属，见 build.gradle.kts 的 exclude）。
 * 桌面端进程本身常驻，不需要额外保活：给一个不占用任何资源的空实现，签名与上游一致。
 */
fun memoryDreamKeepAlive(context: Context): MemoryDreamKeepAlive = MemoryDreamKeepAlive { AutoCloseable {} }

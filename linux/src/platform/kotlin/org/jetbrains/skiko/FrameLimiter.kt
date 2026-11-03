@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.jetbrains.skiko

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Skiko 0.153.0 的 FrameLimiter（Apache-2.0），保留构造与帧广播接口。
 * 显示器休眠时刷新率为 0，帧间隔会变成 Long.MAX_VALUE；该次等待须回退到 60 Hz。
 */
class FrameLimiter(
    private val coroutineScope: CoroutineScope,
    private val frameMillis: () -> Long,
    private val impreciseDelay: suspend (Long) -> Unit = ::delay,
    private val currentTime: () -> Duration = { System.nanoTime().nanoseconds },
) {
    private val channel = RendezvousBroadcastChannel<Unit>()

    init {
        coroutineScope.launch {
            while (true) {
                channel.sendAll(Unit)
                preciseDelay(frameMillis())
            }
        }
    }

    private suspend fun preciseDelay(millis: Long) {
        val frameDuration = millis.milliseconds.takeUnless { it.isInfinite() } ?: 16.milliseconds
        val start = currentTime()
        var actual1msDelay = 1.milliseconds

        while (currentTime() - start <= frameDuration - actual1msDelay) {
            val beforeDelay = currentTime()
            impreciseDelay(1)
            actual1msDelay = maxOf(actual1msDelay, currentTime() - beforeDelay)
        }
    }

    suspend fun awaitNextFrame() {
        withContext(coroutineScope.coroutineContext) {
            channel.receive()
        }
    }
}

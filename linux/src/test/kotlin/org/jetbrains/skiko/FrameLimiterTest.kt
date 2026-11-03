package org.jetbrains.skiko

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

class FrameLimiterTest {
    @Test
    fun `invalid refresh period does not stall subsequent frames`() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val elapsedMillis = AtomicLong()
        val periodReads = AtomicInteger()
        val limiter = FrameLimiter(
            coroutineScope = scope,
            // 显示器休眠时 1000 / 0.0 转为 Long.MAX_VALUE，唤醒后恢复正常刷新率。
            frameMillis = { if (periodReads.getAndIncrement() == 0) Long.MAX_VALUE else 16L },
            impreciseDelay = { millis ->
                delay(1)
                elapsedMillis.addAndGet(millis)
            },
            currentTime = { elapsedMillis.get().milliseconds },
        )
        val watchdog = launch(Dispatchers.Default) {
            delay(2_000)
            scope.cancel("Frame delivery timed out")
        }
        try {
            repeat(3) { limiter.awaitNextFrame() }
            assertTrue(periodReads.get() >= 2)
            assertTrue(elapsedMillis.get() >= 32)
        } finally {
            watchdog.cancel()
            scope.cancel()
        }
    }

    @Test
    fun `finite frame periods retain their pacing`() = runBlocking {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        val elapsedMillis = AtomicLong()
        val frameStarts = CopyOnWriteArrayList<Long>()
        val limiter = FrameLimiter(
            coroutineScope = scope,
            frameMillis = {
                frameStarts.add(elapsedMillis.get())
                25L
            },
            impreciseDelay = { millis ->
                delay(1)
                elapsedMillis.addAndGet(millis)
            },
            currentTime = { elapsedMillis.get().milliseconds },
        )
        val watchdog = launch(Dispatchers.Default) {
            delay(2_000)
            scope.cancel("Frame delivery timed out")
        }
        try {
            limiter.awaitNextFrame()
            limiter.awaitNextFrame()
            while (frameStarts.size < 2) delay(1)
            assertEquals(25L, frameStarts[1] - frameStarts[0])
        } finally {
            watchdog.cancel()
            scope.cancel()
        }
    }
}

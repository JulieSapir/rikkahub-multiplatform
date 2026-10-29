package android.os

import org.junit.Assert.assertTrue
import org.junit.Test

class SystemClockTest {

    @Test
    fun `elapsedRealtime is monotonic`() {
        val first = SystemClock.elapsedRealtime()
        Thread.sleep(5)
        assertTrue(SystemClock.elapsedRealtime() >= first)
    }
}

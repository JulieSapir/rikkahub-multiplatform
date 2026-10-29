package android.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面端 CI 没有麦克风，AudioRecord 的行为只能测与设备无关的部分：
 * 不支持的编码必须走「line 为空」的降级路径（read 报错误码、start 抛异常），而不是崩溃。
 */
class AudioRecordJvmTest {

    @Test
    fun `getMinBufferSize returns 200ms of bytes for 16k mono pcm16`() {
        assertEquals(6400, AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))
        assertTrue(AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0)
    }

    @Test
    fun `read returns error code when encoding is unsupported`() {
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            999,
            4096,
        )
        assertEquals(-1, recorder.read(ByteArray(1024), 0, 1024))
        assertThrows(IllegalStateException::class.java) { recorder.startRecording() }
        // stop/release 在未打开时也必须安全，provider 的 finally 是 runCatching 包着的，但不能依赖它
        recorder.stop()
        recorder.release()
    }

    @Test
    fun `read validates buffer bounds before touching the line`() {
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            4096,
        )
        assertEquals(-1, recorder.read(ByteArray(16), 0, 1024))
        assertEquals(-1, recorder.read(ByteArray(16), -1, 8))
    }
}

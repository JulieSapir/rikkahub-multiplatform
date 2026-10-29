package me.rerere.rikkahub.utils

import android.content.Context
import me.rerere.rikkahub.linux.res.RikkaRes
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10

/**
 * 音效播放走 mp3spi（JavaSound 的 MP3 解码 SPI）：preload 时把 classpath 里的 raw 资源
 * 解码进 Clip 常驻，play 时回卷重放，音量用 Clip 的 MASTER_GAIN 尽力而为。
 *
 * 找不到资源或拿不到音频设备（无显示器的服务器上很常见）都静默降级成 no-op，
 * 与 Android 端 SoundPool 的行为差异只剩「不会加载失败重试」。
 */
class SoundEffectPlayer(private val context: Context) {

    // 调用点都在 UI 线程（Compose 回调），这里同步只是防 preload/play 竞态，不值得上并发容器
    private val clips = HashMap<Int, Clip?>()

    fun preload(vararg resIds: Int) {
        for (resId in resIds) loadClip(resId)
    }

    fun play(resId: Int, volume: Float = 1f) {
        val clip = loadClip(resId) ?: return
        runCatching {
            clip.framePosition = 0
            applyVolume(clip, volume)
            clip.start()
        }
    }

    fun release() {
        synchronized(clips) {
            clips.values.forEach { clip -> runCatching { clip?.close() } }
            clips.clear()
        }
    }

    /** 解析失败的资源记住 null，play 时直接跳过，不反复尝试解码 */
    private fun loadClip(resId: Int): Clip? = synchronized(clips) {
        if (clips.containsKey(resId)) clips[resId] else createClip(resId).also { clips[resId] = it }
    }

    private fun createClip(resId: Int): Clip? = runCatching {
        val path = RikkaRes.resourcePath(resId) ?: return null
        val bytes = RikkaRes.bytesOf(path) ?: return null
        // mp3spi 解出来的 MPEG 流 frame size 是 NOT_SPECIFIED，Clip.open 直接拒收，
        // 先用 AudioSystem 转成 PCM_SIGNED 再交给 Clip
        val source = AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
        val srcFormat = source.format
        val pcm = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            srcFormat.sampleRate,
            16,
            srcFormat.channels,
            srcFormat.channels * 2,
            srcFormat.sampleRate,
            false,
        )
        val clip = AudioSystem.getClip()
        clip.open(AudioSystem.getAudioInputStream(pcm, source))
        clip
    }.onFailure {
        it.printStackTrace()
    }.getOrNull()

    private fun applyVolume(clip: Clip, volume: Float) {
        runCatching {
            val gain = clip.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl ?: return
            // 线性音量换算成分贝：1f -> 0dB，越小越负；有的混音器 minimum 不到 -60，夹到实际范围
            val db = if (volume <= 0f) MIN_DB else 20.0 * log10(volume.coerceAtMost(1f).toDouble())
            gain.setValue(db.toFloat().coerceIn(gain.minimum, gain.maximum))
        }
    }

    private companion object {
        const val MIN_DB = -60f
    }
}

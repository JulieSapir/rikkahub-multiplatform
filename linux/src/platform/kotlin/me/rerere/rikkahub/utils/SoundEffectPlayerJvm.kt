package me.rerere.rikkahub.utils

import android.content.Context

/**
 * 音效播放走 Android 的 SoundPool（解码 .ogg 靠 MediaCodec），JVM 侧没有对应实现，
 * 桌面端先整体静音：保留 preload/play/release 三个方法，设置页与调用点都不用改。
 */
class SoundEffectPlayer(private val context: Context) {
    fun preload(vararg resIds: Int) = Unit

    fun play(resId: Int, volume: Float = 1f) = Unit

    fun release() = Unit
}

package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.rerere.tts.model.PlaybackState

/**
 * 朗读（TTS）在 Android 端用 TextToSpeech 引擎与 MediaPlayer 播放，桌面端这条链路还没接：
 * 这里给出同样的接口与“不可用”的实现，UI 因此照常渲染。语音输入（ASR）已接通，
 * 由上游 hooks/ASR.kt 提供 rememberCustomAsrState。
 */
interface CustomTtsState {
    val isAvailable: StateFlow<Boolean>

    val isSpeaking: StateFlow<Boolean>

    val error: StateFlow<String?>

    val currentChunk: StateFlow<Int>

    val totalChunks: StateFlow<Int>

    val playbackState: StateFlow<PlaybackState>

    fun speak(text: String, flushCalled: Boolean = true)

    fun stop()

    fun pause()

    fun resume()

    fun skipNext()

    fun fastForward(ms: Long = 5_000)

    fun setSpeed(speed: Float)

    fun cleanup()
}

@Composable
fun rememberCustomTtsState(): CustomTtsState = remember { DisabledTtsState() }

private class DisabledTtsState : CustomTtsState {
    override val isAvailable: StateFlow<Boolean> = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = MutableStateFlow(false)
    override val error: StateFlow<String?> = MutableStateFlow(null)
    override val currentChunk: StateFlow<Int> = MutableStateFlow(0)
    override val totalChunks: StateFlow<Int> = MutableStateFlow(0)
    override val playbackState: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())

    override fun speak(text: String, flushCalled: Boolean) = Unit

    override fun stop() = Unit

    override fun pause() = Unit

    override fun resume() = Unit

    override fun skipNext() = Unit

    override fun fastForward(ms: Long) = Unit

    override fun setSpeed(speed: Float) = Unit

    override fun cleanup() = Unit
}

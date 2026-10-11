package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.rerere.asr.ASRState
import me.rerere.tts.model.PlaybackState

/**
 * 语音输入（ASR）与朗读（TTS）在 Android 端要抢麦克风/音频焦点并用 MediaPlayer 播放，
 * 桌面端这条链路整体没接：这里给出同样的接口与“不可用”的实现，UI 因此照常渲染。
 *
 * 空闲状态只报 isAvailable=false，不预置 errorMessage —— ChatInput 用
 * LaunchedEffect(asrState.errorMessage) 弹提示，常驻的错误信息会让每次重新进入页面都弹一次 toast。
 * 真正点麦克风时才报错。
 *
 * 上游的 ASR.kt / TTS.kt 在 linux/build.gradle.kts 里 exclude。
 */
interface CustomAsrState {
    val state: StateFlow<ASRState>

    fun start(onTranscriptChange: (String) -> Unit)

    fun stop()

    fun cleanup()
}

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
fun rememberCustomAsrState(): CustomAsrState = remember { DisabledAsrState() }

@Composable
fun rememberCustomTtsState(): CustomTtsState = remember { DisabledTtsState() }

private class DisabledAsrState : CustomAsrState {
    private val mutableState = MutableStateFlow(ASRState(isAvailable = false))
    override val state: StateFlow<ASRState> = mutableState

    override fun start(onTranscriptChange: (String) -> Unit) {
        mutableState.value = ASRState(errorMessage = UNSUPPORTED)
    }

    override fun stop() {
        mutableState.value = ASRState(isAvailable = false)
    }

    override fun cleanup() = Unit

    companion object {
        private const val UNSUPPORTED = "桌面端暂不支持语音输入"
    }
}

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

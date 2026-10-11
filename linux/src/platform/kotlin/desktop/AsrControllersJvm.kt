package me.rerere.asr.providers

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import okhttp3.OkHttpClient

/**
 * 语音识别的采集端绑 Android 的 AudioRecord（16k PCM 推流到各家实时接口），桌面端没接：
 * 这里给同样的类与接口，空闲时只报 isAvailable=false（语音模式页因此仍能渲染、不弹错误），
 * 真的点录音才报“本端不可用”。
 *
 * MPTODO
 */
private class UnsupportedAsrController(private val message: String) : ASRController {
    private val mutableState = MutableStateFlow(ASRState(isAvailable = false))
    override val state: StateFlow<ASRState> = mutableState

    override fun start(onTranscriptChange: (String) -> Unit) {
        mutableState.value = ASRState(errorMessage = message)
    }

    override fun stop() {
        mutableState.value = ASRState(isAvailable = false)
    }

    override fun dispose() = Unit
}

private const val UNSUPPORTED = "桌面端暂不支持语音输入"

class OpenAIRealtimeASRController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ASRProviderSetting.OpenAIRealtime,
) : ASRController by UnsupportedAsrController(UNSUPPORTED)

class DashScopeASRController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ASRProviderSetting.DashScope,
) : ASRController by UnsupportedAsrController(UNSUPPORTED)

class VolcengineASRController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ASRProviderSetting.Volcengine,
) : ASRController by UnsupportedAsrController(UNSUPPORTED)

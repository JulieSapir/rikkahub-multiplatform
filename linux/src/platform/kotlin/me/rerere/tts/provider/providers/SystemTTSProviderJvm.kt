package me.rerere.tts.provider.providers

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting

/**
 * Android 的 SystemTTS 走设备内置引擎，桌面端没有对应服务：保留类型让 TTSManager 原样编译，
 * 选中它时给出明确报错，其它云端 provider（OpenAI/Gemini/…）都是纯 HTTP，直接复用上游实现。
 */
class SystemTTSProvider : TTSProvider<TTSProviderSetting.SystemTTS> {
    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest,
    ): Flow<AudioChunk> = flow {
        error("桌面端没有系统 TTS 引擎，请改用云端 TTS 提供商")
    }
}

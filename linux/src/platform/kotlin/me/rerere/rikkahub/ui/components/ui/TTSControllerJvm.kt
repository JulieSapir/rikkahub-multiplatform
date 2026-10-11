package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.Composable

/**
 * Android 端的 TTSController 是全局悬浮的朗读控制条（FloatingWindow + media3 播放）。
 * 桌面端朗读链路没接（见 ui/hooks/SpeechStateJvm.kt，isSpeaking 恒为 false），
 * 这里保留同名 composable 但不渲染任何东西，RouteActivity 的调用点不用改。
 */
@Composable
fun TTSController() = Unit

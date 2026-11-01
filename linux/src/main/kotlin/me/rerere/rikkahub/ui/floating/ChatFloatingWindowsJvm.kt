package me.rerere.rikkahub.ui.floating

import android.app.Application
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.ChatFloatingTracker

/**
 * Android 端的聊天气泡/悬浮面板用 FloatingX 在系统窗口层画（原文件在 build.gradle.kts 里 exclude）。
 * 桌面端没有系统悬浮窗这层概念：这里保持 koin 单例的构造签名不变（AppModule 会注入它），
 * 但不观察任何状态、不渲染任何窗口；桌面端的等价能力（置顶小窗）留待后续实现。
 */
@Suppress("unused", "UNUSED_PARAMETER")
class ChatFloatingWindows(
    context: Application,
    appScope: AppScope,
    tracker: ChatFloatingTracker,
    lazyChatService: Lazy<ChatService>,
)

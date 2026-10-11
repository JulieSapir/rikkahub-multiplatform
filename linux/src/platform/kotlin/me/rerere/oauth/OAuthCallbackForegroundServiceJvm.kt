package me.rerere.oauth

import android.content.Context

/**
 * Android 需要前台服务才能在息屏时守着 OAuth 回调端口，桌面端进程本来就一直活着，
 * 所以这里只保留 OAuthLoopbackCallbackServer 调到的两个方法，并总是报告“已就绪”。
 */
object OAuthCallbackForegroundService {
    internal suspend fun acquire(context: Context, sessionId: String): Boolean = true

    internal fun release(context: Context, sessionId: String) = Unit
}

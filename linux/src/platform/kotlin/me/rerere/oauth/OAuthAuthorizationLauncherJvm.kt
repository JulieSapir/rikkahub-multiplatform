package me.rerere.oauth

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.awt.Desktop
import java.net.URI

/**
 * Android 端用 Custom Tabs 在原进程里打开授权页，桌面端交给系统默认浏览器：
 * 回调仍由同模块的 OAuthLoopbackCallbackServer 在本机端口上收，流程不变。
 *
 * 上游那份 OAuthAuthorizationLauncher.kt 里的 fun interface 也在这里一起声明，
 * 因为整个文件被 exclude 了。
 */
fun interface OAuthAuthorizationLauncher {
    fun launch(context: Context, authorizationUrl: String)
}

object CustomTabsOAuthAuthorizationLauncher : OAuthAuthorizationLauncher {
    override fun launch(context: Context, authorizationUrl: String) {
        val opened = runCatching { Desktop.getDesktop().browse(URI(authorizationUrl)) }.isSuccess
        if (!opened) context.startActivity(Intent(Intent.ACTION_VIEW, authorizationUrl.toUri()))
    }
}

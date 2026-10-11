package me.rerere.rikkahub.linux

import androidx.activity.compose.DesktopActivityContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.linux.desktop.DesktopBootstrap
import android.app.Application
import me.rerere.rikkahub.linux.di.desktopPlatformModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * 桌面端入口。
 *
 * 界面不重写：上游 RouteActivity 里带着整棵导航图（AppRoutes）、设置注入与 Coil 配置，
 * 所以这里把它的 onCreate 跑一遍，再把 setContent 记录下来的 composable 放进窗口渲染。
 * 依赖图也直接复用上游 di 包的四个 module，桌面端只补 Context/Application 这两个平台单例。
 */
fun main() {
    // Application 与 Activity 在桌面端都只是 Context 的具名子类，目录一致；
    // 上游 ChatService 等的构造签名要 Application，所以注册的是前者。
    val appContext = Application()
    val activity = RouteActivity()
    DesktopBootstrap.prepareBeforeKoin(appContext)
    startKoin {
        modules(desktopPlatformModule(appContext) + DesktopBootstrap.upstreamModules)
    }
    try {
        activity.onCreate(null)
        DesktopBootstrap.afterKoin(appContext)
        application { DesktopWindow(activity) }
    } finally {
        stopKoin()
    }
}

@Composable
private fun ApplicationScope.DesktopWindow(activity: RouteActivity) {
    val content = remember(activity) { DesktopActivityContent.contentOf(activity) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "RikkaHub",
        state = rememberWindowState(width = 1280.dp, height = 860.dp),
    ) {
        content?.let {
            CompositionLocalProvider(LocalContext provides activity) { it() }
        }
    }
}

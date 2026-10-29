package android.app

import android.content.Context
import me.rerere.rikkahub.linux.state.AppPaths
import java.io.File

/**
 * 桌面端的 Application 只是 Context 的具名子类：上游 ChatService / ChatNotificationManager 的
 * 构造签名要求 Application，而 Koin 里注册的是同一个实例。
 *
 * 默认路径与 Context 保持一致（都来自 AppPaths），避免无参构造时用户数据落进 /tmp。
 */
open class Application(
    dataDir: File = AppPaths.dataDir,
    cacheDir: File = AppPaths.cacheDir,
) : Context(dataDir, cacheDir)

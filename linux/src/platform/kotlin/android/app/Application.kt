package android.app

import android.content.Context
import java.io.File

/**
 * 桌面端的 Application 只是 Context 的具名子类：上游 ChatService / ChatNotificationManager 的
 * 构造签名要求 Application，而 Koin 里注册的是同一个实例。
 */
open class Application(
    dataDir: File = File(System.getProperty("java.io.tmpdir"), "rikka-linux-data"),
    cacheDir: File = File(dataDir.parentFile, "cache"),
) : Context(dataDir, cacheDir)

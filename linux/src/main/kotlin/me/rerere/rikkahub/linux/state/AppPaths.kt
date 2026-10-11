package me.rerere.rikkahub.linux.state

import java.io.File

/**
 * Linux 上的数据与缓存目录，遵循 XDG 约定。
 *
 * 与 Android 端不同，这里没有沙箱，会话与配置都是普通文件，方便直接备份和迁移。
 */
object AppPaths {
    val dataDir: File = xdg("XDG_DATA_HOME", ".local/share").resolve("rikka-linux")
    val cacheDir: File = xdg("XDG_CACHE_HOME", ".cache").resolve("rikka-linux")

    init {
        dataDir.mkdirs()
        cacheDir.mkdirs()
    }

    private fun xdg(envKey: String, fallbackRelative: String): File {
        val env = System.getenv(envKey)?.takeIf { it.startsWith("/") }
        if (env != null) return File(env)
        return File(System.getProperty("user.home")).resolve(fallbackRelative)
    }
}

package android.os

/**
 * 上游代码用 Build.VERSION.SDK_INT 做 API 级别分支，桌面端没有这套概念：给一个足够新的常量，
 * 让所有 "SDK_INT >= N" 的分支都走新实现那条路。
 *
 * BRAND / MODEL / VERSION.RELEASE 会被 PlaceholderTransformer 渲染进 {device_info}、
 * {system_version} 这类提示词占位符，所以这里读真实的主机与系统信息，而不是写死 Android。
 */
object Build {
    private val osName: String = System.getProperty("os.name").orEmpty()
    private val osVersion: String = System.getProperty("os.version").orEmpty()

    val BRAND: String = detectBrand()
    val MANUFACTURER: String = BRAND
    val MODEL: String = System.getProperty("os.arch").orEmpty()
    val DEVICE: String = System.getProperty("user.name").orEmpty()
    val FINGERPRINT: String = "$BRAND/$MODEL/$osVersion"

    object VERSION {
        const val SDK_INT = 36
        val RELEASE: String = System.getProperty("os.version").orEmpty().ifBlank { "desktop" }
        val CODENAME: String = "release"
        val INCREMENTAL: String = RELEASE
    }

    object VERSION_CODES {
        const val LOLLIPOP = 21
        const val O_MR1 = 27
        const val P = 28
        const val Q = 29
        const val S = 31
        const val R = 30
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
        const val VANILLA_ICE_CREAM = 35
    }

    private fun detectBrand(): String = listOf("/etc/os-release", "/usr/lib/os-release")
        .map { java.io.File(it) }
        .filter { it.isFile }
        .firstNotNullOfOrNull { readPrettyName(it) }
        ?.takeIf { it.isNotBlank() }
        ?: osName.ifBlank { "desktop" }

    private fun readPrettyName(file: java.io.File): String? =
        file.readLines().firstOrNull { it.startsWith("PRETTY_NAME=") }
            ?.removePrefix("PRETTY_NAME=")?.trim('"')
}

package android.content.pm

import java.io.File

/**
 * 上游用 packageManager.getPackageInfo(...).lastUpdateTime 作为内置技能包的缓存戳。
 * 桌面端用数据目录的创建时间当这个戳，语义等价（同一份安装对应同一个戳）。
 */
class PackageInfo(val lastUpdateTime: Long)

/** 上游只读 nativeLibraryDir（PRoot 的 so 目录），桌面端指向数据目录里的 lib/。 */
class ApplicationInfo(val nativeLibraryDir: String) {
    val sourceDir: String = nativeLibraryDir
    val dataDir: String = File(nativeLibraryDir).parent.orEmpty()
    val packageName: String = "me.rerere.rikkahub"
}

class PackageManager(private val dataDir: File) {

    fun getPackageInfo(packageName: String, flags: Int): PackageInfo =
        PackageInfo(lastUpdateTime = dataDir.creationTimeOrNow())

    /** 桌面端没有“安装来源”概念，返回 null 表示未知，上游据此不走 Play Store 分支。 */
    fun getInstallSourceInfo(packageName: String): InstallSourceInfo = InstallSourceInfo()

    fun getInstallerPackageName(packageName: String): String? = null

    class InstallSourceInfo(val installingPackageName: String? = null)

    companion object {
        const val PERMISSION_GRANTED = 0
        const val PERMISSION_DENIED = -1
    }
}

private fun File.creationTimeOrNow(): Long = runCatching {
    java.nio.file.Files.getAttribute(toPath(), "creationTime").toString().removeSuffix(".0").toLong()
}.getOrDefault(System.currentTimeMillis())

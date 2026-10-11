package android.content

import android.app.desktopNotificationManager
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.BatteryManager
import me.rerere.rikkahub.linux.service.DesktopServiceRouter
import me.rerere.rikkahub.linux.res.RikkaRes
import me.rerere.rikkahub.linux.state.AppPaths
import java.awt.Desktop
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.database.Cursor
import android.database.MatrixCursor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale

/**
 * android.content.Context 的桌面端替身。
 *
 * 只实现上游 UI 真正用到的那部分：字符串资源、缓存目录、以及把链接交给系统浏览器。SAF
 * （content:// 读写）、通知、权限这些在桌面端没有对应概念，用到它们的功能整体在
 * linux/build.gradle.kts 里排除，而不是在这里塞假实现。
 */
open class Context(
    val dataDir: File = AppPaths.dataDir,
    val cacheDir: File = AppPaths.cacheDir,
    val filesDir: File = File(dataDir, "files").apply { mkdirs() },
    val packageName: String = "me.rerere.rikkahub",
) {
    val applicationContext: Context get() = this

    val contentResolver: ContentResolver = ContentResolver()

    /** assets 在桌面端是 jar 里的 /assets（由资源桥接任务拷进来） */
    val assets: AssetManager = AssetManager(File(dataDir, "assets").apply { mkdirs() })

    val packageManager: PackageManager = PackageManager(dataDir)

    /** 上游只从它取 nativeLibraryDir（PRoot 的 so 目录），桌面端指向数据目录下的 lib/ */
    val applicationInfo: ApplicationInfo = ApplicationInfo(File(dataDir, "lib").apply { mkdirs() }.absolutePath)

    /** OAuth 回调页按 resources.configuration.locales 决定 html 的 lang 属性 */
    val resources: Resources = Resources(this)

    fun getSharedPreferences(name: String, mode: Int = MODE_PRIVATE): SharedPreferences =
        contextSharedPreferences(this, name)

    /** Android 把它放在 /data/data/<pkg>/no_backup，桌面端等价于 dataDir 下的一个子目录 */
    val noBackupFilesDir: File = File(dataDir, "no_backup").apply { mkdirs() }

    /** 与 AppDatabaseFactory 建库的位置保持一致：<dataDir>/database/<name> */
    fun getDatabasePath(name: String): File = File(dataDir, "database/$name")

    fun getString(id: Int): String = RikkaRes.string(id, Locale.getDefault())

    fun getString(id: Int, vararg args: Any?): String {
        val raw = RikkaRes.string(id, Locale.getDefault())
        return if (args.isEmpty()) raw else String.format(Locale.getDefault(), raw, *args)
    }

    fun getQuantityString(id: Int, quantity: Int, vararg args: Any?): String = getString(id, *args)

    open fun startActivity(intent: Intent) {
        val target = intent.data?.toString() ?: return
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            System.err.println("桌面端不支持打开: $target")
            return
        }
        runCatching { Desktop.getDesktop().browse(java.net.URI(target)) }
            .onFailure { System.err.println("打开浏览器失败: ${it.message}") }
    }

    /**
     * 桌面端没有前台服务：Android 用 Service 承载的后台任务（内嵌 Web 服务）在这里直接转成
     * 管理器调用，见 service/WebServerServiceJvm.kt；其余 Intent 丢弃。
     */
    fun startService(intent: Intent): Unit = DesktopServiceRouter.route(this, intent)

    fun startForegroundService(intent: Intent): Unit = DesktopServiceRouter.route(this, intent)

    fun stopService(intent: Intent): Unit = Unit

    fun bindService(intent: Intent, connection: Any?, flags: Int): Boolean = false

    fun unbindService(connection: Any?) = Unit

    /**
     * 桌面端只有系统通知与电量这两个“系统服务”：前者落到托盘气泡，后者固定 100%，
     * 都是为了让上游那几处 getSystemService 的写法不用改。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getSystemService(name: String): T = when (name) {
        NOTIFICATION_SERVICE -> desktopNotificationManager
        CLIPBOARD_SERVICE -> ClipboardManager.shared
        INPUT_METHOD_SERVICE -> android.view.inputmethod.InputMethodManager.shared
        BATTERY_SERVICE -> BatteryManager(this)
        // 下载服务桌面端不存在：这里返回 null，让调用处的 as DownloadManager 抛异常，
        // 从而落到 UpdateChecker 已有的“打开下载页”分支
        else -> null
    } as T

    fun checkSelfPermission(permission: String): Int = PERMISSION_DENIED

    fun getColor(color: Int): Int = color

    companion object {
        const val CLIPBOARD_SERVICE = "clipboard"
        const val NOTIFICATION_SERVICE = "notification"
        const val BATTERY_SERVICE = "batteryinfo"
        const val WIFI_SERVICE = "wifi"
        const val DOWNLOAD_SERVICE = "download"
        const val VIBRATOR_SERVICE = "vibrator"
        const val POWER_SERVICE = "power"
        const val AUDIO_SERVICE = "audio"
        const val INPUT_METHOD_SERVICE = "inputmethod"
        const val CONNECTIVITY_SERVICE = "connectivity"
        const val MODE_PRIVATE = 0
        const val PERMISSION_DENIED = -1
        const val PERMISSION_GRANTED = 0
    }
}

/**
 * 桌面端只支持 file:// 读写；content:// 属于 Android 的 SAF，相关功能在桌面端不启用。
 */
class ContentResolver {
    fun openInputStream(uri: Uri) = fileOf(uri).inputStream()

    fun openOutputStream(uri: Uri, mode: String = "w"): OutputStream =
        if (mode.contains("a")) FileOutputStream(fileOf(uri), true) else FileOutputStream(fileOf(uri))

    fun getType(uri: Uri): String? =
        android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileOf(uri).extension)

    /**
     * 桌面端“媒体库”就是用户主目录下的 Pictures/Movies：insert 按 MediaStore 的列名分配一个
     * file:// 位置，delete 删掉它，上游保存创作结果到相册的那段流程因此原样可用。
     */
    fun insert(uri: Uri, values: ContentValues?): Uri? {
        val name = values?.get(android.provider.MediaStore.MediaColumns.DISPLAY_NAME) as? String
            ?: "rikka-hub-${System.currentTimeMillis()}"
        val relative = values?.get(android.provider.MediaStore.MediaColumns.RELATIVE_PATH) as? String
        val bucket = if (uri.toString().contains("Movies")) {
            android.os.Environment.DIRECTORY_MOVIES
        } else {
            android.os.Environment.DIRECTORY_PICTURES
        }
        val dir = File(
            android.os.Environment.getExternalStoragePublicDirectory(bucket),
            relative?.trim('/').orEmpty(),
        )
        if (!dir.isDirectory && !dir.mkdirs()) return null
        return Uri.fromFile(File(dir, name.substringBeforeLast('/')))
    }

    fun delete(
        uri: Uri,
        selection: String? = null,
        selectionArgs: Array<out String>? = null,
    ): Int = runCatching { if (fileOf(uri).delete()) 1 else 0 }.getOrDefault(0)

    fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String? = null,
        selectionArgs: Array<out String>? = null,
    ): Int = 0

    /**
     * 上游只用它查文件名（DISPLAY_NAME）与大小，桌面端直接由真实文件构造一行结果。
     */
    fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String? = null,
        selectionArgs: Array<out String>? = null,
        sortOrder: String? = null,
    ): Cursor {
        val file = fileOf(uri)
        val columns = projection?.toList() ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(
                columns.map { column ->
                    when (column) {
                        OpenableColumns.DISPLAY_NAME -> file.name
                        OpenableColumns.SIZE -> file.length()
                        else -> null
                    } as Any?
                }.toTypedArray(),
            )
        }
    }

    private fun fileOf(uri: Uri): File {
        check(uri.isFile) { "桌面端不支持 content:// 资源: $uri" }
        return uri.toFile()
    }
}

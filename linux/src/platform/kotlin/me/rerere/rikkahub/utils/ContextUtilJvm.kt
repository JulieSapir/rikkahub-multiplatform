package me.rerere.rikkahub.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.io.File

/**
 * utils/ContextUtil.kt 里只有这一小部分是桌面端需要的（Theme.kt 用它拿 Activity 设置系统栏）。
 *
 * 原文件其余内容依赖 MediaStore / SAF / 通知，在 linux/build.gradle.kts 里整体 exclude 掉了。
 */
fun Context.getActivity(): Activity? = this as? Activity

/** 用系统浏览器打开链接 */
fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
}

fun Context.writeStringPreference(key: String, value: String?) = Unit

fun Context.readBooleanPreference(key: String, default: Boolean): Boolean = default

/** 弹保存对话框写文本，桌面端替代 Android 的 ShareSheet / SAF 创建文件 */
fun Context.saveTextFile(content: String, suggestedName: String) {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "保存", java.awt.FileDialog.SAVE).apply {
        file = suggestedName
        directory = filesDir.absolutePath
    }
    // AWT 组件只能在 EDT 上显示；模态对话框在 EDT 里自旋到关闭，invokeAndWait 随之返回
    try {
        javax.swing.SwingUtilities.invokeAndWait { dialog.isVisible = true }
    } catch (e: java.lang.reflect.InvocationTargetException) {
        throw e.cause ?: e
    }
    val name = dialog.file ?: return
    File(dialog.directory ?: filesDir.absolutePath, name).writeText(content, Charsets.UTF_8)
}

/**
 * Android 上这两个函数把图片导出到系统相册（MediaStore + SAF），桌面端落到数据目录的 exports/ 下，
 * 并把实际路径打出来：签名保持一致，调用处不用改。
 */
fun Context.exportImage(
    activity: Activity,
    bitmap: android.graphics.Bitmap,
    fileName: String = "RikkaHub_${System.currentTimeMillis()}.png",
) {
    val file = File(filesDir, "exports/$fileName")
    file.parentFile?.mkdirs()
    file.outputStream().use { out ->
        bitmap.compress(
            if (fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true)) {
                android.graphics.Bitmap.CompressFormat.JPEG
            } else {
                android.graphics.Bitmap.CompressFormat.PNG
            },
            95,
            out,
        )
    }
    println("已导出图片: ${file.absolutePath}")
}

fun Context.exportImageFile(
    activity: Activity,
    file: File,
    fileName: String = "RikkaHub_${System.currentTimeMillis()}.png",
) {
    val target = File(File(filesDir, "exports"), fileName).apply { parentFile?.mkdirs() }
    file.copyTo(target, overwrite = true)
    println("已导出文件: ${target.absolutePath}")
}

/** Android 跳到“使用情况访问权限”设置页，桌面端没有这个面板，只提示一下。 */
fun Context.openUsageAccessSettings() {
    println("桌面端没有系统用量统计，屏幕时间相关能力不可用")
}

/** 写系统剪贴板 */
fun Context.writeClipboardText(text: String) {
    runCatching {
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
            java.awt.datatransfer.StringSelection(text), null
        )
    }
}

/** 读系统剪贴板；剪贴板被其它程序占着不可用时返回空串，和 Android 端无选区时的行为一致。 */
fun Context.readClipboardText(): String = runCatching {
    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
    val flavor = java.awt.datatransfer.DataFlavor.stringFlavor
    if (clipboard.isDataFlavorAvailable(flavor)) clipboard.getData(flavor) as? String else null
}.getOrNull().orEmpty()

/** Android 上呼起手Q加群，桌面端就是在浏览器里打开那个链接。 */
fun Context.joinQQGroup(key: String?): Boolean {
    if (key.isNullOrBlank()) return false
    val url = "http://qm.qq.com/cgi-bin/qm/qr?from=app&jump_from=webapi&k=$key"
    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    return true
}

/** 桌面端没有“使用情况访问权限”这套系统设置页。 */
fun Context.hasUsageStatsPermission(): Boolean = false

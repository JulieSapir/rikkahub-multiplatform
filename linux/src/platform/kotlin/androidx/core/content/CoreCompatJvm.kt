package androidx.core.content

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri

/**
 * androidx.core 的几个静态工具在桌面端的等价实现。
 * 权限检查与 ActivityCompat 用同一套判定（见 ActivityCompatJvm.kt），保持一致。
 */
object ContextCompat {
    fun checkSelfPermission(context: Context, permission: String): Int =
        androidx.core.app.ActivityCompat.checkSelfPermission(context, permission)

    fun getColor(context: Context, colorRes: Int): Int = context.getColor(colorRes)

    fun getDrawable(context: Context, drawableRes: Int): Drawable? = null

    fun getSystemService(context: Context, name: String): Any? = context.getSystemService(name)

    /** 桌面端没有前台服务，直接丢弃 Intent。 */
    fun startForegroundService(context: Context, service: Intent) = Unit

    fun registerReceiver(context: Context, receiver: Any?, filter: Any?): Uri? = null

    fun getColorStateList(context: Context, colorRes: Int): Any? = null
}

/**
 * FileProvider 把 cacheDir 里的文件换成 content:// 授权 URI；桌面端 ContentResolver
 * 认 file://，所以直接返回文件 URI，分享/打开图片的那几条链路因此仍然可用。
 */
object FileProvider {
    fun getUriForFile(context: Context, authority: String, file: java.io.File): Uri = Uri.fromFile(file)

    class Paths

    object StrictMode {
        fun unlockContentsAndTempStorage(path: String, token: Any?) = Unit

        fun renewContentsAndTempStorage(path: String, token: Any?) = Unit
    }
}

/** 上游只用于加载 assets 里的字体；桌面端字体由 Compose 直接读文件，这里返回一个非空占位。 */
object ResourcesCompat {
    fun getFont(context: Context, fontResId: Int): Typeface? = Typeface.DEFAULT

    fun getDrawable(context: Context, drawableResId: Int, theme: Any?): Drawable? = null
}

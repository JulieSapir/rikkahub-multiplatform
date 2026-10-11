package androidx.core.app

import android.content.Context
import android.content.pm.PackageManager
import java.awt.SystemTray

/**
 * 桌面端没有运行时权限弹窗。上游用 ActivityCompat.checkSelfPermission 判断“能不能发通知”，
 * 这里翻译成“系统托盘可用不可用”，语义上等价：不可用时通知走日志，不打扰用户。
 */
object ActivityCompat {
    fun checkSelfPermission(context: Context, permission: String): Int {
        val granted = !java.awt.GraphicsEnvironment.isHeadless() && SystemTray.isSupported()
        return if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    }

    fun requestPermissions(activity: Context, permissions: Array<out String>, requestCode: Int) = Unit

    fun shouldShowRequestPermissionRationale(activity: Context, permission: String): Boolean = false

    fun requestPermissions(activity: Context, permissions: Array<out String>, requestCode: Int, reason: String?) = Unit
}

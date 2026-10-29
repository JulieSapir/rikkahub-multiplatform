package androidx.core.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import java.awt.SystemTray

/**
 * 桌面端没有运行时权限弹窗。POST_NOTIFICATIONS 翻译成“系统托盘可用不可用”——不可用时通知走
 * 日志，不打扰用户；RECORD_AUDIO 与其余权限一律授予：录音没有系统级的门（AudioRecord 打不开
 * 麦克风时由 provider 自己报错），其它权限在桌面端也没有对应的授权概念。
 */
object ActivityCompat {
    fun checkSelfPermission(context: Context, permission: String): Int = when (permission) {
        Manifest.permission.POST_NOTIFICATIONS -> {
            val granted = !java.awt.GraphicsEnvironment.isHeadless() && SystemTray.isSupported()
            if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        }

        else -> PackageManager.PERMISSION_GRANTED
    }

    fun requestPermissions(activity: Context, permissions: Array<out String>, requestCode: Int) = Unit

    fun shouldShowRequestPermissionRationale(activity: Context, permission: String): Boolean = false

    fun requestPermissions(activity: Context, permissions: Array<out String>, requestCode: Int, reason: String?) = Unit
}

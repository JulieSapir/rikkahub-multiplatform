package android

/** 上游只用这一两个权限名做检查，桌面端由 ActivityCompat 翻译成“系统托盘可用不可用”。 */
object Manifest {
    object permission {
        const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
        const val READ_CALENDAR = "android.permission.READ_CALENDAR"
        const val WRITE_CALENDAR = "android.permission.WRITE_CALENDAR"
        const val INTERNET = "android.permission.INTERNET"
        const val CAMERA = "android.permission.CAMERA"
        const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        const val WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE"
        const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        const val PACKAGE_USAGE_STATS = "android.permission.PACKAGE_USAGE_STATS"
    }
}

package android.app

/**
 * 上游 KeepScreenOn 通过 window.addFlags(FLAG_KEEP_SCREEN_ON) 防止屏幕熄灭，
 * 桌面端没有“常亮”概念，这里保留方法签名不做任何事。
 */
class Window {
    fun addFlags(flags: Int) = Unit

    fun clearFlags(flags: Int) = Unit

    var isNavigationBarContrastEnforced: Boolean = false

    fun setStatusBarColor(color: Int) = Unit

    fun setNavigationBarColor(color: Int) = Unit
}

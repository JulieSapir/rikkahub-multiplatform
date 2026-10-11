package android.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent

/**
 * 桌面端没有 Activity/Window 这层：上游 Theme.kt 用它设置状态栏颜色，拿不到时自己会跳过。
 *
 * 但 RouteActivity 带着整棵导航图（AppRoutes）与 Intent 分发，桌面端要复用它，
 * 所以这里补一套最小生命周期。onCreate 里 setContent 记录的界面由桌面窗口取出来渲染
 * （见 androidx/activity/ActivityEntryJvm.kt）。
 */
open class Activity : Context() {
    val window: Window = Window()

    private var currentIntent: Intent = Intent()

    open val intent: Intent get() = currentIntent

    fun setIntent(value: Intent) {
        currentIntent = value
    }

    open fun onCreate(savedInstanceState: Bundle?) = Unit

    open fun onNewIntent(intent: Intent) = Unit

    open fun onStart() = Unit

    open fun onResume() = Unit

    open fun onPause() = Unit

    open fun onStop() = Unit

    open fun onDestroy() = Unit

    fun finish() = Unit

    open fun dispatchKeyEvent(event: KeyEvent): Boolean = false

    open fun onBackPressed() = Unit

    companion object {
        const val RESULT_OK = -1
        const val RESULT_CANCELED = 0
        const val RESULT_FIRST_USER = 1
    }
}

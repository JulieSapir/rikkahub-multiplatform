package android.view.inputmethod

import android.view.View

/**
 * 软键盘控制在桌面端不存在：Compose 的 TextField 自带焦点与输入法行为，
 * 这里保留方法签名，行为交给 Compose。
 */
class InputMethodManager {
    fun showSoftInput(view: View?, flags: Int): Boolean = view?.requestFocus() ?: false

    fun hideSoftInputFromWindow(windowToken: Any?, flags: Int): Boolean = false

    fun restartInput(view: View?) = Unit

    companion object {
        const val SHOW_IMPLICIT = 1
        const val SHOW_FORCED = 2
        const val HIDE_IMPLICIT_ONLY = 1
        const val NOT_VISIBLE = 0

        internal val shared = InputMethodManager()
    }
}

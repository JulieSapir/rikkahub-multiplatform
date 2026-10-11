package android.view

import android.content.Context

/**
 * 桌面端没有 View；上游只在状态栏着色和“是否编辑期预览”判断里用到，固定返回非编辑期即可。
 */
open class View(open val context: Context = Context()) {
    open val isInEditMode: Boolean get() = false

    /** 终端页请求焦点时会调这两个方法，桌面端交给 Compose 处理。 */
    var isFocusable: Boolean = true
    var isFocusableInTouchMode: Boolean = true

    fun requestFocus(): Boolean = true

    fun post(action: Runnable): Boolean {
        action.run()
        return true
    }

    fun invalidate() = Unit

    /** 上游在录音页用它保持屏幕常亮，桌面端没有熄屏概念。 */
    var keepScreenOn: Boolean = false
}

open class ViewGroup : View()

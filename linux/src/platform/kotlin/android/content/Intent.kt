package android.content

import android.net.Uri

/**
 * android.content.Intent 的最小替代：桌面端只关心动作和携带的数据，不需要组件解析。
 *
 * ACTION_VIEW 带 http(s) 链接时由 Context.startActivity 交给系统浏览器打开。
 */
class Intent(var action: String? = null, uri: Uri? = null) {

    /** 上游用 Intent(context, SomeActivity::class.java) 指定跳转目标，桌面端只记下类名。 */
    constructor(context: Context, target: Class<*>) : this(action = null) {
        component = target.name
    }

    var data: Uri? = uri
    var type: String? = null
    var flags: Int = 0
    var component: String? = null
    private val extras = mutableMapOf<String, Any?>()

    fun putExtra(name: String, value: Any?): Intent = apply { extras[name] = value }

    fun putExtras(values: Map<String, Any?>): Intent = apply { extras.putAll(values) }

    fun getStringExtra(name: String): String? = extras[name] as? String

    fun getCharSequenceExtra(name: String): CharSequence? = extras[name] as? CharSequence

    fun getIntExtra(name: String, default: Int): Int = (extras[name] as? Number)?.toInt() ?: default

    fun getBooleanExtra(name: String, default: Boolean): Boolean = extras[name] as? Boolean ?: default

    fun <T> getExtra(name: String): T? = extras[name] as? T

    fun addFlags(value: Int): Intent = apply { flags = flags or value }

    fun setDataAndType(uri: Uri, type: String?): Intent = apply {
        data = uri
        this.type = type
    }

    fun putExtra(name: String, value: Int): Intent = apply { extras[name] = value }

    fun putExtra(name: String, value: Boolean): Intent = apply { extras[name] = value }

    fun putExtra(name: String, value: Long): Intent = apply { extras[name] = value }

    fun setClassName(packageName: String, className: String): Intent = apply {
        component = "$packageName/$className"
    }

    fun hasExtra(name: String): Boolean = extras.containsKey(name)

    companion object {
        /** 桌面端没有系统选择器，直接把原 Intent 交回 Context.startActivity。 */
        fun createChooser(target: Intent, title: CharSequence?): Intent = target

        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_SENDTO = "android.intent.action.SENDTO"
        const val ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT"
        const val ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT"
        const val ACTION_CREATE_DOCUMENT = "android.intent.action.CREATE_DOCUMENT"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val EXTRA_PROCESS_TEXT = "android.intent.extra.PROCESS_TEXT"
        const val ACTION_PROCESS_TEXT = "android.intent.action.PROCESS_TEXT"
        const val FLAG_IMMUTABLE = 67108864
        const val FLAG_UPDATE_CURRENT = 134217728
        const val FLAG_GRANT_READ_URI_PERMISSION = 1
        const val FLAG_ACTIVITY_NEW_TASK = 268435456
        const val FLAG_ACTIVITY_SINGLE_TOP = 536870912
        const val FLAG_ACTIVITY_CLEAR_TOP = 67108864
    }
}

package android.util

/**
 * ai 模块只用到 Log.d/i/w/e 这几种签名，这里给出最小等价实现，统一输出到 stderr。
 *
 * 保持与 Android 相同的包名和类名，ai 模块源码就能原样编译，桌面端不必改动上游文件。
 */
object Log {
    fun d(tag: String, msg: String?): Int = print("D", tag, msg, null)

    fun d(tag: String, msg: String?, tr: Throwable?): Int = print("D", tag, msg, tr)

    fun i(tag: String, msg: String?): Int = print("I", tag, msg, null)

    fun i(tag: String, msg: String?, tr: Throwable?): Int = print("I", tag, msg, tr)

    fun w(tag: String, msg: String?): Int = print("W", tag, msg, null)

    fun w(tag: String, msg: String?, tr: Throwable?): Int = print("W", tag, msg, tr)

    fun w(tag: String, tr: Throwable?): Int = print("W", tag, "", tr)

    fun e(tag: String, msg: String?): Int = print("E", tag, msg, null)

    fun e(tag: String, msg: String?, tr: Throwable?): Int = print("E", tag, msg, tr)

    fun v(tag: String, msg: String?): Int = print("V", tag, msg, null)

    fun v(tag: String, msg: String?, tr: Throwable?): Int = print("V", tag, msg, tr)

    fun e(tag: String, tr: Throwable?): Int = print("E", tag, "", tr)

    private fun print(level: String, tag: String, msg: String?, tr: Throwable?): Int {
        System.err.println("[$level/$tag] $msg")
        tr?.printStackTrace()
        return msg?.length ?: 0
    }
}

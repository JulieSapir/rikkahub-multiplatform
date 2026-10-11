package android.content

/** Android 在没有应用能处理 Intent 时抛这个；桌面端 startActivity 自己降级，类型只是被引用到。 */
class ActivityNotFoundException(message: String? = null) : Exception(message)

package coil3.request

/**
 * coil3 的 Android 源集里有几个 ImageRequest.Builder 扩展（硬件位图、SAF 访问开关），
 * JVM 产物没有这些概念。补同名扩展并忽略参数，上游的 import 与链式调用保持原样。
 */
fun ImageRequest.Builder.allowHardware(enabled: Boolean): ImageRequest.Builder = this

fun ImageRequest.Builder.allowSharedMemoryCategory(enabled: Boolean): ImageRequest.Builder = this

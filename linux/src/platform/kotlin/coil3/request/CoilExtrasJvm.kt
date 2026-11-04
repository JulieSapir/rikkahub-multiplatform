package coil3.request

/**
 * 硬件位图开关属于 Coil 的 Android 源集，桌面端忽略该参数，保留上游的链式调用。
 */
fun ImageRequest.Builder.allowHardware(enabled: Boolean): ImageRequest.Builder = this

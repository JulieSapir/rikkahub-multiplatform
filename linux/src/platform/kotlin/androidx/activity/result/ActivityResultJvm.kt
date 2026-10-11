package androidx.activity.result

import android.content.Intent

/**
 * Activity Result API 的桌面端类型：Android 上结果由系统回灌，桌面端由 Swing 对话框直接回调，
 * 这里只保留上游签名需要的数据类。
 */
class ActivityResult(
    val resultCode: Int,
    val data: Intent? = null,
) {
    companion object {
        const val RESULT_OK = -1
        const val RESULT_CANCELED = 0
    }
}

/**
 * 与 Android 一样：媒体类型是 Int 常量（ImageOnly=1 / VideoOnly=2 / ImageAndVideo=3），
 * 这样 `PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)` 原样可用。
 */
class PickVisualMediaRequest(val mediaType: Int = ImageAndVideo) {
    companion object {
        const val ImageOnly = 1
        const val VideoOnly = 2
        const val ImageAndVideo = 3
    }
}

/** 非 compose 版启动器（上游从 ActivityResultRegistry 拿到的那种），桌面端由 contract 直接执行。 */
interface ActivityResultLauncher<I> {
    fun launch(input: I)

    fun dispose()
}

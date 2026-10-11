package android.app

import android.net.Uri

/**
 * Android 的 DownloadManager 会把 APK 后台下载到公共目录并挂通知，桌面端没有这套设施：
 * Context.getSystemService(DOWNLOAD_SERVICE) 返回 null，UpdateChecker 的那段 runCatching
 * 因此落到“打开下载页”的分支，这正是我们要的行为。这里只补类型让上游代码能编译。
 */
class DownloadManager {
    class Request(val uri: Uri) {
        companion object {
            const val VISIBILITY_VISIBLE_NOTIFY_COMPLETED = 1
            const val NETWORK_WIFI = 1
            const val NETWORK_MOBILE = 2
        }

        fun setTitle(title: CharSequence): Request = this

        fun setDescription(description: CharSequence): Request = this

        fun setNotificationVisibility(visibility: Int): Request = this

        fun setAllowedNetworkTypes(types: Int): Request = this

        fun setDestinationInExternalPublicDir(dirType: String, subPath: String): Request = this

        fun setMimeType(mimeType: String): Request = this
    }

    fun enqueue(request: Request): Long = throw UnsupportedOperationException("桌面端没有 DownloadManager")
}

package android.provider

import android.net.Uri

/**
 * 桌面端没有媒体库：上游“保存到相册”的代码在我方 ContextUtilJvm 里已经改成落到 exports/，
 * 这里只提供被引用到的常量与 URI，保证编译。
 */
object MediaStore {
    object Images {
        const val Media_DisplayName = "display_name"

        object Media {
            val EXTERNAL_CONTENT_URI: Uri = Uri.parse("file:///Pictures")
            const val TITLE = "title"
            const val DISPLAY_NAME = "display_name"
            const val MIME_TYPE = "mime_type"
            const val DATA = "_data"
            const val SIZE = "size"
            const val DATE_ADDED = "date_added"
        }
    }

    object Video {
        object Media {
            val EXTERNAL_CONTENT_URI: Uri = Uri.parse("file:///Movies")
            const val DISPLAY_NAME = "display_name"
        }
    }

    object MediaColumns {
        const val DISPLAY_NAME = "display_name"
        const val MIME_TYPE = "mime_type"
        const val RELATIVE_PATH = "relative_path"
        const val TITLE = "title"
        const val DATE_ADDED = "date_added"
        const val SIZE = "size"
        const val _ID = "_id"
    }

    object Files {
        object Media {
            const val COLUMN_DATA = "_data"
        }
    }

    const val VISIBILITY_PUBLIC = 0
    const val VISIBILITY_UNSCANNED = 2
}

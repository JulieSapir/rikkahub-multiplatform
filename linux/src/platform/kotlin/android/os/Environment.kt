package android.os

import java.io.File

/** 上游只用到这一个目录常量。 */
object Environment {
    const val DIRECTORY_DOWNLOADS = "Download"
    const val DIRECTORY_DOCUMENTS = "Documents"
    const val DIRECTORY_PICTURES = "Pictures"
    const val DIRECTORY_MUSIC = "Music"
    const val DIRECTORY_MOVIES = "Movies"

    /** 桌面端对应 ~/Pictures、~/Movies 这样的用户目录。 */
    fun getExternalStoragePublicDirectory(type: String): File =
        File(System.getProperty("user.home"), type).apply { mkdirs() }
}

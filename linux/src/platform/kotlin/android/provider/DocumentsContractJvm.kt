package android.provider

import android.content.ContentResolver
import android.net.Uri
import java.io.File

/**
 * SAF 的列名常量与“目录树”接口在桌面端直接落到真实文件系统：
 * 工作区导出（WorkspaceDetailVM）走的是 createDocument/deleteDocument，
 * 这里实现成建文件/删文件，功能等价，调用处不用改。
 */
object DocumentsContract {
    const val COLUMN_DOCUMENT_ID = "document_id"
    const val COLUMN_DISPLAY_NAME = "_display_name"

    object Document {
        const val COLUMN_DOCUMENT_ID = "document_id"
        const val COLUMN_DISPLAY_NAME = "_display_name"
        const val COLUMN_MIME_TYPE = "mime_type"
        const val COLUMN_SIZE = "_size"
        const val COLUMN_FLAGS = "flags"
        const val COLUMN_LAST_MODIFIED = "last_modified"
        const val FLAG_SUPPORTS_CREATE = 1
        const val FLAG_SUPPORTS_DELETE = 4
        const val FLAG_SUPPORTS_WRITE = 2
        const val MIME_TYPE_DIR = "vnd.android.document/directory"
    }

    object Root {
        const val COLUMN_ROOT_ID = "root_id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_SUMMARY = "summary"
        const val COLUMN_FLAGS = "flags"
        const val COLUMN_MIME_TYPES = "mime_types"
        const val FLAG_LOCAL_ONLY = 1
        const val FLAG_SUPPORTS_CREATE = 2
    }

    fun isDocumentUri(context: android.content.Context, uri: Uri?): Boolean = uri != null && uri.scheme == "file"

    /** 桌面端 tree uri 就是目录本身，documentId 是它的绝对路径。 */
    fun getTreeDocumentId(treeUri: Uri): String = pathOf(treeUri)

    fun buildDocumentUriUsingTree(treeUri: Uri, documentId: String): Uri =
        Uri.fromFile(File(pathOf(treeUri), documentId.removePrefix("/")))

    fun buildChildDocumentsUri(treeUri: Uri, parentDocumentId: String): Uri =
        Uri.fromFile(File(pathOf(treeUri), parentDocumentId.removePrefix("/")))

    fun buildDocumentUri(uri: Uri, documentId: String): Uri = Uri.fromFile(File(documentId))

    fun createDocument(
        resolver: ContentResolver,
        treeUri: Uri,
        mimeType: String?,
        displayName: String,
    ): Uri? = runCatching {
        // SAF 契约是必然生成唯一名：目标已存在时换 "name (1).ext" 再建，不能静默覆盖旧文件
        val dir = File(pathOf(treeUri))
        val target = uniqueFile(File(dir, displayName))
        if (mimeType == Document.MIME_TYPE_DIR) {
            if (!target.mkdirs() && !target.isDirectory) return null
        } else {
            target.parentFile?.mkdirs()
            if (!target.exists() && !target.createNewFile()) return null
        }
        Uri.fromFile(target)
    }.getOrNull()

    /** 与 Android 的 DocumentsProvider 一致：已存在时生成 "name (n).ext"，n 从 1 递增。 */
    private fun uniqueFile(target: File): File {
        if (!target.exists()) return target
        val name = target.name
        val base = name.substringBeforeLast('.', name)
        val ext = if (name == base) "" else ".${name.substringAfterLast('.')}"
        var index = 1
        while (true) {
            val candidate = File(target.parentFile, "$base ($index)$ext")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    fun deleteDocument(resolver: ContentResolver, uri: Uri): Boolean =
        File(pathOf(uri)).let { if (it.isDirectory) it.deleteRecursively() else it.delete() }

    fun renameDocument(resolver: ContentResolver, uri: Uri, displayName: String): Uri? =
        runCatching {
            val source = File(pathOf(uri))
            val target = File(source.parentFile, displayName)
            if (!source.renameTo(target)) return null
            Uri.fromFile(target)
        }.getOrNull()

    private fun pathOf(uri: Uri): String = uri.path ?: uri.toString()
}

object OpenableColumns {
    const val DISPLAY_NAME = "_display_name"
    const val SIZE = "_size"
}

package androidx.activity.result.contract

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Android 的 Activity Result API 在桌面端由 Swing 文件选择框实现：`launch(input)` 依旧异步，
 * 结果在对话框关闭后回调，调用方的写法不用改。
 *
 * 拍照、录像这类没有桌面等价物的契约返回“取消”；运行时权限一律视为已授予，
 * 与 ActivityCompat 的判定保持一致。
 */
interface ActivityResultContract<I, O> {
    fun execute(context: Context, input: I, callback: (O) -> Unit)
}

object ActivityResultContracts {

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "webm", "mkv", "avi")

    class CreateDocument(private val mimeType: String = "*/*") : ActivityResultContract<String, Uri?> {
        val mimeTypeValue: String get() = mimeType

        override fun execute(context: Context, input: String, callback: (Uri?) -> Unit) {
            pickFiles(save = true, suggestedName = input, extensions = extensionsFor(mimeType)) { files ->
                callback(files.firstOrNull()?.let(Uri::fromFile))
            }
        }
    }

    class GetContent(private val mimeType: String = "*/*") : ActivityResultContract<String, Uri?> {
        val mimeTypeValue: String get() = mimeType

        override fun execute(context: Context, input: String, callback: (Uri?) -> Unit) {
            pickFiles(extensions = extensionsFor(input.ifBlank { mimeType })) { files ->
                callback(files.firstOrNull()?.let(Uri::fromFile))
            }
        }
    }

    class GetMultipleContents(private val mimeType: String = "*/*") : ActivityResultContract<String, List<Uri>> {
        val mimeTypeValue: String get() = mimeType

        override fun execute(context: Context, input: String, callback: (List<Uri>) -> Unit) {
            pickFiles(multiple = true, extensions = extensionsFor(input.ifBlank { mimeType })) { files ->
                callback(files.map(Uri::fromFile))
            }
        }
    }

    class OpenDocument : ActivityResultContract<Array<String>, Uri?> {
        override fun execute(context: Context, input: Array<String>, callback: (Uri?) -> Unit) {
            pickFiles(extensions = mimeExtensions(input)) { files ->
                callback(files.firstOrNull()?.let(Uri::fromFile))
            }
        }
    }

    class OpenMultipleDocuments : ActivityResultContract<Array<String>, List<Uri>> {
        override fun execute(context: Context, input: Array<String>, callback: (List<Uri>) -> Unit) {
            pickFiles(multiple = true, extensions = mimeExtensions(input)) { files ->
                callback(files.map(Uri::fromFile))
            }
        }
    }

    /** 桌面端就是一个目录选择框，返回 file:// Uri，上游的 tree uri 解析在 Context 替身里已支持。 */
    class OpenDocumentTree : ActivityResultContract<Uri?, Uri?> {
        override fun execute(context: Context, input: Uri?, callback: (Uri?) -> Unit) {
            pickFiles(directories = true) { files ->
                callback(files.firstOrNull()?.let(Uri::fromFile))
            }
        }
    }

    class PickVisualMedia(private val mediaType: Int = PickVisualMediaRequest.ImageAndVideo) :
        ActivityResultContract<PickVisualMediaRequest, Uri?> {
        override fun execute(context: Context, input: PickVisualMediaRequest, callback: (Uri?) -> Unit) {
            pickFiles(extensions = mediaExtensions(input.mediaType)) { files ->
                callback(files.firstOrNull()?.let(Uri::fromFile))
            }
        }

        companion object {
            const val ImageOnly = PickVisualMediaRequest.ImageOnly
            const val VideoOnly = PickVisualMediaRequest.VideoOnly
            const val ImageAndVideo = PickVisualMediaRequest.ImageAndVideo
        }
    }

    class PickMultipleVisualMedia(private val maxItems: Int = Int.MAX_VALUE) :
        ActivityResultContract<PickVisualMediaRequest, List<Uri>> {
        override fun execute(context: Context, input: PickVisualMediaRequest, callback: (List<Uri>) -> Unit) {
            pickFiles(multiple = true, extensions = mediaExtensions(input.mediaType)) { files ->
                callback(files.take(maxItems).map(Uri::fromFile))
            }
        }
    }

    class RequestPermission : ActivityResultContract<String, Boolean> {
        override fun execute(context: Context, input: String, callback: (Boolean) -> Unit) = callback(true)
    }

    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>> {
        override fun execute(context: Context, input: Array<String>, callback: (Map<String, Boolean>) -> Unit) =
            callback(input.associateWith { true })
    }

    class TakePicture : ActivityResultContract<Uri, Boolean> {
        override fun execute(context: Context, input: Uri, callback: (Boolean) -> Unit) = callback(false)
    }

    class TakeVideo : ActivityResultContract<Uri, Boolean> {
        override fun execute(context: Context, input: Uri, callback: (Boolean) -> Unit) = callback(false)
    }

    /** 桌面端没有“另一个 Activity 返回结果”，直接给取消，调用方走失败分支。 */
    class StartActivityForResult : ActivityResultContract<Intent, ActivityResult> {
        override fun execute(context: Context, input: Intent, callback: (ActivityResult) -> Unit) =
            callback(ActivityResult(ActivityResult.RESULT_CANCELED))
    }

    private fun mimeExtensions(mimeTypes: Array<String>): Set<String>? {
        val extensions = mimeTypes.flatMap { extensionsFor(it).orEmpty() }.toSet()
        return extensions.takeIf { it.isNotEmpty() }
    }

    private fun mediaExtensions(mediaType: Int): Set<String> = when (mediaType) {
        PickVisualMediaRequest.ImageOnly -> IMAGE_EXTENSIONS
        PickVisualMediaRequest.VideoOnly -> VIDEO_EXTENSIONS
        else -> IMAGE_EXTENSIONS + VIDEO_EXTENSIONS
    }
}

private fun extensionsFor(mimeType: String): Set<String>? = when (mimeType) {
    "*/*", "", "application/octet-stream" -> null
    "image/*" -> IMAGE_EXT
    "video/*" -> VIDEO_EXT
    "application/json" -> setOf("json")
    "text/plain" -> setOf("txt", "md")
    "application/zip" -> setOf("zip")
    else -> mimeType.substringAfter('/').takeIf { it.isNotBlank() && it != "*" }?.let { setOf(it) }
}

private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
private val VIDEO_EXT = setOf("mp4", "mov", "webm", "mkv", "avi")

private val pickerScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

/**
 * 构造选择框这一步会走 Swing 的 LookAndFeel（`UIManager` 按类名反射实例化 `Metal*UI`），
 * 所以单独拆出来：AOT 采集元数据时也要把同样的路径跑一遍，否则原生包里缺这些反射项，
 * 运行时表现为「no ComponentUI class for javax.swing.JFileChooser」。
 */
internal fun buildFileChooser(
    multiple: Boolean = false,
    directories: Boolean = false,
    extensions: Set<String>? = null,
    save: Boolean = false,
    suggestedName: String? = null,
): JFileChooser = JFileChooser().apply {
    fileSelectionMode = if (directories) JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_ONLY
    isMultiSelectionEnabled = multiple
    dialogTitle = if (save) "保存文件" else "选择文件"
    if (save) {
        selectedFile = File(suggestedName?.substringAfterLast('/').takeUnless { it.isNullOrBlank() } ?: "output.txt")
    }
    if (!extensions.isNullOrEmpty()) {
        fileFilter = FileNameExtensionFilter(
            extensions.joinToString(" / ").uppercase(),
            *extensions.toTypedArray(),
        )
    }
}

internal fun pickFiles(
    multiple: Boolean = false,
    directories: Boolean = false,
    extensions: Set<String>? = null,
    save: Boolean = false,
    suggestedName: String? = null,
    callback: (List<File>) -> Unit,
) {
    pickerScope.launch {
        val chooser = buildFileChooser(multiple, directories, extensions, save, suggestedName)
        val outcome = if (save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
        if (outcome != JFileChooser.APPROVE_OPTION) {
            callback(emptyList())
            return@launch
        }
        val chosen = if (multiple) {
            chooser.selectedFiles.orEmpty().toList()
        } else {
            listOfNotNull(chooser.selectedFile)
        }
        // 保存时若没写后缀，按过滤器的第一个扩展名补上，和 Android 的 CreateDocument 行为一致
        callback(
            chosen.map { file ->
                if (save && file.extension.isEmpty() && !extensions.isNullOrEmpty()) {
                    File(file.parentFile, "${file.name}.${extensions.first()}")
                } else {
                    file
                }
            },
        )
    }
}

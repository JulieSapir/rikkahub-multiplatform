package me.rerere.rikkahub.data.export

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import java.awt.FileDialog
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 上游那份 ExportHooks 走 SAF 的 CreateDocument/OpenDocument 与 FileProvider 分享，
 * 桌面端换成系统文件对话框：`ui/components/ui/Export.kt` 与提示词页的调用处保持不变。
 */
@Stable
class ExporterState<T>(
    private val data: T,
    private val serializer: ExportSerializer<T>,
    private val context: Context,
    private val scope: CoroutineScope,
) {
    val value: String
        get() = serializer.exportToJson(data)

    val fileName: String
        get() = serializer.getExportFileName(data)

    fun exportToFile(fileName: String = this.fileName) {
        scope.launch(Dispatchers.IO) {
            pickFile(fileName, save = true)?.let { target ->
                target.writeText(value, Charsets.UTF_8)
            }
        }
    }

    /** 桌面端没有系统分享面板，落盘后直接把路径打到控制台，行为与 exportImage 的替身一致。 */
    fun exportAndShare(fileName: String = this.fileName) {
        exportToFile(fileName)
    }
}

@Composable
fun <T> rememberExporter(
    data: T,
    serializer: ExportSerializer<T>,
): ExporterState<T> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(data, serializer) {
        ExporterState(
            data = data,
            serializer = serializer,
            context = context,
            scope = scope,
        )
    }
}

@Stable
class ImporterState<T>(
    private val serializer: ExportSerializer<T>,
    private val context: Context,
    private val scope: CoroutineScope,
    private val onResult: (Result<T>) -> Unit,
) {
    fun importFromFile() {
        scope.launch(Dispatchers.IO) {
            val target = pickFile(serializer.type, save = false)
            val result = if (target == null) {
                Result.failure(IllegalArgumentException("已取消选择文件"))
            } else {
                withContext(Dispatchers.IO) {
                    serializer.import(context, Uri.fromFile(target))
                }
            }
            onResult(result)
        }
    }
}

@Composable
fun <T> rememberImporter(
    serializer: ExportSerializer<T>,
    onResult: (Result<T>) -> Unit,
): ImporterState<T> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(serializer) {
        ImporterState(
            serializer = serializer,
            context = context,
            scope = scope,
            onResult = onResult,
        )
    }
}

/** AWT 的文件对话框：save=true 时带建议文件名，取消返回 null。 */
private fun pickFile(suggestedName: String, save: Boolean): File? {
    val dialog = FileDialog(
        null as java.awt.Frame?,
        if (save) "保存" else "选择文件",
        if (save) FileDialog.SAVE else FileDialog.LOAD,
    ).apply {
        if (save) file = suggestedName
    }
    // AWT 组件只能在 EDT 上显示；模态对话框在 EDT 里自旋到关闭，invokeAndWait 随之返回，
    // 调用线程（IO）保持阻塞等待的语义
    try {
        javax.swing.SwingUtilities.invokeAndWait { dialog.isVisible = true }
    } catch (e: java.lang.reflect.InvocationTargetException) {
        throw e.cause ?: e
    }
    val name = dialog.file ?: return null
    return File(dialog.directory ?: System.getProperty("user.home"), name)
}

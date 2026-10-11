package androidx.core.net

import android.net.Uri
import java.io.File

/** AndroidX 的 String.toUri() 扩展，上游代码到处在用 */
fun String.toUri(): Uri = Uri.parse(this)

fun java.io.File.toUri(): Uri = Uri.fromFile(this)

fun Uri.toFile(): File = File(path ?: error("Uri 没有路径: $this"))

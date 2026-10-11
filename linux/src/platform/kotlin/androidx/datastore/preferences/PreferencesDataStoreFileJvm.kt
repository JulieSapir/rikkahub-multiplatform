package androidx.datastore.preferences

import android.content.Context
import java.io.File

/**
 * Android 的 Context.preferencesDataStoreFile 在桌面端的对应实现。
 *
 * DataStore 的 KMP 产物只提供 PreferenceDataStoreFactory，落盘位置由调用方给；这里沿用 Android 的
 * 文件名（<name>.preferences_pb，DataStore 会校验这个后缀），便于直接对照 Android 端的数据排查。
 */
fun Context.preferencesDataStoreFile(name: String): File =
    File(dataDir, "data/${name}.preferences_pb").apply { parentFile?.mkdirs() }

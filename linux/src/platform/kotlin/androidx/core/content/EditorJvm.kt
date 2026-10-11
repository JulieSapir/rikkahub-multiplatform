package androidx.core.content

import android.content.SharedPreferences

/** androidx-core 的 `prefs.edit { putString(...) }`，Android 版也是转手交给 Editor + apply。 */
fun SharedPreferences.edit(action: SharedPreferences.Editor.() -> Unit) {
    edit(action)
}

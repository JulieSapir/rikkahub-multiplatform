package androidx.activity.compose

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.backhandler.BackHandler as ComposeBackHandler

/**
 * `androidx.activity.compose` 的桌面端替身。
 *
 * BackHandler 直接转给 Compose Multiplatform 自己的 `androidx.compose.ui.backhandler.BackHandler`
 * （桌面端绑的是 Esc 键与窗口关闭），语义与 Android 的返回键一致：最内层的处理器优先响应。
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    ComposeBackHandler(enabled = enabled, onBack = onBack)
}

@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): ManagedActivityResultLauncher<I, O> {
    val context = LocalContext.current
    return remember(contract, onResult) {
        ManagedActivityResultLauncher(context, contract, onResult)
    }
}

/** 与 Android 同名：launch 把动作交给平台契约，结果异步回调。 */
class ManagedActivityResultLauncher<I, O>(
    private val context: Context,
    private val contract: ActivityResultContract<I, O>,
    private val onResult: (O) -> Unit,
) : ActivityResultLauncher<I> {
    override fun launch(input: I) = contract.execute(context, input, onResult)

    override fun dispose() = Unit
}

val LocalActivity = staticCompositionLocalOf<Activity?> { null }

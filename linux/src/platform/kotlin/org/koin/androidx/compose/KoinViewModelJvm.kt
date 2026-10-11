package org.koin.androidx.compose

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier

/**
 * 上游页面 import 的是 `org.koin.androidx.compose.koinViewModel`（属于 koin-android，只有 Android
 * 产物）。Koin 的 KMP 版把同一个函数放在 `org.koin.compose.viewmodel` 下，这里转发一层，
 * 页面代码就不用改。
 */
@Composable
inline fun <reified T : ViewModel> koinViewModel(
    viewModelStoreOwner: Any? = null,
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null,
): T {
    // 桌面端只有一个窗口，没有 Activity 级的 ViewModelStore；owner 参数只是形参，
    // 作用域由 Koin 的 KMP 版按当前导航条目决定。
    @Suppress("UNUSED_EXPRESSION")
    viewModelStoreOwner
    return org.koin.compose.viewmodel.koinViewModel<T>(qualifier = qualifier, parameters = parameters)
}

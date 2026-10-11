package org.koin.android.ext.android

import org.koin.core.context.GlobalContext
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier

/**
 * `org.koin.android.ext.android.inject` 在 koin-android 里是 ComponentCallbacks 的扩展，
 * 桌面端没有那层：这里对任意对象提供同名扩展，直接从全局 Koin 容器取，语义一致（惰性、复用单例）。
 */
inline fun <reified T : Any> Any.inject(
    qualifier: Qualifier? = null,
    mode: LazyThreadSafetyMode = LazyThreadSafetyMode.SYNCHRONIZED,
    noinline parameters: ParametersDefinition? = null,
): Lazy<T> = lazy(mode) { GlobalContext.get().get(qualifier, parameters) }

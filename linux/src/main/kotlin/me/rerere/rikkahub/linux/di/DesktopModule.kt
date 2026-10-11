package me.rerere.rikkahub.linux.di

import android.app.Application
import android.content.Context
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * 桌面端只补平台相关的单例，其余依赖全部用上游 di 包里的那四个 module
 * （appModule / viewModelModule / dataSourceModule / repositoryModule）。
 *
 * 这样上游加一个 ViewModel 或 Provider 时，桌面端不用跟着改：只要它注册进那四个 module 就自动可用。
 */
fun desktopPlatformModule(application: Application): Module = module {
    single<Application> { application }
    single<Context> { application }
}

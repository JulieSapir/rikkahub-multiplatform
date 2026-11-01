package me.rerere.rikkahub

/**
 * Android 的 BuildConfig 由 AGP 生成，桌面端手写一份：上游只有少几处用到版本号与调试标记。
 */
object BuildConfig {
    const val APPLICATION_ID = "me.rerere.rikkahub"
    const val DEBUG = false
    const val VERSION_NAME = "2.5.6"
    const val VERSION_CODE = 191
    const val AUTHORITY = "me.rerere.rikkahub.fileprovider"
    // Play 渠道禁用自更新等入口的开关，桌面端不走 Play 商店，恒为 false
    const val IS_PLAY_STORE = false
}

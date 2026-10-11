package android

/**
 * 系统自带字符串的 id（值取自真实 Android framework 常量），桌面端由 RikkaRes 翻译成当前语言。
 * 上游只在“取消/确定”按钮上用到这两个。
 */
object R {
    object string {
        const val ok = 0x0104000C
        const val cancel = 0x01040009
        const val yes = 0x01040012
        const val no = 0x01040015
        const val copy = 0x01040036
        const val paste = 0x01040037
    }
}

package android.view

/**
 * 终端页把按键事件转成 VT 序列时用到这两个类的常量。
 * 桌面端由 Compose 的 KeyEvent 翻译过来，字段含义与 Android 保持一致。
 */
class KeyEvent private constructor(
    val action: Int = ACTION_DOWN,
    val keyCode: Int = 0,
    val unicodeChar: Int = 0,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val ctrl: Boolean = false,
) {
    fun isShiftPressed(): Boolean = shift

    fun isAltPressed(): Boolean = alt

    fun isCtrlPressed(): Boolean = ctrl

    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val KEYCODE_ENTER = 66
        const val KEYCODE_DPAD_UP = 19
        const val KEYCODE_DPAD_DOWN = 20
        const val KEYCODE_DPAD_LEFT = 21
        const val KEYCODE_DPAD_RIGHT = 22
        const val KEYCODE_BACKSPACE = 67
        const val KEYCODE_TAB = 61
        const val KEYCODE_ESCAPE = 111
        const val KEYCODE_DEL = 67
        const val META_SHIFT_ON = 1
        const val META_ALT_ON = 2
        const val META_CTRL_ON = 4096
        const val KEYCODE_VOLUME_UP = 24
        const val KEYCODE_VOLUME_DOWN = 25

        fun of(
            keyCode: Int,
            unicodeChar: Int = 0,
            shift: Boolean = false,
            alt: Boolean = false,
            ctrl: Boolean = false,
            action: Int = ACTION_DOWN,
        ): KeyEvent = KeyEvent(action, keyCode, unicodeChar, shift, alt, ctrl)
    }
}

class MotionEvent private constructor(val action: Int) {
    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3

        fun obtain(action: Int): MotionEvent = MotionEvent(action)
    }
}

object WindowManager {
    object LayoutParams {
        const val FLAG_KEEP_SCREEN_ON = 128
        const val FLAG_NOT_FOCUSABLE = 8
        const val SOFT_INPUT_ADJUST_RESIZE = 48
        const val TYPE_APPLICATION_PANEL = 1
    }
}

package com.termux.view

import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession

/**
 * Termux 的 TerminalView 是 Android View（自绘 + 手势选择 + 软键盘），桌面端没有 View 层。
 * 这里保留上游用到的成员，渲染交给下面的 [DesktopTerminalView]：按行取屏幕缓冲区的纯文本，
 * 键盘事件翻译成控制序列写回 pty。颜色与光标样式暂不还原（Termux 那边是位图级自绘）。
 */
class TerminalView {
    var mEmulator: TerminalEmulator? = null
        private set

    var session: TerminalSession? = null
        private set

    var isSelectingText: Boolean = false

    var textSizePx: Int = 14
        private set

    var typeface: Typeface = Typeface.MONOSPACE
        private set

    private var revision = 0L

    var screenVersion: Long by mutableLongStateOf(0L)
        private set

    fun attachSession(target: TerminalSession?) {
        session = target
        mEmulator = target?.emulator
        onScreenUpdated()
    }

    fun onScreenUpdated() {
        revision += 1
        screenVersion = revision
    }

    fun invalidate() = onScreenUpdated()

    fun post(action: Runnable): Boolean {
        action.run()
        return true
    }

    fun requestFocus(): Boolean = true

    fun setTextSize(sizePx: Int) {
        textSizePx = sizePx
    }

    fun setTypeface(face: Typeface) {
        typeface = face
    }

    /** Termux 的命中测试；桌面端只用它做“点 URL 打开”，返回左上角保持接口可用。 */
    fun getColumnAndRow(event: MotionEvent?, includeCursor: Boolean): IntArray = intArrayOf(0, 0)
}

/**
 * 终端画面：等宽文本网格 + 滚动回看 + 键盘直通 shell。
 */
@Composable
fun DesktopTerminalView(
    view: TerminalView,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
) {
    val focusRequester = remember { FocusRequester() }
    val scroll = rememberScrollState()
    // 读一次快照值：终端有新输出时这里变化，下面的文本网格才会重组
    val version = view.screenVersion

    LaunchedEffect(version) {
        // 有新输出时贴到底部，像真实终端那样跟随
        scroll.animateScrollTo(scroll.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .padding(12.dp)
            .verticalScroll(scroll)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val bytes = encodeTerminalKey(event) ?: return@onPreviewKeyEvent false
                view.session?.write(bytes, 0, bytes.size)
                view.onScreenUpdated()
                true
            },
    ) {
        val emulator = view.mEmulator
        if (emulator == null) {
            Text("终端未就绪", color = Color(0xFFE6E6E6), fontFamily = FontFamily.Monospace)
        } else {
            val columns = emulator.mColumns
            val rows = emulator.mRows
            val screen = emulator.getScreen()
            val firstRow = -screen.activeTranscriptRows
            (firstRow until rows).forEach { row ->
                val line = screen.getSelectedText(0, row, columns - 1, row) ?: ""
                Text(
                    text = line.ifEmpty { " " },
                    color = Color(0xFFE6E6E6),
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        focusRequester.requestFocus()
        onDispose { }
    }
}

private fun encodeTerminalKey(event: ComposeKeyEvent): ByteArray? {
    val ctrl = event.isCtrlPressed
    when (event.key) {
        Key.Enter -> return "\r".toByteArray()
        Key.Escape -> return "\u001b".toByteArray()
        Key.Backspace -> return "\u007f".toByteArray()
        Key.Tab -> return "\t".toByteArray()
        Key.DirectionUp -> return if (ctrl) "\u001b[A".toByteArray() else ESC_UP
        Key.DirectionDown -> return ESC_DOWN
        Key.DirectionRight -> return ESC_RIGHT
        Key.DirectionLeft -> return ESC_LEFT
        Key.MoveHome -> return ESC_HOME
        Key.MoveEnd -> return ESC_END
        Key.Delete -> return ESC_DELETE
        Key.PageUp -> return if (event.isShiftPressed) "\u001b[5;2~".toByteArray() else "\u001b[5~".toByteArray()
        Key.PageDown -> return if (event.isShiftPressed) "\u001b[6;2~".toByteArray() else "\u001b[6~".toByteArray()
        else -> Unit
    }
    val codePoint = event.utf16CodePoint
    if (codePoint <= 0) return null
    if (ctrl && codePoint in 'a'.code..'z'.code) {
        // Ctrl+A..Z -> 0x01..0x1A
        return byteArrayOf((codePoint - 'a'.code + 1).toByte())
    }
    if (codePoint < 32) return byteArrayOf(codePoint.toByte())
    return Character.toString(codePoint).toByteArray()
}

private val ESC_UP = "\u001b[A".toByteArray()
private val ESC_DOWN = "\u001b[B".toByteArray()
private val ESC_RIGHT = "\u001b[C".toByteArray()
private val ESC_LEFT = "\u001b[D".toByteArray()
private val ESC_HOME = "\u001b[H".toByteArray()
private val ESC_END = "\u001b[F".toByteArray()
private val ESC_DELETE = "\u001b[3~".toByteArray()

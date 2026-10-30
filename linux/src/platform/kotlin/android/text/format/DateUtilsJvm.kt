package android.text.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

/**
 * AssistantMemoryPage 用 [getRelativeTimeSpanString] 显示记忆的相对时间。
 * 桌面端没有框架实现，这里按同一语义给一份简化版：
 * 一小时内按分钟、一天内按小时、一周内按天，更早直接显示日期（月-日，跨年带年份）。
 * FLAG_* 与 *_IN_MILLIS 只保留调用方用到的常量。
 */
object DateUtils {
    val MINUTE_IN_MILLIS: Long = TimeUnit.MINUTES.toMillis(1)
    val HOUR_IN_MILLIS: Long = TimeUnit.HOURS.toMillis(1)
    val DAY_IN_MILLIS: Long = TimeUnit.DAYS.toMillis(1)
    val WEEK_IN_MILLIS: Long = TimeUnit.DAYS.toMillis(7)

    const val FORMAT_SHOW_DATE: Int = 0x00001
    const val FORMAT_SHOW_TIME: Int = 0x00002
    const val FORMAT_SHOW_WEEKDAY: Int = 0x00004
    const val FORMAT_ABBREV_MONTH: Int = 0x10000
    const val FORMAT_ABBREV_WEEKDAY: Int = 0x20000
    const val FORMAT_NO_YEAR: Int = 0x80000

    fun getRelativeTimeSpanString(
        time: Long,
        now: Long,
        minResolution: Long,
        flags: Int,
    ): CharSequence = relative(time, now)

    fun getRelativeTimeSpanString(time: Long): CharSequence =
        relative(time, System.currentTimeMillis())

    private fun relative(time: Long, now: Long): String {
        val delta = (now - time).coerceAtLeast(0)
        return when {
            delta < MINUTE_IN_MILLIS -> "刚刚"
            delta < HOUR_IN_MILLIS -> "${delta / MINUTE_IN_MILLIS} 分钟前"
            delta < DAY_IN_MILLIS -> "${delta / HOUR_IN_MILLIS} 小时前"
            delta < WEEK_IN_MILLIS -> "${delta / DAY_IN_MILLIS} 天前"
            else -> {
                val date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
                val sameYear = date.year == Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().year
                val style = if (sameYear) FormatStyle.SHORT else FormatStyle.MEDIUM
                val formatter = DateTimeFormatter.ofLocalizedDate(style)
                // 非默认 Locale 的本地化日期格式可能带星期/纪元信息，这里统一按本地时区的 ISO 日期兜底
                runCatching { date.format(formatter) }.getOrElse {
                    if (sameYear) date.format(DateTimeFormatter.ofPattern("M-d"))
                    else date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                }
            }
        }
    }
}

package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.event.AppEventBus

/**
 * 日历与屏幕时间取自 Android 的 CalendarContract / UsageStatsManager，桌面端没有这两个系统服务。
 *
 * 上游 LocalTools 仍按用户勾选注册这些工具，所以这里保留同名构造函数，让工具本身可用但返回
 * “本端不支持”，比把选项从设置页里抹掉更省事，也不会因上游调整 LocalTools 而漂移。
 */
private fun unsupportedTool(name: String, description: String): Tool = Tool(
    name = name,
    description = description,
    parameters = { InputSchema.Obj(properties = buildJsonObject {}) },
    execute = {
        val payload = buildJsonObject {
            put("success", false)
            put("error", "$name is not available on the desktop client")
        }
        listOf(UIMessagePart.Text(payload.toString()))
    },
)

internal fun buildCalendarQueryTool(context: Context): Tool = unsupportedTool(
    name = "calendar_query",
    description = "Query the device calendar. Not available on the desktop client.",
)

internal fun buildCalendarCreateTool(context: Context): Tool = unsupportedTool(
    name = "calendar_create",
    description = "Create a calendar event on the device. Not available on the desktop client.",
)

internal fun buildScreenTimeTool(context: Context, eventBus: AppEventBus): Tool = unsupportedTool(
    name = "screen_time",
    description = "Read the device screen-time statistics. Not available on the desktop client.",
)

package me.rerere.rikkahub.linux.res

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.RikkaResLookup
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 生成的资源表的运行时入口。
 *
 * 资源由 linux/gradle/android-res.gradle.kts 从 app/src/main/res 生成，所以这里不维护清单，
 * 上游加的字符串和图标会自动出现。
 */
object RikkaRes {
    private val json = Json { isLenient = true }
    private val tables = ConcurrentHashMap<String, Map<String, String>>()

    /** id 由 R 生成，从 1 开始，下标 0 是空串，表示“查不到” */
    fun string(id: Int, locale: Locale): String {
        // android.R.string.* 由系统翻译，桌面端自己给一份：cancel 复用 app 的同名译文
        if (id == ANDROID_CANCEL) return lookupKey("app/cancel", locale)
        if (id == ANDROID_OK) return ANDROID_OK_TEXT[locale.language] ?: "OK"

        return lookupKey(RikkaResLookup.stringNames.getOrNull(id).orEmpty(), locale)
    }

    private fun lookupKey(key: String, locale: Locale): String {
        if (key.isEmpty()) return ""
        for (tag in localeCandidates(locale)) {
            val value = tableOf(tag)[key]
            if (!value.isNullOrEmpty()) return value
        }
        return key
    }

    /** 矢量 drawable（VectorDrawable 的 xml）在 jar 里的路径，交给 VectorDrawableParser 解析 */
    fun vectorResourcePath(id: Int): String? = filePath(id, xml = true)

    /** 位图资源在 jar 里的路径；矢量 drawable 走 vectorResourcePath */
    fun resourcePath(id: Int): String? = filePath(id, xml = false)

    /** id 由 R 生成，跨模块、跨 group 全局唯一，所以下标直接就是 id，不用调用处说明是哪个 group */
    private fun filePath(id: Int, xml: Boolean): String? {
        val file = RikkaResLookup.binaryFiles.getOrNull(id).orEmpty()
        if (file.isEmpty() || file.endsWith(".xml") != xml) return null
        return "/rikka-res/$file"
    }

    fun textOf(path: String): String? = bytesOf(path)?.decodeToString()

    fun bytesOf(path: String): ByteArray? =
        RikkaRes::class.java.getResourceAsStream(path)?.use { it.readBytes() }

    private fun tableOf(tag: String): Map<String, String> = tables.getOrPut(tag) {
        val text = RikkaRes::class.java.getResourceAsStream("/rikka-res/strings/$tag.json")
            ?.reader(Charsets.UTF_8)?.readText() ?: return@getOrPut emptyMap()
        runCatching {
            json.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonPrimitive.contentOrNull ?: "" }
        }.getOrDefault(emptyMap())
    }

    private const val ANDROID_OK = 0x0104000C
    private const val ANDROID_CANCEL = 0x01040009

    private val ANDROID_OK_TEXT = mapOf(
        "zh" to "确定",
        "ja" to "OK",
        "ko" to "확인",
        "ru" to "ОК",
        "ar" to "حسنا",
    )

    /** zh_TW -> [zh-TW, zh, default]，与 Android 的 values-<lang>-r<REGION> 回退顺序一致 */
    private fun localeCandidates(locale: Locale): List<String> = buildList {
        if (locale.country.isNotEmpty()) add("${locale.language}-${locale.country}")
        if (locale.language.isNotEmpty()) add(locale.language)
        add("default")
    }
}

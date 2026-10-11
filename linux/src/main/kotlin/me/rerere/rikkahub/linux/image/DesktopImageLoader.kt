package me.rerere.rikkahub.linux.image

import coil3.map.Mapper
import coil3.request.Options
import java.io.File
import me.rerere.rikkahub.linux.res.DesktopAssets
import me.rerere.rikkahub.linux.res.RikkaRes

/**
 * 上游在 RouteActivity 里用 setSingletonImageLoaderFactory 配 Coil，Android 上有两种数据源是框架自带
 * 的映射，JVM 版 Coil 没有：`file:///android_asset/...` 和按资源 id 加载（AsyncImage(model = R.xxx)）。
 * 这里各补一个 Mapper，由 linux/gradle/source-compat.gradle.kts 的规则注入进那条 components 链。
 *
 * 映射结果都是磁盘文件，之后交给 Coil 自带的文件与 SVG 解码器，显示逻辑与 Android 一致。
 */
class DesktopAssetUriMapper : Mapper<coil3.Uri, File> {
    override fun map(data: coil3.Uri, options: Options): File? {
        val path = data.path ?: return null
        if (!path.startsWith(ASSET_PREFIX)) return null
        return DesktopAssets.extract(path.removePrefix(ASSET_PREFIX))
    }

    companion object {
        private const val ASSET_PREFIX = "/android_asset/"
    }
}

class DesktopResourceIdMapper : Mapper<Int, File> {
    override fun map(data: Int, options: Options): File? =
        RikkaRes.resourcePath(data)?.let(DesktopAssets::extractResource)
}

package coil3.gif

import coil3.ImageLoader
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options

/**
 * Android 端要显式注册 GIF/动图解码器（GifDecoder，API 28+ 用 AnimatedImageDecoder），
 * JB 桌面端的 Coil 用 Skia 直接解动图。这里保留同名工厂但永不产解码器，
 * 于是上游 `if (SDK_INT >= P) ... else ...` 那两行不用改，动图仍由内置解码器处理。
 */
object GifDecoder {
    fun Factory(): Decoder.Factory = PassThroughDecoderFactory
}

object AnimatedImageDecoder {
    fun Factory(): Decoder.Factory = PassThroughDecoderFactory
}

private object PassThroughDecoderFactory : Decoder.Factory {
    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? = null
}

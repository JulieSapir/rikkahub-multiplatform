package android.util

import java.util.Base64 as JdkBase64

/**
 * speech 模块里两个 provider 用 android.util.Base64 解音频，其余逻辑都是纯 JVM 的，
 * 所以补这层壳而不是把整个 providers 目录换成桌面端实现。
 */
object Base64 {
    const val DEFAULT = 0
    const val NO_WRAP = 2
    const val NO_PADDING = 1
    const val URL_SAFE = 8
    const val MIME = 4

    private fun decoder(flags: Int): java.util.Base64.Decoder =
        if (flags and URL_SAFE != 0) JdkBase64.getUrlDecoder() else JdkBase64.getMimeDecoder()

    private fun encoder(flags: Int): java.util.Base64.Encoder =
        if (flags and URL_SAFE != 0) JdkBase64.getUrlEncoder() else JdkBase64.getEncoder()

    fun decode(str: String, flags: Int): ByteArray = runCatching { decoder(flags).decode(str) }.getOrDefault(ByteArray(0))

    fun decode(input: ByteArray, flags: Int): ByteArray = runCatching { decoder(flags).decode(input) }.getOrDefault(ByteArray(0))

    fun encodeToString(input: ByteArray, flags: Int): String = encoder(flags).encodeToString(input)

    fun encodeToString(input: ByteArray, flags: Int, offset: Int, len: Int): String =
        encoder(flags).encodeToString(input.copyOfRange(offset, offset + len))

    fun encode(input: ByteArray, flags: Int): ByteArray = encoder(flags).encode(input)
}

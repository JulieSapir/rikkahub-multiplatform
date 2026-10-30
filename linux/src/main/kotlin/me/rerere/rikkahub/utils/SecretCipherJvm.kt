package me.rerere.rikkahub.utils

import me.rerere.rikkahub.linux.state.AppPaths
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android 那份（上游原文件）用 Keystore 里不可导出的 AES 密钥加解密 SSH 凭据等敏感字符串。
 * 桌面端没有 Keystore，改为把 256 位 AES 密钥落在数据目录的 keys/secret-cipher.key（0600），
 * 算法与密文格式（v1: base64(iv + ciphertext)）与 Android 端完全一致。
 *
 * 与 Android 端同样的限制：密文和密钥绑定本机，换机器迁移备份后无法解密，调用方需提示重填。
 */
object SecretCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val VERSION_PREFIX = "v1:"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    private val keyFile: File
        get() = File(AppPaths.dataDir, "keys/secret-cipher.key")

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return VERSION_PREFIX + Base64.getEncoder().encodeToString(cipher.iv + ciphertext)
    }

    fun decrypt(value: String): String {
        require(value.startsWith(VERSION_PREFIX)) { "Unsupported secret format" }
        val bytes = Base64.getDecoder().decode(value.removePrefix(VERSION_PREFIX))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
        return cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE).toString(Charsets.UTF_8)
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val file = keyFile
        if (file.isFile) {
            val encoded = runCatching { Base64.getDecoder().decode(file.readText().trim()) }.getOrNull()
            if (encoded != null && encoded.size == 32) {
                return SecretKeySpec(encoded, "AES")
            }
            // 密钥文件损坏：删掉重建，旧密文解不开由调用方按解密失败提示用户重填
            file.delete()
        }
        val key = KeyGenerator.getInstance("AES").apply { init(256, SecureRandom()) }.generateKey()
        file.parentFile?.mkdirs()
        file.writeText(Base64.getEncoder().encodeToString(key.encoded))
        runCatching {
            // 只限制属主可读写，其它用户不能拿到这台机器的密钥
            val perms = java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
            java.nio.file.Files.setPosixFilePermissions(file.toPath(), perms)
        }
        return key
    }
}

package me.rerere.rikkahub.linux

import me.rerere.rikkahub.linux.web.KcefRuntime
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 运行时包里只解 CEF 的文件：JDK 部分、符号链接与越界路径都不能落盘 */
class KcefRuntimeTest {
    @Test
    fun extractsOnlyCefFiles() {
        val dir = Files.createTempDirectory("kcef-test").toFile()
        try {
            val archive = File(dir, "rt.tar.gz")
            TarArchiveOutputStream(GzipCompressorOutputStream(archive.outputStream())).use { tar ->
                fun file(name: String, mode: Int = 0b110_100_100) {
                    val bytes = name.toByteArray()
                    tar.putArchiveEntry(TarArchiveEntry("jbr/$name").apply { size = bytes.size.toLong(); this.mode = mode })
                    tar.write(bytes)
                    tar.closeArchiveEntry()
                }
                file("lib/libcef.so")
                file("lib/jcef_helper", 0b111_101_101)
                file("lib/resources.pak")
                file("lib/locales/en-US.pak")
                file("lib/libawt.so")
                file("lib/server/libjvm.so")
                file("bin/java")
                file("lib/../../evil.so")
                tar.putArchiveEntry(TarArchiveEntry("jbr/lib/libjcef.so", TarArchiveEntry.LF_SYMLINK).apply { linkName = "/etc/passwd" })
                tar.closeArchiveEntry()
            }
            val out = File(dir, "out")
            runCatching { KcefRuntime.extractCef(archive, out) }
            val names = out.walkTopDown().filter { it.isFile }.map { it.relativeTo(out).path }.sorted().toList()
            assertEquals(listOf("jcef_helper", "libcef.so", "locales/en-US.pak", "resources.pak"), names)
            assertTrue(File(out, "jcef_helper").canExecute())
            assertFalse(File(dir, "evil.so").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}

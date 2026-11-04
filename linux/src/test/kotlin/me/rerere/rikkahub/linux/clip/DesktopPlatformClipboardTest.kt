@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package me.rerere.rikkahub.linux.clip

import androidx.compose.foundation.internal.readText
import androidx.compose.ui.platform.ClipEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

class DesktopPlatformClipboardTest {
    @Test(timeout = 10_000)
    fun `slow native format query leaves the event thread responsive`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val native = object : RecordingClipboard(mapOf(DataFlavor.imageFlavor to image())) {
            override fun getAvailableDataFlavors(): Array<DataFlavor> {
                assertFalse(SwingUtilities.isEventDispatchThread())
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return super.getAvailableDataFlavors()
            }
        }
        val clipboard = DesktopPlatformClipboard { native }
        val read = async(Dispatchers.Swing) { clipboard.getClipEntry() }
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val heartbeat = CountDownLatch(1)
            SwingUtilities.invokeLater { heartbeat.countDown() }
            assertTrue("粘贴等待期间 EDT 应继续响应", heartbeat.await(1, TimeUnit.SECONDS))
        } finally {
            release.countDown()
        }
        assertTrue(read.await() != null)
    }

    @Test
    fun `pasting an image into a text field does not fetch image data`() = runBlocking {
        val native = RecordingClipboard(mapOf(DataFlavor.imageFlavor to image()))
        val clipboard = DesktopPlatformClipboard { native }
        withContext(Dispatchers.Swing) {
            val entry = checkNotNull(clipboard.getClipEntry())
            assertNull(entry.readText())
        }
        assertTrue(native.readFlavors.isEmpty())
    }

    @Test
    fun `text paste reads only text on a worker even when image is offered`() = runBlocking {
        val native = RecordingClipboard(
            mapOf(DataFlavor.stringFlavor to "粘贴文本", DataFlavor.imageFlavor to image()),
        )
        withContext(Dispatchers.Swing) {
            val entry = checkNotNull(DesktopPlatformClipboard { native }.getClipEntry())
            assertEquals("粘贴文本", entry.readText())
        }
        assertEquals(listOf(DataFlavor.stringFlavor), native.readFlavors)
    }

    @Test
    fun `entry preserves native flavors and loads only the requested format`() = runBlocking {
        val image = image()
        val native = RecordingClipboard(mapOf(DataFlavor.imageFlavor to image))
        val clipboard = DesktopPlatformClipboard { native }
        assertSame(native, clipboard.nativeClipboard)
        val transferable = checkNotNull(clipboard.getClipEntry()).nativeClipEntry as Transferable
        val flavors = transferable.transferDataFlavors
        assertTrue(transferable.isDataFlavorSupported(DataFlavor.imageFlavor))
        flavors[0] = DataFlavor.stringFlavor
        assertEquals(DataFlavor.imageFlavor, transferable.transferDataFlavors.single())
        assertSame(image, withContext(Dispatchers.IO) { transferable.getTransferData(DataFlavor.imageFlavor) })
        assertEquals(listOf(DataFlavor.imageFlavor), native.readFlavors)
        try {
            transferable.getTransferData(DataFlavor.stringFlavor)
            throw AssertionError("应拒绝未提供的剪贴板格式")
        } catch (_: UnsupportedFlavorException) {
            assertEquals(listOf(DataFlavor.imageFlavor), native.readFlavors)
        }
    }

    @Test
    fun `copy and clear retain compose clipboard semantics`() = runBlocking {
        val native = Clipboard("test")
        val clipboard = DesktopPlatformClipboard { native }
        val transferable = StringSelection("复制内容")
        withContext(Dispatchers.Swing) { clipboard.setClipEntry(ClipEntry(transferable)) }
        assertSame(transferable, native.getContents(null))
        assertEquals("复制内容", native.getData(DataFlavor.stringFlavor))
        clipboard.setClipEntry(null)
        assertTrue(native.availableDataFlavors.isEmpty())
    }

    @Test
    fun `empty or busy native clipboard returns no entry`() = runBlocking {
        assertNull(DesktopPlatformClipboard { RecordingClipboard(emptyMap()) }.getClipEntry())
        val busy = object : Clipboard("busy") {
            override fun getAvailableDataFlavors(): Array<DataFlavor> = throw IllegalStateException("busy")
        }
        assertNull(DesktopPlatformClipboard { busy }.getClipEntry())
    }

    private fun image() = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)

    private open class RecordingClipboard(private val offered: Map<DataFlavor, Any>) : Clipboard("test") {
        val readFlavors = mutableListOf<DataFlavor>()

        override fun getContents(requestor: Any?): Transferable =
            throw AssertionError("X11 getContents 会急切读取所有格式")

        override fun getAvailableDataFlavors(): Array<DataFlavor> {
            assertFalse(SwingUtilities.isEventDispatchThread())
            return offered.keys.toTypedArray()
        }

        override fun getData(flavor: DataFlavor): Any {
            assertFalse("剪贴板数据读取应位于 IO 线程", SwingUtilities.isEventDispatchThread())
            readFlavors += flavor
            return offered[flavor] ?: throw UnsupportedFlavorException(flavor)
        }
    }
}

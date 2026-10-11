package me.rerere.rikkahub.linux

import android.app.Application
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.linux.di.desktopPlatformModule
import me.rerere.rikkahub.linux.desktop.DesktopBootstrap
import me.rerere.rikkahub.service.ChatService
import org.koin.dsl.koinApplication
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * 桌面端依赖图的装配冒烟测试：整棵 Koin 图（上游那四个 module + 桌面平台单例）能真正解析出
 * 关键单例，说明 Room / DataStore / ChatService 这条链在 JVM 上是通的。
 *
 * 上游加了新的 single 而桌面端缺平台实现时，这里会以 MissingDefinition 失败，
 * 比等到界面上点到那个功能才发现要早得多。
 */
class DesktopGraphTest {
    private lateinit var dataDir: File
    private lateinit var application: Application

    @Before
    fun setUp() {
        val root = File(System.getProperty("java.io.tmpdir"), "rikka-graph-${Random.nextLong()}")
        dataDir = File(root, "data").apply { mkdirs() }
        application = Application(dataDir, File(root, "cache").apply { mkdirs() })
    }

    @After
    fun tearDown() {
        runCatching { dataDir.parentFile?.deleteRecursively() }
    }

    @Test
    fun `resolves upstream chat pipeline on jvm`() {
        val koinApp = koinApplication {
            modules(desktopPlatformModule(application) + DesktopBootstrap.upstreamModules)
        }
        try {
            assertNotNull(koinApp.koin.get<SettingsStore>())
            assertNotNull(koinApp.koin.get<AppDatabase>())
            assertNotNull(koinApp.koin.get<ConversationRepository>())
            assertNotNull(koinApp.koin.get<ChatService>())
        } finally {
            koinApp.close()
        }
    }
}

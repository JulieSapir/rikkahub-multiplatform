package me.rerere.rikkahub.data.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Files

/** Loads the same simple tokenizer and query functions as the Android database. */
internal object DesktopSQLiteDriver {
    private val extensionPath by lazy {
        val directory = Files.createTempDirectory("rikka-sqlite-").toFile().apply { deleteOnExit() }
        val library = directory.resolve("libsimple.so").apply { deleteOnExit() }
        checkNotNull(javaClass.getResourceAsStream("/native/libsimple.so")) { "Missing SQLite simple extension" }
            .use { input -> library.outputStream().use(input::copyTo) }
        library.absolutePath
    }

    fun create(): BundledSQLiteDriver = BundledSQLiteDriver().apply {
        addExtension(extensionPath, "sqlite3_simple_init")
    }
}

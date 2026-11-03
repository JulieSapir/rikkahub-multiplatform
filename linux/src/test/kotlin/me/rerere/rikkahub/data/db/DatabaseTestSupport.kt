package me.rerere.rikkahub.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File

internal suspend fun <R> AppDatabase.useDatabase(block: suspend (AppDatabase) -> R): R =
    try {
        block(this)
    } finally {
        close()
    }

internal fun createUpstreamSchema(file: File, version: Int, seed: (SupportSQLiteDatabase) -> Unit = {}) {
    val schema = JsonInstant.parseToJsonElement(
        File("../app/schemas/me.rerere.rikkahub.data.db.AppDatabase/$version.json").readText(),
    ).jsonObject.getValue("database").jsonObject
    DesktopSQLiteDriver.create().open(file.absolutePath).use { connection ->
        val db = SupportSQLiteDatabase(connection)
        schema.getValue("entities").jsonArray.forEach { entity ->
            val definition = entity.jsonObject
            val name = definition.getValue("tableName").jsonPrimitive.content
            db.execSQL(definition.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
            definition["indices"]?.jsonArray?.forEach { index ->
                db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
            }
        }
        schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.execSQL("PRAGMA user_version = $version")
        seed(db)
    }
}

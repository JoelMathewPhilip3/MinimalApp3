package com.joel.minimallauncher.verse

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


data class BiblePassage(
    val reference: String,
    val text: String
)

data class BibleChapter(
    val bookName: String,
    val chapter: Int,
    val verses: List<BiblePassage>
) {
    val title: String get() = "$bookName $chapter"
}

object BibleRepository {
    private const val ASSET_NAME = "kjv.sqlite"
    private const val DATABASE_NAME = "kjv.sqlite"

    @Volatile private var database: SQLiteDatabase? = null

    suspend fun passage(context: Context, reference: String): BiblePassage = withContext(Dispatchers.IO) {
        val parsed = parseReference(reference)
        val db = openDatabase(context.applicationContext)
        db.query(
            "verses",
            arrayOf("text"),
            "book_name = ? AND chapter = ? AND verse = ?",
            arrayOf(parsed.book, parsed.chapter.toString(), parsed.verse.toString()),
            null, null, null,
            "1"
        ).use { cursor ->
            require(cursor.moveToFirst()) { "Bible reference not found: $reference" }
            BiblePassage(reference, cursor.getString(0))
        }
    }

    suspend fun passages(context: Context, references: List<String>): List<BiblePassage> =
        withContext(Dispatchers.IO) {
            references.map { reference ->
                val parsed = parseReference(reference)
                val db = openDatabase(context.applicationContext)
                db.query(
                    "verses",
                    arrayOf("text"),
                    "book_name = ? AND chapter = ? AND verse = ?",
                    arrayOf(parsed.book, parsed.chapter.toString(), parsed.verse.toString()),
                    null, null, null,
                    "1"
                ).use { cursor ->
                    require(cursor.moveToFirst()) { "Bible reference not found: $reference" }
                    BiblePassage(reference, cursor.getString(0))
                }
            }
        }

    suspend fun chapter(context: Context, reference: String): BibleChapter = withContext(Dispatchers.IO) {
        val parsed = parseReference(reference)
        val db = openDatabase(context.applicationContext)
        val verses = buildList {
            db.query(
                "verses",
                arrayOf("verse", "text"),
                "book_name = ? AND chapter = ?",
                arrayOf(parsed.book, parsed.chapter.toString()),
                null, null,
                "verse ASC"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val verse = cursor.getInt(0)
                    add(BiblePassage("${parsed.book} ${parsed.chapter}:$verse", cursor.getString(1)))
                }
            }
        }
        require(verses.isNotEmpty()) { "Bible chapter not found: ${parsed.book} ${parsed.chapter}" }
        BibleChapter(parsed.book, parsed.chapter, verses)
    }

    suspend fun verseCount(context: Context): Int = withContext(Dispatchers.IO) {
        val db = openDatabase(context.applicationContext)
        db.rawQuery("SELECT COUNT(*) FROM verses", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
    }

    private fun openDatabase(context: Context): SQLiteDatabase {
        database?.takeIf { it.isOpen }?.let { return it }
        return synchronized(this) {
            database?.takeIf { it.isOpen } ?: run {
                val destination = File(context.noBackupFilesDir, DATABASE_NAME)
                if (!destination.exists() || destination.length() == 0L) {
                    destination.parentFile?.mkdirs()
                    val temporary = File(destination.parentFile, "$DATABASE_NAME.tmp")
                    context.assets.open(ASSET_NAME).use { input ->
                        temporary.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (!temporary.renameTo(destination)) {
                        temporary.copyTo(destination, overwrite = true)
                        temporary.delete()
                    }
                }
                SQLiteDatabase.openDatabase(
                    destination.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                ).also { database = it }
            }
        }
    }

    private data class ParsedReference(val book: String, val chapter: Int, val verse: Int)

    private fun parseReference(reference: String): ParsedReference {
        val match = REFERENCE_REGEX.matchEntire(reference.trim())
            ?: error("Invalid Bible reference: $reference")
        return ParsedReference(
            book = match.groupValues[1],
            chapter = match.groupValues[2].toInt(),
            verse = match.groupValues[3].toInt()
        )
    }

    private val REFERENCE_REGEX = Regex("^(.+?)\\s+(\\d+):(\\d+)$")
}

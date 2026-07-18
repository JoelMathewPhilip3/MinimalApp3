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
    private const val ASSET_NAME = "bsb.sqlite"
    private const val DATABASE_NAME = "bsb.sqlite"
    private const val BOOKS_TABLE = "BSB_books"
    private const val VERSES_TABLE = "BSB_verses"

    @Volatile
    private var database: SQLiteDatabase? = null

    suspend fun passage(context: Context, reference: String): BiblePassage =
        withContext(Dispatchers.IO) {
            passageInternal(openDatabase(context.applicationContext), reference)
        }

    suspend fun passages(
        context: Context,
        references: List<String>
    ): List<BiblePassage> = withContext(Dispatchers.IO) {
        val db = openDatabase(context.applicationContext)
        references.map { reference -> passageInternal(db, reference) }
    }

    suspend fun chapter(context: Context, reference: String): BibleChapter =
        withContext(Dispatchers.IO) {
            val parsed = parseReference(reference)
            val databaseBookName = normalizeBookName(parsed.book)
            val db = openDatabase(context.applicationContext)
            val verses = buildList {
                db.rawQuery(
                    """
                    SELECT v.verse, v.text
                    FROM $VERSES_TABLE AS v
                    INNER JOIN $BOOKS_TABLE AS b ON b.id = v.book_id
                    WHERE b.name = ? COLLATE NOCASE
                      AND v.chapter = ?
                    ORDER BY v.verse ASC
                    """.trimIndent(),
                    arrayOf(databaseBookName, parsed.chapter.toString())
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val verseNumber = cursor.getInt(0)
                        add(
                            BiblePassage(
                                reference = "${parsed.book} ${parsed.chapter}:$verseNumber",
                                text = cursor.getString(1)
                            )
                        )
                    }
                }
            }

            require(verses.isNotEmpty()) {
                "Bible chapter not found: ${parsed.book} ${parsed.chapter}"
            }

            BibleChapter(
                bookName = parsed.book,
                chapter = parsed.chapter,
                verses = verses
            )
        }

    suspend fun verseCount(context: Context): Int = withContext(Dispatchers.IO) {
        val db = openDatabase(context.applicationContext)
        db.rawQuery("SELECT COUNT(*) FROM $VERSES_TABLE", null).use { cursor ->
            check(cursor.moveToFirst()) { "Unable to count BSB verses" }
            cursor.getInt(0)
        }
    }

    private fun passageInternal(
        db: SQLiteDatabase,
        reference: String
    ): BiblePassage {
        val parsed = parseReference(reference)
        val databaseBookName = normalizeBookName(parsed.book)

        db.rawQuery(
            """
            SELECT v.text
            FROM $VERSES_TABLE AS v
            INNER JOIN $BOOKS_TABLE AS b ON b.id = v.book_id
            WHERE b.name = ? COLLATE NOCASE
              AND v.chapter = ?
              AND v.verse = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(
                databaseBookName,
                parsed.chapter.toString(),
                parsed.verse.toString()
            )
        ).use { cursor ->
            require(cursor.moveToFirst()) {
                "Bible reference not found: $reference"
            }
            return BiblePassage(reference = reference, text = cursor.getString(0))
        }
    }

    private fun openDatabase(context: Context): SQLiteDatabase {
        database?.takeIf { it.isOpen && hasRequiredSchema(it) }?.let { return it }

        return synchronized(this) {
            database?.takeIf { it.isOpen && hasRequiredSchema(it) } ?: run {
                database?.close()
                database = null

                val destination = File(context.noBackupFilesDir, DATABASE_NAME)
                if (!destination.isFile || destination.length() == 0L) {
                    copyDatabaseFromAssets(context, destination)
                }

                var opened = openReadOnly(destination)

                // If an older incompatible database was copied by a previous build,
                // replace it with the database bundled in the current APK.
                if (!hasRequiredSchema(opened)) {
                    opened.close()
                    destination.delete()
                    copyDatabaseFromAssets(context, destination)
                    opened = openReadOnly(destination)
                }

                check(hasRequiredSchema(opened)) {
                    "Bundled BSB database is missing $BOOKS_TABLE or $VERSES_TABLE"
                }

                opened.also { database = it }
            }
        }
    }

    private fun copyDatabaseFromAssets(context: Context, destination: File) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "$DATABASE_NAME.tmp")
        temporary.delete()

        context.assets.open(ASSET_NAME).use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        }

        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
    }

    private fun openReadOnly(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )

    private fun hasRequiredSchema(db: SQLiteDatabase): Boolean {
        val required = setOf(BOOKS_TABLE, VERSES_TABLE)
        val found = mutableSetOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN (?, ?)",
            arrayOf(BOOKS_TABLE, VERSES_TABLE)
        ).use { cursor ->
            while (cursor.moveToNext()) found += cursor.getString(0)
        }
        return found == required
    }

    private data class ParsedReference(
        val book: String,
        val chapter: Int,
        val verse: Int
    )

    private fun parseReference(reference: String): ParsedReference {
        val match = REFERENCE_REGEX.matchEntire(reference.trim())
            ?: error("Invalid Bible reference: $reference")

        return ParsedReference(
            book = match.groupValues[1],
            chapter = match.groupValues[2].toInt(),
            verse = match.groupValues[3].toInt()
        )
    }

    private fun normalizeBookName(book: String): String = when (book.trim()) {
        "Psalm" -> "Psalms"
        "Song of Songs", "Canticles" -> "Song of Solomon"
        else -> book.trim()
    }

    private val REFERENCE_REGEX = Regex("^(.+?)\\s+(\\d+):(\\d+)$")
}

package com.joel.minimallauncher.verse

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate


data class DailyReading(
    val main: BiblePassage,
    val related: List<BiblePassage>
)

private data class CuratedDailyReading(
    val main: String,
    val related: List<String>,
    val themes: List<String>
)

object DailyVerseRepository {
    private const val ASSET_NAME = "curated_daily_verses.json"
    private const val MINIMUM_READING_COUNT = 365

    @Volatile
    private var cachedReadings: List<CuratedDailyReading>? = null

    suspend fun readingFor(context: Context, date: LocalDate): DailyReading {
        val readings = loadReadings(context)

        // The asset is deliberately ordered to alternate books and themes.
        // Using the calendar day directly keeps the same verse for the whole day,
        // gives predictable seven-day history, and avoids repeats until the full
        // curated list has been used.
        val index = Math.floorMod(date.toEpochDay(), readings.size.toLong()).toInt()
        val selected = readings[index]

        val passages = BibleRepository.passages(
            context,
            listOf(selected.main) + selected.related
        )

        return DailyReading(
            main = passages.first(),
            related = passages.drop(1)
        )
    }

    fun size(context: Context): Int = loadReadingsBlocking(context).size

    private suspend fun loadReadings(context: Context): List<CuratedDailyReading> =
        withContext(Dispatchers.IO) {
            loadReadingsBlocking(context.applicationContext)
        }

    private fun loadReadingsBlocking(context: Context): List<CuratedDailyReading> {
        cachedReadings?.let { return it }

        return synchronized(this) {
            cachedReadings ?: readAsset(context.applicationContext).also {
                cachedReadings = it
            }
        }
    }

    private fun readAsset(context: Context): List<CuratedDailyReading> {
        val json = context.assets.open(ASSET_NAME)
            .bufferedReader()
            .use { it.readText() }

        val root = JSONObject(json)
        val array = root.getJSONArray("readings")

        require(array.length() >= MINIMUM_READING_COUNT) {
            "Curated daily verse library is incomplete: expected at least " +
                "$MINIMUM_READING_COUNT readings, found ${array.length()}."
        }

        val readings = buildList(array.length()) {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val relatedArray = item.getJSONArray("related")
                val themesArray = item.optJSONArray("themes")

                require(relatedArray.length() == 3) {
                    "Curated reading $index must contain exactly three related references."
                }

                add(
                    CuratedDailyReading(
                        main = item.getString("main"),
                        related = List(3) { relatedArray.getString(it) },
                        themes = if (themesArray == null) {
                            emptyList()
                        } else {
                            List(themesArray.length()) { themesArray.getString(it) }
                        }
                    )
                )
            }
        }

        require(readings.map { it.main }.distinct().size == readings.size) {
            "Curated daily verse library contains duplicate main references."
        }

        return readings
    }
}

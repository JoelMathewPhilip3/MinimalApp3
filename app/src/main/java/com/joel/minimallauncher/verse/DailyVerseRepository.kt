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

private data class DailyReadingReferences(
    val main: String,
    val related: List<String>
)

object DailyVerseRepository {
    private const val ASSET_NAME = "daily_reading_refs.json"
    private const val EXPECTED_READING_COUNT = 1_095

    @Volatile private var cachedReferences: List<DailyReadingReferences>? = null

    suspend fun readingFor(context: Context, date: LocalDate): DailyReading {
        val references = loadReferences(context)
        val base = Math.floorMod(date.toEpochDay(), references.size.toLong()).toInt()
        // 437 is coprime with 1,095, so each plan is used once before the cycle repeats.
        val index = Math.floorMod(base * 437 + 211, references.size)
        val plan = references[index]
        val passages = BibleRepository.passages(context, listOf(plan.main) + plan.related)
        return DailyReading(main = passages.first(), related = passages.drop(1))
    }

    fun size(context: Context): Int = loadReferencesBlocking(context).size

    private suspend fun loadReferences(context: Context): List<DailyReadingReferences> =
        withContext(Dispatchers.IO) { loadReferencesBlocking(context) }

    private fun loadReferencesBlocking(context: Context): List<DailyReadingReferences> {
        cachedReferences?.let { return it }
        return synchronized(this) {
            cachedReferences ?: readAsset(context.applicationContext).also { cachedReferences = it }
        }
    }

    private fun readAsset(context: Context): List<DailyReadingReferences> {
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val array = root.getJSONArray("readings")
        require(array.length() == EXPECTED_READING_COUNT) {
            "Daily reading reference library is incomplete: expected $EXPECTED_READING_COUNT, found ${array.length()}."
        }
        return buildList(array.length()) {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val relatedArray = item.getJSONArray("related")
                require(relatedArray.length() == 3) { "Reading $index must contain three related references" }
                add(
                    DailyReadingReferences(
                        main = item.getString("main"),
                        related = List(3) { relatedArray.getString(it) }
                    )
                )
            }
        }
    }
}

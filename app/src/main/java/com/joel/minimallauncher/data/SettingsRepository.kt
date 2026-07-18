package com.joel.minimallauncher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.joel.minimallauncher.model.LauncherSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.dataStore by preferencesDataStore(name = "launcher_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val favorites = stringPreferencesKey("favorite_ids_personal_json")
        val minimalMode = booleanPreferencesKey("minimal_mode")
        val largeText = booleanPreferencesKey("large_text")
        val highContrast = booleanPreferencesKey("high_contrast")
        val reduceGestures = booleanPreferencesKey("reduce_gestures")
        val hapticFeedback = booleanPreferencesKey("haptic_feedback")
        val doubleTapLock = booleanPreferencesKey("double_tap_lock")
        val showMorningReading = booleanPreferencesKey("show_morning_reading")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data.map(::toSettings)

    private fun toSettings(prefs: Preferences) = LauncherSettings(
        favoriteIds = parseStringList(prefs[Keys.favorites]),
        minimalMode = prefs[Keys.minimalMode] ?: false,
        largeText = prefs[Keys.largeText] ?: false,
        highContrast = prefs[Keys.highContrast] ?: false,
        reduceGestures = prefs[Keys.reduceGestures] ?: false,
        hapticFeedback = prefs[Keys.hapticFeedback] ?: true,
        doubleTapLock = prefs[Keys.doubleTapLock] ?: false,
        showMorningReading = prefs[Keys.showMorningReading] ?: true,
        onboardingComplete = prefs[Keys.onboardingComplete] ?: false
    )

    suspend fun toggleFavorite(id: String) = context.dataStore.edit { prefs ->
        val current = parseStringList(prefs[Keys.favorites]).toMutableList()
        if (id in current) current.remove(id) else current.add(id)
        prefs[Keys.favorites] = JSONArray(current).toString()
    }

    suspend fun moveFavorite(id: String, direction: Int) = context.dataStore.edit { prefs ->
        val current = parseStringList(prefs[Keys.favorites]).toMutableList()
        val index = current.indexOf(id)
        if (index < 0) return@edit
        val target = (index + direction).coerceIn(0, current.lastIndex)
        if (target != index) {
            val item = current.removeAt(index)
            current.add(target, item)
            prefs[Keys.favorites] = JSONArray(current).toString()
        }
    }

    suspend fun setMinimalMode(value: Boolean) = context.dataStore.edit { it[Keys.minimalMode] = value }
    suspend fun setLargeText(value: Boolean) = context.dataStore.edit { it[Keys.largeText] = value }
    suspend fun setHighContrast(value: Boolean) = context.dataStore.edit { it[Keys.highContrast] = value }
    suspend fun setReduceGestures(value: Boolean) = context.dataStore.edit { it[Keys.reduceGestures] = value }
    suspend fun setHapticFeedback(value: Boolean) = context.dataStore.edit { it[Keys.hapticFeedback] = value }
    suspend fun setDoubleTapLock(value: Boolean) = context.dataStore.edit { it[Keys.doubleTapLock] = value }
    suspend fun setShowMorningReading(value: Boolean) = context.dataStore.edit { it[Keys.showMorningReading] = value }
    suspend fun completeOnboarding() = context.dataStore.edit { it[Keys.onboardingComplete] = true }

    private fun parseStringList(raw: String?): List<String> = runCatching {
        val array = JSONArray(raw ?: "[]")
        buildList { for (i in 0 until array.length()) add(array.optString(i)) }
    }.getOrDefault(emptyList())
}

package com.joel.minimallauncher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joel.minimallauncher.data.AppRepository
import com.joel.minimallauncher.data.SettingsRepository
import com.joel.minimallauncher.model.AppEntry
import com.joel.minimallauncher.model.LauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val appRepository = AppRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val apps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val query = MutableStateFlow("")
    private val isLoading = MutableStateFlow(false)
    private val appsLoaded = MutableStateFlow(false)

    private data class CoreState(val apps: List<AppEntry>, val settings: LauncherSettings, val query: String)
    private val coreState = combine(apps, settingsRepository.settings, query, ::CoreState)

    val uiState: StateFlow<LauncherUiState> = combine(coreState, isLoading, appsLoaded) { core, loading, loaded ->
        val byId = core.apps.associateBy { it.id }
        val favorites = core.settings.favoriteIds.mapNotNull(byId::get)
        val results = core.apps.filter {
            core.query.isBlank() || it.label.contains(core.query, true) || it.packageName.contains(core.query, true)
        }
        LauncherUiState(core.apps, favorites, results, core.settings, core.query, loading, loaded)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    fun loadApps(forceRefresh: Boolean = false) = viewModelScope.launch {
        isLoading.value = true
        apps.value = withContext(Dispatchers.Default) { appRepository.loadLaunchableApps(forceRefresh) }
        appsLoaded.value = true
        isLoading.value = false
    }

    fun invalidateApps() { appRepository.invalidateCache(); appsLoaded.value = false }
    fun setQuery(value: String) { query.value = value }
    fun clearQuery() { query.value = "" }
    fun launch(app: AppEntry): Boolean = appRepository.launch(app)
    fun openAppInfo(app: AppEntry): Boolean = appRepository.openAppInfo(app)
    fun toggleFavorite(app: AppEntry) = viewModelScope.launch { settingsRepository.toggleFavorite(app.id) }
    fun moveFavorite(app: AppEntry, direction: Int) = viewModelScope.launch { settingsRepository.moveFavorite(app.id, direction) }
    fun setMinimalMode(value: Boolean) = viewModelScope.launch { settingsRepository.setMinimalMode(value) }
    fun setLargeText(value: Boolean) = viewModelScope.launch { settingsRepository.setLargeText(value) }
    fun setHighContrast(value: Boolean) = viewModelScope.launch { settingsRepository.setHighContrast(value) }
    fun setReduceGestures(value: Boolean) = viewModelScope.launch { settingsRepository.setReduceGestures(value) }
    fun setHapticFeedback(value: Boolean) = viewModelScope.launch { settingsRepository.setHapticFeedback(value) }
    fun setDoubleTapLock(value: Boolean) = viewModelScope.launch { settingsRepository.setDoubleTapLock(value) }
    fun setShowMorningReading(value: Boolean) = viewModelScope.launch { settingsRepository.setShowMorningReading(value) }
    fun completeOnboarding() = viewModelScope.launch { settingsRepository.completeOnboarding() }
}

data class LauncherUiState(
    val allApps: List<AppEntry> = emptyList(),
    val favorites: List<AppEntry> = emptyList(),
    val searchResults: List<AppEntry> = emptyList(),
    val settings: LauncherSettings = LauncherSettings(),
    val query: String = "",
    val isLoading: Boolean = false,
    val appsLoaded: Boolean = false
)

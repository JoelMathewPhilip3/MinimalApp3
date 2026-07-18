package com.joel.minimallauncher.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joel.minimallauncher.LauncherDeviceAdminReceiver
import com.joel.minimallauncher.model.AppEntry
import com.joel.minimallauncher.ui.theme.JoelMinimalTheme
import com.joel.minimallauncher.verse.BibleChapter
import com.joel.minimallauncher.verse.BibleRepository
import com.joel.minimallauncher.verse.DailyReading
import com.joel.minimallauncher.verse.DailyVerseRepository
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private enum class Screen { DAILY_VERSE, FAVORITES_HOME, CHAPTER, APPS, SETTINGS, FAVORITES, ACCESSIBILITY }

@Composable
fun LauncherApp(viewModel: LauncherViewModel, homeRequestKey: Int) {
    val state by viewModel.uiState.collectAsState()
    JoelMinimalTheme(
        highContrast = state.settings.highContrast,
        largeText = state.settings.largeText
    ) {
        LauncherContent(viewModel, state, homeRequestKey)
    }
}

@Composable
private fun LauncherContent(viewModel: LauncherViewModel, state: LauncherUiState, homeRequestKey: Int) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var screen by remember { mutableStateOf(Screen.DAILY_VERSE) }
    var selectedApp by remember { mutableStateOf<AppEntry?>(null) }
    var failedApp by remember { mutableStateOf<AppEntry?>(null) }
    var selectedChapterReference by remember { mutableStateOf<String?>(null) }

    val dpm = remember { context.getSystemService(DevicePolicyManager::class.java) }
    val adminComponent = remember { ComponentName(context, LauncherDeviceAdminReceiver::class.java) }

    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.setDoubleTapLock(dpm.isAdminActive(adminComponent))
    }

    LaunchedEffect(Unit) { viewModel.loadApps() }
    LaunchedEffect(homeRequestKey) {
        screen = Screen.DAILY_VERSE
        viewModel.clearQuery()
        selectedApp = null
        failedApp = null
        selectedChapterReference = null
    }

    BackHandler(enabled = screen != Screen.DAILY_VERSE || state.query.isNotBlank()) {
        if (state.query.isNotBlank()) viewModel.clearQuery() else screen = Screen.DAILY_VERSE
    }

    fun lockScreen() {
        if (!state.settings.doubleTapLock) return
        if (dpm.isAdminActive(adminComponent)) {
            runCatching { dpm.lockNow() }.onFailure { /* no crash */ }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).statusBarsPadding().navigationBarsPadding()) {
            when (screen) {
                Screen.DAILY_VERSE -> DailyVerseScreen(
                    state = state,
                    showMorningReading = state.settings.showMorningReading,
                    onOpenFavorites = { screen = Screen.FAVORITES_HOME },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onOpenChapter = { reference ->
                        selectedChapterReference = reference
                        screen = Screen.CHAPTER
                    },
                    onDoubleTap = ::lockScreen
                )
                Screen.CHAPTER -> ChapterScreen(
                    reference = selectedChapterReference,
                    onBack = { screen = Screen.DAILY_VERSE }
                )
                Screen.FAVORITES_HOME -> HomeScreen(
                    state = state,
                    onOpenApp = { if (!viewModel.launch(it)) failedApp = it },
                    onLongPressApp = { selectedApp = it },
                    onOpenApps = { viewModel.loadApps(); screen = Screen.APPS },
                    onOpenVerse = { screen = Screen.DAILY_VERSE },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onDoubleTap = ::lockScreen
                )
                Screen.APPS -> AppListScreen(
                    title = "All apps",
                    apps = state.searchResults,
                    query = state.query,
                    loading = state.isLoading,
                    onQuery = viewModel::setQuery,
                    onRefresh = { viewModel.loadApps(true) },
                    onBack = { viewModel.clearQuery(); screen = Screen.FAVORITES_HOME },
                    onClick = { if (!viewModel.launch(it)) failedApp = it },
                    onLongClick = { selectedApp = it }
                )
                Screen.SETTINGS -> SettingsScreen(
                    state = state,
                    adminActive = dpm.isAdminActive(adminComponent),
                    onBack = { screen = Screen.DAILY_VERSE },
                    onFavorites = { screen = Screen.FAVORITES },
                    onAccessibility = { screen = Screen.ACCESSIBILITY },
                    onMinimal = viewModel::setMinimalMode,
                    onShowMorningReading = viewModel::setShowMorningReading,
                    onHomeSettings = { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) },
                    onDoubleTapLock = { enabled ->
                        if (!enabled) viewModel.setDoubleTapLock(false)
                        else if (dpm.isAdminActive(adminComponent)) viewModel.setDoubleTapLock(true)
                        else {
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable double-tap-to-lock from the launcher home screen.")
                            }
                            adminLauncher.launch(intent)
                        }
                    }
                )
                Screen.FAVORITES -> FavoriteScreen(
                    state = state,
                    onBack = { viewModel.clearQuery(); screen = Screen.SETTINGS },
                    onQuery = viewModel::setQuery,
                    onToggle = viewModel::toggleFavorite,
                    onMove = viewModel::moveFavorite
                )
                Screen.ACCESSIBILITY -> AccessibilityScreen(
                    state = state,
                    onBack = { screen = Screen.SETTINGS },
                    onLargeText = viewModel::setLargeText,
                    onHighContrast = viewModel::setHighContrast,
                    onReduceGestures = viewModel::setReduceGestures,
                    onHaptics = viewModel::setHapticFeedback,
                    onOpenSystemAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
            }
        }
    }

    if (!state.settings.onboardingComplete && state.appsLoaded) {
        OnboardingDialog(
            onChooseFavorites = { viewModel.completeOnboarding(); screen = Screen.FAVORITES },
            onSkip = viewModel::completeOnboarding
        )
    }

    selectedApp?.let { app ->
        AppActionDialog(
            app = app,
            isFavorite = app.id in state.settings.favoriteIds,
            onDismiss = { selectedApp = null },
            onOpen = { selectedApp = null; if (!viewModel.launch(app)) failedApp = app },
            onFavorite = { viewModel.toggleFavorite(app); selectedApp = null },
            onInfo = { viewModel.openAppInfo(app); selectedApp = null }
        )
    }

    failedApp?.let { app ->
        AlertDialog(
            onDismissRequest = { failedApp = null },
            title = { Text("Could not open ${app.label}") },
            text = { Text("The app may have changed or been removed. Refresh the app list and try again.") },
            confirmButton = { TextButton(onClick = { viewModel.loadApps(true); failedApp = null }) { Text("Refresh") } },
            dismissButton = { TextButton(onClick = { failedApp = null }) { Text("Close") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    state: LauncherUiState,
    onOpenApp: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onOpenApps: () -> Unit,
    onOpenVerse: () -> Unit,
    onOpenSettings: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val context = LocalContext.current
    var totalY by remember { mutableFloatStateOf(0f) }
    var totalX by remember { mutableFloatStateOf(0f) }
    val gestureModifier = if (state.settings.reduceGestures) Modifier else Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { totalX = 0f; totalY = 0f },
            onDrag = { change, amount -> change.consume(); totalX += amount.x; totalY += amount.y },
            onDragEnd = {
                when {
                    abs(totalY) > abs(totalX) && totalY < -140f -> onOpenApps()
                    abs(totalX) > abs(totalY) && totalX > 140f -> onOpenVerse()
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(gestureModifier)
            .pointerInput(state.settings.doubleTapLock) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            }
            .padding(horizontal = 28.dp, vertical = 18.dp)
    ) {
        MinuteClock()
        Spacer(Modifier.height(34.dp))

        if (state.isLoading && !state.appsLoaded) CircularProgressIndicator()
        else if (state.favorites.isEmpty()) {
            Text("A quieter phone.", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Text("Long-press an app in search to add it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.favorites.take(if (state.settings.minimalMode) 6 else 12).forEach { app ->
                AppTextRow(app, { onOpenApp(app) }, {
                    if (state.settings.hapticFeedback) vibrate(context)
                    onLongPressApp(app)
                })
            }
        }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            if (state.settings.minimalMode) {
                HoldToOpenApps(
                    onComplete = {
                        if (state.settings.hapticFeedback) vibrate(context)
                        onOpenApps()
                    }
                )
            } else {
                TextButton(onClick = onOpenApps, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Search, null); Text("  Search apps")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenVerse, modifier = Modifier.semantics { contentDescription = "Open daily Bible verse" }) {
                    Icon(Icons.Outlined.MenuBook, "Daily Bible verse")
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.semantics { contentDescription = "Open launcher settings" }) {
                    Icon(Icons.Outlined.Settings, "Settings")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DailyVerseScreen(
    state: LauncherUiState,
    showMorningReading: Boolean,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChapter: (String) -> Unit,
    onDoubleTap: () -> Unit
) {
    var daysAgo by remember { mutableStateOf(0) }
    val date = LocalDate.now().minusDays(daysAgo.toLong())
    val context = LocalContext.current
    val readingState by produceState<DailyReading?>(initialValue = null, date) {
        value = runCatching { DailyVerseRepository.readingFor(context, date) }.getOrNull()
    }
    var totalX by remember { mutableFloatStateOf(0f) }

    val swipeModifier = if (state.settings.reduceGestures) Modifier else Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { totalX = 0f },
            onDrag = { change, amount -> change.consume(); totalX += amount.x },
            onDragEnd = { if (totalX < -140f) onOpenFavorites() }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(swipeModifier)
            .pointerInput(state.settings.doubleTapLock) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            }
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) { MinuteClock() }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.semantics { contentDescription = "Open launcher settings" }
                ) { Icon(Icons.Outlined.Settings, "Settings") }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                when (daysAgo) {
                    0 -> "TODAY'S VERSE"
                    1 -> "YESTERDAY"
                    else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())).uppercase()
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(12.dp))
        }

        val reading = readingState
        if (reading == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOpenChapter(reading.main.reference) },
                            onLongClick = { onOpenChapter(reading.main.reference) }
                        ),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("“${reading.main.text}”", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        Text(reading.main.reference, style = MaterialTheme.typography.titleMedium)
                        Text("Tap to read the full chapter", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (showMorningReading) {
                item {
                    Spacer(Modifier.height(30.dp)); Divider(); Spacer(Modifier.height(20.dp))
                    Text("MORNING READING", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(reading.related, key = { it.reference }) { passage ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpenChapter(passage.reference) },
                                onLongClick = { onOpenChapter(passage.reference) }
                            ),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(Modifier.padding(top = 18.dp, bottom = 4.dp)) {
                            Text(passage.reference, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(passage.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(28.dp)); Divider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { if (daysAgo < 6) daysAgo++ }, enabled = daysAgo < 6, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.ChevronLeft, "Previous day"); Text("Previous")
                }
                Text("${daysAgo + 1} of 7", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { if (daysAgo > 0) daysAgo-- }, enabled = daysAgo > 0, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (daysAgo == 1) "Today" else "Next"); Icon(Icons.Outlined.ChevronRight, "Next day")
                }
            }
            TextButton(onClick = onOpenFavorites, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Open favourites  "); Icon(Icons.Outlined.ChevronRight, "Open favourites")
            }
            Text(
                "Swipe left for favourites · Full King James Version offline · ${DailyVerseRepository.size(context)}-day reading-plan cycle",
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ChapterScreen(reference: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val chapterState by produceState<BibleChapter?>(initialValue = null, reference) {
        value = reference?.let { runCatching { BibleRepository.chapter(context, it) }.getOrNull() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Header(chapterState?.title ?: "Bible chapter", onBack)
        val chapter = chapterState
        if (chapter == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn {
                items(chapter.verses, key = { it.reference }) { passage ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            passage.reference.substringAfterLast(':'),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp, top = 3.dp)
                        )
                        Text(passage.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "King James Version · Full chapter stored offline",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}
@Composable
private fun HoldToOpenApps(onComplete: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 1200 else 150),
        label = "holdProgress"
    )
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(1200)
            if (pressed && !completed) {
                completed = true
                onComplete()
            }
        } else completed = false
    }
    Surface(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Hold for all apps"
                onClick(label = "Hold to open all apps") { false }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                })
            },
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(if (pressed) "Keep holding…" else "Hold for all apps")
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MinuteClock() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay((60_000L - System.currentTimeMillis() % 60_000L).coerceAtLeast(250L))
        }
    }
    Text(now.format(DateTimeFormatter.ofPattern("h:mm a")), style = MaterialTheme.typography.displayLarge)
    Text(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTextRow(app: AppEntry, onClick: () -> Unit, onLongClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .combinedClickable(
                onClickLabel = "Open ${app.label}",
                onLongClickLabel = "App options for ${app.label}",
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    BasicTextField(
        value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, "Search")
                Spacer(Modifier.padding(4.dp))
                Box(Modifier.weight(1f)) { if (query.isBlank()) Text("Search apps", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                if (query.isNotBlank()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "Clear search") }
            }
        }
    )
}

@Composable
private fun AppListScreen(title: String, apps: List<AppEntry>, query: String, loading: Boolean, onQuery: (String) -> Unit, onRefresh: () -> Unit, onBack: () -> Unit, onClick: (AppEntry) -> Unit, onLongClick: (AppEntry) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Header(title, onBack) { IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Refresh apps") } }
        SearchField(query, onQuery); Divider(Modifier.padding(vertical = 8.dp))
        if (loading) CircularProgressIndicator(Modifier.padding(16.dp))
        LazyColumn { items(apps, key = { it.id }) { app -> AppTextRow(app, { onClick(app) }, { onLongClick(app) }) } }
    }
}

@Composable
private fun FavoriteScreen(state: LauncherUiState, onBack: () -> Unit, onQuery: (String) -> Unit, onToggle: (AppEntry) -> Unit, onMove: (AppEntry, Int) -> Unit) {
    val apps = filterApps(state.allApps, state.query)
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Header("Favourites", onBack)
        Text("Tap to add or remove. Use arrows to reorder selected apps.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp)); SearchField(state.query, onQuery); Divider(Modifier.padding(vertical = 8.dp))
        LazyColumn {
            items(apps, key = { it.id }) { app ->
                val selected = app.id in state.settings.favoriteIds
                AppTextRow(app, { onToggle(app) }, {}) {
                    if (selected) {
                        IconButton(onClick = { onMove(app, -1) }) { Icon(Icons.Outlined.ArrowUpward, "Move ${app.label} up") }
                        IconButton(onClick = { onMove(app, 1) }) { Icon(Icons.Outlined.ArrowDownward, "Move ${app.label} down") }
                        Icon(Icons.Outlined.Check, "Selected as favourite")
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); trailing?.invoke()
    }
}

@Composable
private fun SettingsScreen(state: LauncherUiState, adminActive: Boolean, onBack: () -> Unit, onFavorites: () -> Unit, onAccessibility: () -> Unit, onMinimal: (Boolean) -> Unit, onShowMorningReading: (Boolean) -> Unit, onHomeSettings: () -> Unit, onDoubleTapLock: (Boolean) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        item { Header("Settings", onBack) }
        item { Section("Home") }
        item { SettingRow("Favourites", "${state.settings.favoriteIds.size} selected", onFavorites) }
        item { ToggleRow("Minimal mode", "Shows at most six favourites and uses a deliberate hold to open all apps.", state.settings.minimalMode, onMinimal) }
        item { Section("Daily Verse") }
        item { ToggleRow("Morning reading", "Show three related KJV passages below the verse of the day.", state.settings.showMorningReading, onShowMorningReading) }
        item { Text("Swipe left from Home or tap the Bible icon to open today's reading. The verse changes once per calendar day without background work.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp)) }
        item { Section("Screen") }
        item { ToggleRow("Double-tap to lock", if (adminActive) "Double-tap an empty area on Home to turn off and lock the screen." else "Requires optional Device Administrator access.", state.settings.doubleTapLock && adminActive, onDoubleTapLock) }
        item { Section("Accessibility") }
        item { SettingRow("Accessibility options", "Text, contrast, gesture alternatives, haptics, and accessibility guidance.", onAccessibility) }
        item { Section("System") }
        item { SettingRow("Choose default home app", "Open Android home-app settings.", onHomeSettings) }
        item { Spacer(Modifier.height(24.dp)); Text("Pressing the system Home button always returns to this Home screen.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AccessibilityScreen(state: LauncherUiState, onBack: () -> Unit, onLargeText: (Boolean) -> Unit, onHighContrast: (Boolean) -> Unit, onReduceGestures: (Boolean) -> Unit, onHaptics: (Boolean) -> Unit, onOpenSystemAccessibility: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        item { Header("Accessibility", onBack) }
        item { ToggleRow("Larger launcher text", "Adds extra launcher text size while still respecting Android font scaling.", state.settings.largeText, onLargeText) }
        item { ToggleRow("High contrast", "Uses pure black, white, and stronger outlines.", state.settings.highContrast, onHighContrast) }
        item { ToggleRow("Reduce gesture dependence", "Disables swipe-up so every action remains available through visible buttons.", state.settings.reduceGestures, onReduceGestures) }
        item { ToggleRow("Haptic feedback", "Vibrates briefly after long-press actions and completed hold actions.", state.settings.hapticFeedback, onHaptics) }
        item { Section("Built in") }
        item { AccessibilityInfo("48 dp minimum touch targets", "Interactive rows and controls use Android's recommended minimum touch size.") }
        item { AccessibilityInfo("Screen-reader labels", "Icons, app actions, selected states, and navigation controls include descriptive labels.") }
        item { AccessibilityInfo("System font scaling", "Launcher text follows Android's font-size accessibility setting.") }
        item { AccessibilityInfo("Button alternatives", "App search, settings, favourites, and navigation do not require gestures.") }
        item { AccessibilityInfo("Selected-state announcements", "Favourite selections include an announced selected description.") }
        item { SettingRow("Android accessibility settings", "Open TalkBack, font size, magnification, color correction, and other system options.", onOpenSystemAccessibility) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).combinedClickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        trailing?.invoke()
    }
}

@Composable private fun AccessibilityInfo(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Info, null, modifier = Modifier.padding(top = 2.dp)); Spacer(Modifier.padding(5.dp))
        Column { Text(title); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun Section(title: String) { Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)) }

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun OnboardingDialog(onChooseFavorites: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Set up your quiet home screen") },
        text = { Text("Choose a few essential apps for your Home screen. You can change them any time.") },
        confirmButton = { TextButton(onClick = onChooseFavorites) { Text("Choose favourites") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("Skip") } }
    )
}

@Composable
private fun AppActionDialog(app: AppEntry, isFavorite: Boolean, onDismiss: () -> Unit, onOpen: () -> Unit, onFavorite: () -> Unit, onInfo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = { Column {
            TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open") }
            TextButton(onClick = onFavorite, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (isFavorite) "Remove from favourites" else "Add to favourites") }
            TextButton(onClick = onInfo, modifier = Modifier.heightIn(min = 48.dp)) { Text("App information") }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun filterApps(apps: List<AppEntry>, query: String): List<AppEntry> = if (query.isBlank()) apps else apps.filter {
    it.label.contains(query, true) || it.packageName.contains(query, true)
}

private fun vibrate(context: Context) {
    runCatching {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

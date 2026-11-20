package com.berkbb.otaghome

import android.app.SearchManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.TimeZone

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_BG_ROTATION = "bg_rotation"
        private const val KEY_BG_ROTATION_DAY = "bg_rotation_day"
        private const val KEY_NOTICE_SECONDS = "notice_seconds"
        private const val KEY_VEIL = "veil"
    }

    private val prefs by lazy { getSharedPreferences(LauncherViewModel.PREFS_NAME, Context.MODE_PRIVATE) }

    /** Started as the Home app, by the system or by the Home override (HomeOverrideService). */
    private var openedAsHome by mutableStateOf(false)

    private fun todayEpochDay(): Long {
        val now = System.currentTimeMillis()
        return PresetRotator.localEpochDay(now, TimeZone.getDefault().getOffset(now))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedAsHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true
        val initialTheme = ThemeMode.fromName(prefs.getString(KEY_THEME_MODE, null))
        val initialRotation = PresetRotation.fromName(prefs.getString(KEY_BG_ROTATION, null))
        val today = todayEpochDay()
        val rawBackground = BackgroundSpec.decode(prefs.getString(KEY_BACKGROUND, null))
        val storedDay = prefs.getLong(KEY_BG_ROTATION_DAY, -1L)
        val initialBackground = if (rawBackground is BackgroundSpec.Preset) {
            when {
                initialRotation == PresetRotation.ON_LAUNCH -> {
                    val rotated = BackgroundSpec.Preset(PresetRotator.next(rawBackground.image))
                    prefs.edit {
                        putString(KEY_BACKGROUND, rotated.encode())
                        putLong(KEY_BG_ROTATION_DAY, today)
                    }
                    rotated
                }
                initialRotation == PresetRotation.DAILY && PresetRotator.shouldRotateDaily(storedDay, today) -> {
                    val rotated = BackgroundSpec.Preset(PresetRotator.next(rawBackground.image))
                    prefs.edit {
                        putString(KEY_BACKGROUND, rotated.encode())
                        putLong(KEY_BG_ROTATION_DAY, today)
                    }
                    rotated
                }
                initialRotation == PresetRotation.DAILY && storedDay < 0L -> {
                    prefs.edit { putLong(KEY_BG_ROTATION_DAY, today) }
                    rawBackground
                }
                else -> rawBackground
            }
        } else rawBackground
        val initialVeil = VeilLevel.fromName(prefs.getString(KEY_VEIL, null))
        val initialNoticeSeconds = NoticeTimer.fromStored(prefs.getInt(KEY_NOTICE_SECONDS, NoticeTimer.DEFAULT))
        // Every colour the user picked, by preferences key (see ColorRole.prefKey)
        val initialColors = ColorRole.entries.flatMap { listOf(it.prefKey(true), it.prefKey(false)) }.distinct()
            .mapNotNull { key -> prefs.getString(key, null)?.let(HexColor::parse)?.let { key to it } }.toMap()

        // The UI language is the TV's system language, as in every launcher
        setContent {
            var themeMode by remember { mutableStateOf(initialTheme) }
            var background by remember { mutableStateOf(initialBackground) }
            var bgRotation by remember { mutableStateOf(initialRotation) }
            var storedColors by remember { mutableStateOf(initialColors) }
            var noticeSeconds by remember { mutableIntStateOf(initialNoticeSeconds) }
            var veil by remember { mutableStateOf(initialVeil) }
            val scope = rememberCoroutineScope()
            suspend fun rotateLocalImageIfDue(current: BackgroundSpec, rotation: PresetRotation) {
                if (current !is BackgroundSpec.Image || rotation == PresetRotation.OFF) return
                val currentDay = todayEpochDay()
                val lastDay = prefs.getLong(KEY_BG_ROTATION_DAY, -1L)
                val due = when (rotation) {
                    PresetRotation.ON_LAUNCH -> true
                    PresetRotation.DAILY -> PresetRotator.shouldRotateDaily(lastDay, currentDay)
                    PresetRotation.OFF -> false
                }
                if (!due) {
                    if (rotation == PresetRotation.DAILY && lastDay < 0L) {
                        prefs.edit { putLong(KEY_BG_ROTATION_DAY, currentDay) }
                    }
                    return
                }
                val images = withContext(Dispatchers.IO) {
                    DeviceImageCatalog.scanDirectory(localPicturesDir(), "", Build.VERSION.SDK_INT)
                }
                val nextUri = DeviceImageCatalog.nextImageUri(current.uri, images) ?: return
                val nextBg = BackgroundSpec.Image(nextUri)
                releaseImagePermission(background, nextBg)
                background = nextBg
                prefs.edit {
                    putString(KEY_BACKGROUND, nextBg.encode())
                    putLong(KEY_BG_ROTATION_DAY, currentDay)
                }
            }
            LaunchedEffect(Unit) {
                rotateLocalImageIfDue(initialBackground, initialRotation)
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, bgRotation, background) {
                var initialStart = true
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        val wasInitial = initialStart
                        initialStart = false
                        if (bgRotation == PresetRotation.DAILY) {
                            when (val current = background) {
                                is BackgroundSpec.Preset -> {
                                    val currentDay = todayEpochDay()
                                    val lastDay = prefs.getLong(KEY_BG_ROTATION_DAY, -1L)
                                    if (PresetRotator.shouldRotateDaily(lastDay, currentDay)) {
                                        val nextBg = BackgroundSpec.Preset(PresetRotator.next(current.image))
                                        background = nextBg
                                        prefs.edit {
                                            putString(KEY_BACKGROUND, nextBg.encode())
                                            putLong(KEY_BG_ROTATION_DAY, currentDay)
                                        }
                                    }
                                }
                                is BackgroundSpec.Image -> if (!wasInitial) {
                                    scope.launch { rotateLocalImageIfDue(current, PresetRotation.DAILY) }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            // Average colour of the user's own picture, reported once it has loaded
            var pictureAverage by remember { mutableStateOf<Int?>(null) }
            val backgroundIsDark = background.isDark(pictureAverage)
            val isDark = themeMode.isDark(isSystemInDarkTheme(), backgroundIsDark)
            // The colours that apply now: the shared accent and the current theme's own
            val customColors = ColorRole.entries.mapNotNull { role -> storedColors[role.prefKey(isDark)]?.let { role to it } }.toMap()
            val palette = Palette.of(themeMode, isSystemInDarkTheme(), customColors, backgroundIsDark)

            // Opened as the Home app, Back has nowhere to go; opened from another launcher it closes
            BackHandler(enabled = openedAsHome) { }

            CompositionLocalProvider(LocalPalette provides palette) {
                LauncherScreen(
                    viewModel = viewModel,
                    themeMode = themeMode,
                    background = background,
                    bgRotation = bgRotation,
                    onCycleBgRotation = {
                        bgRotation = bgRotation.next()
                        prefs.edit {
                            putString(KEY_BG_ROTATION, bgRotation.name)
                            if (bgRotation == PresetRotation.DAILY) putLong(KEY_BG_ROTATION_DAY, todayEpochDay())
                        }
                    },
                    onPictureAverage = { pictureAverage = it },
                    // Theme, background, colours and the rest of the screen's settings are read in onCreate
                    onBackupRestored = { recreate() },
                    onResetAll = {
                        releaseImagePermission(background, BackgroundSpec.Default)
                        viewModel.resetAllSettings()
                        recreate()
                    },
                    veil = veil,
                    onCycleVeil = {
                        veil = veil.next()
                        prefs.edit { putString(KEY_VEIL, veil.name) }
                    },
                    noticeSeconds = noticeSeconds,
                    onNoticeSecondsChange = { seconds ->
                        noticeSeconds = NoticeTimer.fromStored(seconds)
                        prefs.edit { putInt(KEY_NOTICE_SECONDS, noticeSeconds) }
                    },
                    customColors = customColors,
                    onColorChange = { role, color ->
                        val key = role.prefKey(isDark)
                        storedColors = if (color == null) storedColors - key else storedColors + (key to color)
                        prefs.edit { if (color == null) remove(key) else putString(key, HexColor.format(color)) }
                    },
                    onResetColors = {
                        val keys = ColorRole.entries.map { it.prefKey(isDark) }
                        storedColors = storedColors - keys.toSet()
                        prefs.edit { keys.forEach(::remove) }
                    },
                    onToggleTheme = {
                        themeMode = themeMode.next()
                        prefs.edit { putString(KEY_THEME_MODE, themeMode.name) }
                    },
                    onBackgroundChange = { spec ->
                        releaseImagePermission(background, spec)
                        background = spec
                        prefs.edit {
                            putString(KEY_BACKGROUND, spec.encode())
                            if (spec is BackgroundSpec.Preset || spec is BackgroundSpec.Image) {
                                putLong(KEY_BG_ROTATION_DAY, todayEpochDay())
                            }
                        }
                    }
                )
            }
        }
    }

    /** A picture that is no longer the background does not need its read permission or temp copy any more. */
    private fun releaseImagePermission(old: BackgroundSpec, new: BackgroundSpec) {
        if (old is BackgroundSpec.Image && old != new) {
            val parsed = Uri.parse(old.uri)
            when (parsed.scheme) {
                ContentResolver.SCHEME_CONTENT -> runCatching {
                    contentResolver.releasePersistableUriPermission(parsed, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ContentResolver.SCHEME_FILE -> cleanupImportedBackground(parsed.path)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setScreenActive(true)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onStop() {
        viewModel.setScreenActive(false)
        super.onStop()
    }

    // Opened from the apps list first, OtagHome becomes the Home app once Home brings it back
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) openedAsHome = true
    }
}



/**
 * Google voice search the way the stock TV launcher starts it (the assistant intent), falling back
 * to global search on TVs without an assistant. The search screen also offers a keyboard.
 */
internal fun Context.voiceSearchIntent(): Intent {
    val assist = Intent(Intent.ACTION_ASSIST).putExtra(Intent.EXTRA_ASSIST_INPUT_HINT_KEYBOARD, false)
    return if (assist.resolveActivity(packageManager) != null) assist else Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)
}

/**
 * Google search with the keyboard, for remotes without a microphone. Global search is the fallback,
 * though some TVs start it listening.
 */
internal fun Context.keyboardSearchIntent(): Intent {
    val assist = Intent(Intent.ACTION_ASSIST).putExtra(Intent.EXTRA_ASSIST_INPUT_HINT_KEYBOARD, true)
    return if (assist.resolveActivity(packageManager) != null) assist else Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)
}

/**
 * The TV's input picker (HDMI, tuner…), as the stock launcher's Inputs button opens it. Google's
 * panel is preferred when the TV has it, otherwise the manufacturer's.
 */
internal fun Context.inputsIntent(): Intent {
    val intent = Intent(ACTION_VIEW_INPUTS)
    val panels = runCatching { packageManager.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
        .map { it.activityInfo }
        .filter { it.packageName != packageName }
    val panel = panels.firstOrNull { it.packageName == GOOGLE_TV_LAUNCHER } ?: panels.firstOrNull()
    if (panel != null) intent.setClassName(panel.packageName, panel.name)
    return intent
}

/**
 * The Wi-Fi network list. Most TVs show it for the Wi-Fi settings intent, but some (Philips) send
 * that intent to their own connection wizard; when the Wi-Fi picker lives in a different app than
 * the settings handler, the picker is opened instead.
 */
internal fun Context.wifiSettingsIntent(): Intent {
    val settings = Intent(Settings.ACTION_WIFI_SETTINGS)
    val picker = Intent(ACTION_SETUP_WIFI_NETWORK)
    val settingsApp = runCatching { settings.resolveActivity(packageManager)?.packageName }.getOrNull()
    val pickerApp = runCatching { picker.resolveActivity(packageManager)?.packageName }.getOrNull()
    return if (prefersWifiPicker(settingsApp, pickerApp)) picker else settings
}

/** The Wi-Fi picker is used when the TV has one and the settings intent goes to another app. */
internal fun prefersWifiPicker(settingsApp: String?, pickerApp: String?): Boolean =
    pickerApp != null && pickerApp != settingsApp

private const val ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS"
private const val ACTION_SETUP_WIFI_NETWORK = "com.android.net.wifi.SETUP_WIFI_NETWORK"
private const val GOOGLE_TV_LAUNCHER = "com.google.android.tvlauncher"

@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel,
    themeMode: ThemeMode,
    background: BackgroundSpec,
    bgRotation: PresetRotation,
    onCycleBgRotation: () -> Unit,
    onPictureAverage: (Int?) -> Unit,
    veil: VeilLevel,
    onCycleVeil: () -> Unit,
    noticeSeconds: Int,
    onNoticeSecondsChange: (Int) -> Unit,
    /** After a backup is restored: the screen's own settings are read again. */
    onBackupRestored: () -> Unit,
    /** After all preferences are wiped: permissions are dropped and the activity recreates. */
    onResetAll: () -> Unit,
    customColors: Map<ColorRole, Int>,
    onColorChange: (ColorRole, Int?) -> Unit,
    onResetColors: () -> Unit,
    onToggleTheme: () -> Unit,
    onBackgroundChange: (BackgroundSpec) -> Unit
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val weatherSettings by viewModel.weatherSettings.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val network by viewModel.networkStatus.collectAsState()
    val lockSettings by viewModel.lockSettings.collectAsState()
    val autoUpdateNotice by viewModel.autoUpdateNotice.collectAsState()
    // Checks every minute while the home screen is visible whether the refresh interval has passed
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshWeather()
                delay(60_000L)
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.maybeAutoCheckUpdate()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Back on the consent dialog postpones it to the next start
    var consentPostponed by remember { mutableStateOf(false) }
    var settingsOnWeather by remember { mutableStateOf(false) }

    // Footer messages: each one shows for a few seconds; a new one restarts the timer
    var notice by remember { mutableStateOf<Pair<Notice, Long>?>(null) }
    val notify: (Notice) -> Unit = remember { { message -> notice = message to System.nanoTime() } }
    LaunchedEffect(autoUpdateNotice) {
        val latest = autoUpdateNotice ?: return@LaunchedEffect
        notify(Notice(context.getString(R.string.update_available_hint, latest), R.drawable.ic_update))
        viewModel.clearAutoUpdateNotice()
    }
    val noticeMillis = noticeSeconds * 1000L
    val collator = remember(locale) { Collator.getInstance(locale) }
    val display by viewModel.display.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    var mediaBarFocused by remember { mutableStateOf(false) }
    var showMediaAccessDialog by remember { mutableStateOf(false) }
    // Apps meant for the viewer, minus the ones they hid, with user-defined display names applied
    val visibleApps = remember(uiState.apps, uiState.systemApps, uiState.hidden, uiState.customLabels) {
        val visible = AppCatalog.visible(uiState.apps, uiState.systemApps).filter { it.packageName !in uiState.hidden }
        AppCatalog.applyCustomLabels(visible, uiState.customLabels)
    }
    val allApps = remember(visibleApps, uiState.appOrder, uiState.appsSort, uiState.lastLaunched, collator) {
        AppCatalog.sorted(visibleApps, uiState.appsSort, collator, uiState.lastLaunched) {
            AppCatalog.ordered(visibleApps, uiState.appOrder, collator)
        }
    }
    val favoriteApps = remember(visibleApps, uiState.favorites, uiState.favoritesSort, uiState.lastLaunched, collator) {
        val favorites = AppCatalog.favoriteApps(visibleApps, uiState.favorites)
        AppCatalog.sorted(favorites, uiState.favoritesSort, collator, uiState.lastLaunched) { favorites }
    }

    // Until the user picks a tab, open on Favorites when there are any
    // The start tab is settled once the apps are known; after that the user's choice holds
    var chosenTab by remember { mutableStateOf<HomeTab?>(null) }
    val defaultTab = when (display.startTab.resolve(display.lastTab, favoriteApps.isNotEmpty())) {
        LauncherTab.FAVORITES -> HomeTab.Favorites
        LauncherTab.ALL_APPS -> HomeTab.AllApps
    }
    val requestedTab = chosenTab ?: defaultTab
    val activeCategory = (requestedTab as? HomeTab.Category)?.let { c -> uiState.categories.firstOrNull { it.id == c.id } }
    val tab: HomeTab = if (requestedTab is HomeTab.Category && activeCategory == null) defaultTab else requestedTab
    LaunchedEffect(tab, uiState.isLoading) {
        if (!uiState.isLoading) {
            when (tab) {
                HomeTab.Favorites -> viewModel.rememberTab(LauncherTab.FAVORITES)
                HomeTab.AllApps -> viewModel.rememberTab(LauncherTab.ALL_APPS)
                is HomeTab.Category -> Unit
            }
        }
    }
    val apps = when (tab) {
        HomeTab.Favorites -> favoriteApps
        HomeTab.AllApps -> allApps
        is HomeTab.Category -> activeCategory?.let { AppCatalog.categoryApps(visibleApps, it) }.orEmpty()
    }
    var showSettings by remember { mutableStateOf(false) }
    var showForecast by remember { mutableStateOf(false) }
    var pendingPinAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    // Index of the card to focus next: the first one after loading, a neighbour after a removal
    var pendingFocusIndex by remember { mutableStateOf<Int?>(null) }
    var initialFocusDone by remember { mutableStateOf(false) }
    val favoritesTabFocus = remember { FocusRequester() }
    val allAppsTabFocus = remember { FocusRequester() }
    val categoryFocusMap = remember(uiState.categories) {
        uiState.categories.associate { HomeTab.Category(it.id) to FocusRequester() }
    }
    val tabFocus: (HomeTab) -> FocusRequester = { which ->
        when (which) {
            HomeTab.Favorites -> favoritesTabFocus
            HomeTab.AllApps -> allAppsTabFocus
            is HomeTab.Category -> categoryFocusMap[which] ?: favoritesTabFocus
        }
    }
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && !initialFocusDone) {
            initialFocusDone = true
            pendingFocusIndex = 0
        }
    }

    val settingsPinTitle = stringResource(R.string.settings_title)
    val openSettingsGuarded: (Boolean) -> Unit = { onWeather ->
        val openPanel = {
            settingsOnWeather = onWeather
            showSettings = true
        }
        if (lockSettings.isSettingsLocked()) pendingPinAction = settingsPinTitle to openPanel else openPanel()
    }
    val launchDirect: (AppEntry) -> Unit = { entry ->
        if (!viewModel.launch(entry)) notify(Notice(context.getString(R.string.msg_launch_failed, entry.label), R.drawable.ic_error))
    }
    val onLaunch: (AppEntry) -> Unit = { entry ->
        if (lockSettings.isAppLocked(entry.packageName)) pendingPinAction = entry.label to { launchDirect(entry) }
        else launchDirect(entry)
    }
    val onToggleFavorite: (Int, AppEntry) -> Unit = { index, entry ->
        val added = viewModel.toggleFavorite(entry.packageName)
        val message = if (added) R.string.msg_favorite_added else R.string.msg_favorite_removed
        notify(Notice(context.getString(message, entry.label), if (added) R.drawable.ic_star else R.drawable.ic_star_border))
        // Removing a card from the Favorites tab would drop the focus; move it to a neighbour
        if (tab == HomeTab.Favorites && !added) {
            if (apps.size > 1) pendingFocusIndex = index.coerceAtMost(apps.size - 2)
            else runCatching { favoritesTabFocus.requestFocus() }
        }
    }
    val focusSelectedTab: () -> Unit = { runCatching { tabFocus(tab).requestFocus() } }
    // Up from the first row reaches the top bar control above the card, not always the tabs
    val upTargets = remember(
        display.showSearch,
        display.showVoiceSearch,
        display.showInputs,
        display.showNetwork,
        uiState.categories.size
    ) { UpTargets() }
    val upFromFirstRow: (Float) -> Unit = { x -> if (!upTargets.focusAbove(x)) focusSelectedTab() }

    // The long-press menu's app, nested dialog targets, and the app being moved with the arrows
    var menuFor by remember { mutableStateOf<Pair<Int, AppEntry>?>(null) }
    var renamingApp by remember { mutableStateOf<AppEntry?>(null) }
    var renameReturnIndex by remember { mutableStateOf<Int?>(null) }
    var categorizingApp by remember { mutableStateOf<AppEntry?>(null) }
    var categorizeReturnIndex by remember { mutableStateOf<Int?>(null) }
    var coveringApp by remember { mutableStateOf<AppEntry?>(null) }
    var coverReturnIndex by remember { mutableStateOf<Int?>(null) }
    var focusedIndex by remember(tab) { mutableIntStateOf(0) }
    var movingPackage by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = movingPackage != null) { movingPackage = null }
    val columns = if (tab == HomeTab.Favorites) display.cardSize.favoriteColumns else display.cardSize.allAppsColumns
    val onMoveKey: (AppEntry, Key) -> Unit = { entry, key ->
        val delta = when (key) {
            Key.DirectionLeft -> -1
            Key.DirectionRight -> 1
            Key.DirectionUp -> -columns
            else -> columns
        }
        val shown = apps.map { it.packageName }
        pendingFocusIndex = when (tab) {
            HomeTab.Favorites -> viewModel.moveFavorite(entry.packageName, delta, shown)
            HomeTab.AllApps -> viewModel.moveApp(entry.packageName, delta, shown)
            is HomeTab.Category -> viewModel.moveInCategory(tab.id, entry.packageName, delta, shown)
        }
    }
    val moveKeysFor: (AppEntry) -> ((Key) -> Unit)? = { entry ->
        if (entry.packageName == movingPackage) { key -> onMoveKey(entry, key) } else null
    }

    val (busy, pulseBusy) = rememberBusy(holding = uiState.isLoading)
    val busyThen: (() -> Unit) -> Unit = { change -> pulseBusy(); change() }
    val shortcutsEnabled = !showSettings && menuFor == null && renamingApp == null &&
        categorizingApp == null && coveringApp == null && pendingPinAction == null &&
        !showForecast && !showMediaAccessDialog && movingPackage == null &&
        !(weatherSettings.needsConsent && !uiState.isLoading && !consentPostponed)
    CompositionLocalProvider(LocalNotice provides notify, LocalBusy provides pulseBusy) {
        val open = rememberOpener()
        // A chosen picture is decoded here so its colours can steer the backdrop colours too
        val pictureState = (background as? BackgroundSpec.Image)?.let { rememberUriBitmap(it.uri) }
        val picture = pictureState?.value
        // "Match background" needs the picture's colour, which only this screen knows
        LaunchedEffect(picture) { onPictureAverage(picture?.let(::pictureAverage)) }
        val backdrop = remember(background, picture, palette, veil) { backdropColors(background, picture, palette, veil) }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (!shortcutsEnabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.ChannelUp, Key.ChannelDown -> {
                                val nextTab = RemoteNavigation.nextTab(
                                    current = tab,
                                    categoryIds = uiState.categories.map { it.id },
                                    forward = event.key == Key.ChannelUp
                                )
                                chosenTab = nextTab
                                val nextApps = when (nextTab) {
                                    HomeTab.Favorites -> favoriteApps
                                    HomeTab.AllApps -> allApps
                                    is HomeTab.Category -> uiState.categories.firstOrNull { it.id == nextTab.id }
                                        ?.let { AppCatalog.categoryApps(visibleApps, it) }.orEmpty()
                                }
                                if (nextApps.isNotEmpty()) pendingFocusIndex = 0
                                else runCatching { tabFocus(nextTab).requestFocus() }
                                true
                            }
                            Key.PageUp, Key.PageDown -> {
                                val target = RemoteNavigation.pageJumpIndex(
                                    currentIndex = focusedIndex,
                                    totalCount = apps.size,
                                    columns = columns,
                                    forward = event.key == Key.PageDown
                                )
                                if (target >= 0) {
                                    pendingFocusIndex = target
                                    true
                                } else false
                            }
                            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause ->
                                if (nowPlaying != null) { viewModel.mediaPlayPause(); true } else false
                            Key.MediaNext ->
                                if (nowPlaying != null) { viewModel.mediaNext(); true } else false
                            Key.MediaPrevious ->
                                if (nowPlaying != null) { viewModel.mediaPrevious(); true } else false
                            else -> {
                                val digit = event.key.toDigitChar()?.digitToIntOrNull()
                                val favIndex = if (display.numberShortcuts && digit != null && event.nativeKeyEvent.repeatCount == 0) {
                                    RemoteNavigation.favoriteIndexForDigit(digit, favoriteApps.size)
                                } else -1
                                if (favIndex >= 0) {
                                    onLaunch(favoriteApps[favIndex])
                                    true
                                } else false
                            }
                        }
                    }
                    // App cards consume Menu in preview; outside cards Menu opens Settings
                    .onKeyEvent { event ->
                        if (shortcutsEnabled && event.key == Key.Menu && event.type == KeyEventType.KeyUp) {
                            openSettingsGuarded(false)
                            true
                        } else false
                    }
            ) {
                LauncherBackground(background, picture, veil)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 28.dp)
                ) {
                    TopBar(
                        tab = tab,
                        categories = uiState.categories,
                        onSelectTab = { chosenTab = it },
                        tabFocus = tabFocus,
                        onOpenClock = {
                            if (weatherSettings.enabled && weather != null) showForecast = true
                            else openSettingsGuarded(true)
                        },
                        onOpenSettings = { openSettingsGuarded(false) },
                        upTargets = upTargets,
                        onFocusTabs = focusSelectedTab,
                        weather = weather.takeIf { weatherSettings.enabled },
                        temperatureUnit = weatherSettings.unit,
                        display = display,
                        network = network,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    when {
                        // The full-screen ring covers loading; this keeps the layout in place under it
                        uiState.isLoading -> Spacer(modifier = Modifier.weight(1f))
                        apps.isEmpty() -> CenteredStatus(
                            stringResource(
                                when (tab) {
                                    HomeTab.Favorites -> R.string.favorites_empty
                                    HomeTab.AllApps -> R.string.status_no_apps
                                    is HomeTab.Category -> R.string.category_empty
                                }
                            ),
                            showSpinner = false,
                            modifier = Modifier.weight(1f)
                        )
                        else -> {
                            val requesters = remember(apps.size, tab) { List(apps.size) { FocusRequester() } }
                            LaunchedEffect(pendingFocusIndex, requesters) {
                                val index = pendingFocusIndex ?: return@LaunchedEffect
                                delay(50)
                                runCatching { requesters[index.coerceIn(0, requesters.lastIndex)].requestFocus() }
                                pendingFocusIndex = null
                            }
                            val roomy = with(LocalDensity.current) { RoomyBringIntoView((FADE_BAND + 16.dp).toPx()) }
                            @OptIn(ExperimentalFoundationApi::class)
                            CompositionLocalProvider(LocalBringIntoViewSpec provides roomy) {
                                if (tab == HomeTab.Favorites) {
                                    // Favorites are few: inset from all four sides, every row centred (the last,
                                    // partial one too), growing downwards and scrolling once they fill the screen
                                    val scroll = rememberScrollState()
                                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    // The cards and their gaps fill the inset width exactly
                                    val cardWidth = (maxWidth - FAVORITES_INSET * 2 - FAVORITES_GAP * (columns - 1)) / columns
                                    FlowRow(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .fadingEdges(top = scroll.canScrollBackward, bottom = scroll.canScrollForward)
                                            .verticalScroll(scroll)
                                            .padding(horizontal = FAVORITES_INSET, vertical = 36.dp),
                                        horizontalArrangement = Arrangement.spacedBy(FAVORITES_GAP, Alignment.CenterHorizontally),
                                        verticalArrangement = Arrangement.spacedBy(24.dp),
                                        maxItemsInEachRow = columns
                                    ) {
                                        apps.forEachIndexed { index, entry ->
                                            key(entry.packageName) {
                                                AppCard(
                                                    entry = entry,
                                                    art = uiState.art[entry.packageName],
                                                    isFavorite = false,
                                                    isLocked = lockSettings.isAppLocked(entry.packageName),
                                                    cardOpacity = display.cardOpacity,
                                                    modifier = Modifier
                                                        .animatePlacement()
                                                        .width(cardWidth)
                                                        .focusRequester(requesters[index]),
                                                    onFocused = { focusedIndex = index },
                                                    showName = display.showNames,
                                                    onClick = { onLaunch(entry) },
                                                    onLongClick = { menuFor = index to entry },
                                                    onUp = if (index < columns) upFromFirstRow else null,
                                                    onMoveKey = moveKeysFor(entry),
                                                    onMoveDone = { movingPackage = null }
                                                )
                                            }
                                        }
                                    }
                                    }
                                } else {
                                    val gridState = rememberLazyGridState()
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(columns),
                                        state = gridState,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fadingEdges(top = gridState.canScrollBackward, bottom = gridState.canScrollForward),
                                        // Room around the cards so the focused or lifted one can grow without being clipped
                                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        itemsIndexed(apps, key = { _, entry -> entry.packageName }) { index, entry ->
                                            AppCard(
                                                entry = entry,
                                                art = uiState.art[entry.packageName],
                                                isFavorite = entry.packageName in uiState.favorites,
                                                isLocked = lockSettings.isAppLocked(entry.packageName),
                                                cardOpacity = display.cardOpacity,
                                                // Slides to its new place when apps are moved
                                                modifier = Modifier
                                                    .animateItem()
                                                    .focusRequester(requesters[index]),
                                                onFocused = { focusedIndex = index },
                                                showName = display.showNames,
                                                onClick = { onLaunch(entry) },
                                                onLongClick = { menuFor = index to entry },
                                                onUp = if (index < columns) upFromFirstRow else null,
                                                onMoveKey = moveKeysFor(entry),
                                                onMoveDone = { movingPackage = null }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // The bar takes its own room under the apps, so it never covers the last row.
                    // While an app is being moved it keeps its instructions; otherwise the latest message.
                    val moving = apps.firstOrNull { it.packageName == movingPackage }
                    val showMediaBar = nowPlaying != null && notice == null && moving == null
                    LaunchedEffect(showMediaBar) {
                        if (!showMediaBar && mediaBarFocused) {
                            mediaBarFocused = false
                            if (apps.isNotEmpty()) pendingFocusIndex = 0 else focusSelectedTab()
                        }
                    }
                    NowPlayingBar(
                        nowPlaying = nowPlaying,
                        visible = showMediaBar,
                        onOpenApp = {
                            val label = (nowPlaying as? NowPlaying.Session)?.snapshot?.appLabel.orEmpty()
                            if (!viewModel.mediaOpenApp() && label.isNotEmpty()) {
                                notify(Notice(context.getString(R.string.msg_launch_failed, label), R.drawable.ic_error))
                            }
                        },
                        onOpenAccessSetup = { showMediaAccessDialog = true },
                        onPlayPause = viewModel::mediaPlayPause,
                        onPrevious = viewModel::mediaPrevious,
                        onNext = viewModel::mediaNext,
                        onSwitchSession = viewModel::mediaSwitchSession,
                        onToggleMute = viewModel::mediaToggleMute,
                        onStopOrDismiss = viewModel::mediaStopOrDismiss,
                        onFocusChange = { mediaBarFocused = it }
                    )
                    FooterBar(
                        notice = moving?.let { Notice(stringResource(R.string.move_hint, it.label), R.drawable.ic_move) } ?: notice?.first,
                        noticeKey = if (moving != null) null else notice?.second,
                        // The move instructions stay until the move ends; messages count down
                        durationMillis = if (moving != null) null else noticeMillis,
                        onClose = { if (moving != null) movingPackage = null else notice = null },
                        onTimeout = { notice = null }
                    )
                }
                // Over everything on the screen while it redraws; the dialogs carry their own
                BusyOverlay(visible = busy)
            }
        }

        menuFor?.let { (index, entry) ->
            val isFavorite = entry.packageName in uiState.favorites
            val isLocked = lockSettings.isAppLocked(entry.packageName)
            val infoLabel = stringResource(R.string.menu_info)
            val uninstallLabel = stringResource(R.string.menu_uninstall)
            val hideLabel = stringResource(R.string.menu_hide)
            val lockLabel = stringResource(if (isLocked) R.string.menu_unlock_app else R.string.menu_lock_app)
            val packageUri = Uri.fromParts("package", entry.packageName, null)
            fun guarded(label: String, block: () -> Unit): () -> Unit = {
                if (lockSettings.isSettingsLocked()) pendingPinAction = label to block else block()
            }
            val favoriteAction = MenuAction(
                iconRes = if (isFavorite) R.drawable.ic_star_border else R.drawable.ic_star,
                labelRes = if (isFavorite) R.string.menu_remove_favorite else R.string.menu_add_favorite,
                run = { onToggleFavorite(index, entry) }
            )
            val moveAction = MenuAction(R.drawable.ic_move, R.string.menu_move) {
                movingPackage = entry.packageName
                pendingFocusIndex = index
            }
            val renameAction = MenuAction(R.drawable.ic_edit, R.string.menu_rename) {
                renameReturnIndex = index
                renamingApp = entry
            }
            val coverAction = MenuAction(R.drawable.ic_image, R.string.menu_custom_art) {
                coverReturnIndex = index
                coveringApp = entry
            }
            val categoriesAction = if (uiState.categories.isNotEmpty()) {
                MenuAction(R.drawable.ic_folder, R.string.menu_categories) {
                    categorizeReturnIndex = index
                    categorizingApp = entry
                }
            } else null
            val lockAction = if (lockSettings.hasPin) {
                MenuAction(if (isLocked) R.drawable.ic_lock_open else R.drawable.ic_lock, lockActionRes(isLocked)) {
                    pendingPinAction = lockLabel to {
                        val nowLocked = viewModel.toggleAppLock(entry.packageName)
                        val msgRes = if (nowLocked) R.string.msg_app_locked else R.string.msg_app_unlocked
                        notify(Notice(context.getString(msgRes, entry.label), if (nowLocked) R.drawable.ic_lock else R.drawable.ic_lock_open))
                    }
                }
            } else null
            val hideAction = MenuAction(
                R.drawable.ic_visibility_off,
                R.string.menu_hide,
                guarded(hideLabel) {
                    viewModel.toggleHidden(entry.packageName)
                    notify(Notice(context.getString(R.string.msg_hidden, entry.label), R.drawable.ic_visibility_off))
                }
            )
            val infoAction = MenuAction(
                R.drawable.ic_info,
                R.string.menu_info,
                guarded(infoLabel) { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri), infoLabel) }
            )
            val uninstallAction = MenuAction(
                R.drawable.ic_delete,
                R.string.menu_uninstall,
                guarded(uninstallLabel) { open(Intent(Intent.ACTION_DELETE, packageUri), uninstallLabel) }
            )
            // Move only makes sense while the tab is in the user's own order
            val canMove = when (tab) {
                HomeTab.Favorites -> uiState.favoritesSort == SortMode.CUSTOM
                HomeTab.AllApps -> uiState.appsSort == SortMode.CUSTOM
                is HomeTab.Category -> true
            }
            val middle = listOfNotNull(renameAction, coverAction, categoriesAction, lockAction, infoAction)
            AppMenu(
                entry = entry,
                art = uiState.art[entry.packageName],
                actions = if (tab == HomeTab.Favorites) {
                    listOfNotNull(moveAction.takeIf { canMove }) + middle + listOf(favoriteAction, hideAction, uninstallAction)
                } else {
                    listOfNotNull(favoriteAction, moveAction.takeIf { canMove }) + middle + listOf(hideAction, uninstallAction)
                },
                onDismiss = {
                    menuFor = null
                    // Back on the card the menu was opened from (unless a nested dialog took over)
                    if (pendingFocusIndex == null && renamingApp == null && categorizingApp == null &&
                        coveringApp == null && pendingPinAction == null) {
                        pendingFocusIndex = index
                    }
                }
            )
        }

        renamingApp?.let { entry ->
            val originalLabel = uiState.apps.firstOrNull { it.packageName == entry.packageName }?.label ?: entry.label
            val hasCustom = entry.packageName in uiState.customLabels
            RenameAppDialog(
                originalLabel = originalLabel,
                currentLabel = entry.label,
                hasCustomLabel = hasCustom,
                onSave = { newLabel ->
                    val trimmed = newLabel?.trim().orEmpty()
                    viewModel.setCustomLabel(entry.packageName, trimmed.ifEmpty { null })
                    if (trimmed.isEmpty()) {
                        if (hasCustom) notify(Notice(context.getString(R.string.msg_rename_reset, originalLabel), R.drawable.ic_edit))
                    } else {
                        notify(Notice(context.getString(R.string.msg_renamed, trimmed), R.drawable.ic_edit))
                    }
                },
                onDismiss = {
                    renamingApp = null
                    renameReturnIndex?.let { pendingFocusIndex = it }
                    renameReturnIndex = null
                }
            )
        }

        categorizingApp?.let { entry ->
            AppCategoriesDialog(
                appLabel = entry.label,
                categories = uiState.categories,
                packageName = entry.packageName,
                onToggle = { categoryId -> viewModel.toggleAppCategory(categoryId, entry.packageName) },
                onDismiss = {
                    categorizingApp = null
                    categorizeReturnIndex?.let { pendingFocusIndex = it }
                    categorizeReturnIndex = null
                }
            )
        }

        coveringApp?.let { entry ->
            AppCoverPickerDialog(
                appLabel = entry.label,
                currentCoverUri = uiState.customArt[entry.packageName],
                onPickUri = { uri ->
                    scope.launch {
                        val saved = viewModel.setCustomArt(entry.packageName, uri)
                        notify(
                            if (saved) Notice(context.getString(R.string.msg_cover_updated, entry.label), R.drawable.ic_image)
                            else Notice(context.getString(R.string.msg_image_failed), R.drawable.ic_error)
                        )
                    }
                },
                onReset = {
                    viewModel.clearCustomArt(entry.packageName)
                    notify(Notice(context.getString(R.string.msg_cover_reset, entry.label), R.drawable.ic_image))
                },
                onDismiss = {
                    coveringApp = null
                    coverReturnIndex?.let { pendingFocusIndex = it }
                    coverReturnIndex = null
                }
            )
        }

        if (showForecast && weather != null) {
            WeatherForecastDialog(
                weather = weather!!,
                unit = weatherSettings.unit,
                onRefresh = {
                    viewModel.refreshWeather(force = true)
                    notify(Notice(context.getString(R.string.msg_weather_refreshing), R.drawable.ic_autorenew))
                },
                onOpenSettings = { openSettingsGuarded(true) },
                onDismiss = { showForecast = false }
            )
        }

        pendingPinAction?.let { (title, action) ->
            PinDialog(
                title = title,
                mode = PinDialogMode.VERIFY,
                verify = viewModel::verifyPin,
                onSuccess = { action() },
                onDismiss = { pendingPinAction = null }
            )
        }

        // Before the internet address is used to find the place, the user decides
        if (weatherSettings.needsConsent && !uiState.isLoading && !consentPostponed && !showSettings &&
            pendingPinAction == null && !showForecast) {
            WeatherConsentDialog(
                onAllow = { viewModel.setIpConsent(true) },
                onPickCity = {
                    viewModel.setIpConsent(false)
                    settingsOnWeather = true
                    showSettings = true
                },
                onDeny = {
                    viewModel.setIpConsent(false)
                    viewModel.setWeatherEnabled(false)
                },
                onLater = { consentPostponed = true }
            )
        }

        if (showMediaAccessDialog) {
            MediaControlsSetupDialog(
                viewModel = viewModel,
                showTurnOff = false,
                onDismiss = { showMediaAccessDialog = false }
            )
        }

        if (showSettings) {
            SettingsPanel(
                themeMode = themeMode,
                background = background,
                bgRotation = bgRotation,
                onCycleBgRotation = onCycleBgRotation,
                network = network,
                customColors = customColors,
                onColorChange = { role, color -> busyThen { onColorChange(role, color) } },
                onResetColors = { busyThen(onResetColors) },
                systemApps = uiState.systemApps,
                favoritesSort = uiState.favoritesSort,
                appsSort = uiState.appsSort,
                onToggleTheme = { busyThen(onToggleTheme) },
                onBackgroundChange = { spec -> busyThen { onBackgroundChange(spec) } },
                onCycleSystemApps = viewModel::cycleSystemApps,
                onCycleFavoritesSort = viewModel::cycleFavoritesSort,
                onCycleAppsSort = viewModel::cycleAppsSort,
                veil = veil,
                onCycleVeil = { busyThen(onCycleVeil) },
                noticeSeconds = noticeSeconds,
                onNoticeSecondsChange = onNoticeSecondsChange,
                weatherSettings = weatherSettings,
                onWeatherEnabled = viewModel::setWeatherEnabled,
                onWeatherPlace = viewModel::setWeatherPlace,
                onWeatherConsent = viewModel::setIpConsent,
                onCycleWeatherInterval = viewModel::cycleWeatherInterval,
                searchCities = viewModel::searchCities,
                openOnWeather = settingsOnWeather,
                viewModel = viewModel,
                busy = busy,
                onBackupRestored = onBackupRestored,
                onResetAll = {
                    showSettings = false
                    onResetAll()
                },
                onClose = {
                    showSettings = false
                    settingsOnWeather = false
                }
            )
        }
    }
}

private fun lockActionRes(isLocked: Boolean): Int =
    if (isLocked) R.string.menu_unlock_app else R.string.menu_lock_app

/**
 * Rows scrolling under the top bar or off the bottom fade out instead of being cut off. An edge
 * fades only while there is more to scroll that way, so the first and last rows show in full.
 */
private fun Modifier.fadingEdges(top: Boolean, bottom: Boolean): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (!top && !bottom) return@drawWithContent
        val band = (FADE_BAND.toPx() / size.height).coerceIn(0f, 0.5f)
        drawRect(
            brush = Brush.verticalGradient(
                0f to if (top) Color.Transparent else Color.Black,
                band to Color.Black,
                1f - band to Color.Black,
                1f to if (bottom) Color.Transparent else Color.Black
            ),
            blendMode = BlendMode.DstIn
        )
    }

private val FADE_BAND = 28.dp
private val FAVORITES_INSET = 112.dp
private val FAVORITES_GAP = 28.dp


/**
 * The backdrop: the default picture, the user's picture, a solid colour or a gradient. Pictures
 * get a veil of the theme's background so the clock and labels stay readable; colours are shown
 * exactly as typed.
 */
@Composable
private fun LauncherBackground(spec: BackgroundSpec, picture: ImageBitmap?, level: VeilLevel) {
    val palette = LocalPalette.current
    val veil = palette.background.copy(alpha = veilAlpha(palette, level))
    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        when (spec) {
            is BackgroundSpec.Preset -> {
                Image(
                    painter = painterResource(spec.image.drawableRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(veil))
            }
            is BackgroundSpec.Image -> {
                picture?.let {
                    Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(modifier = Modifier.fillMaxSize().background(veil))
                }
            }
            is BackgroundSpec.Solid -> Box(modifier = Modifier.fillMaxSize().background(Color(spec.color)))
            is BackgroundSpec.Gradient -> {
                val colors = listOf(Color(spec.start), Color(spec.end))
                val brush = when (spec.direction) {
                    GradientDirection.VERTICAL -> Brush.verticalGradient(colors)
                    GradientDirection.HORIZONTAL -> Brush.horizontalGradient(colors)
                    GradientDirection.DIAGONAL -> Brush.linearGradient(colors)
                }
                Box(modifier = Modifier.fillMaxSize().background(brush))
            }
        }
    }
}

/** The veil over pictures: lighter on the dark theme, which needs less help, scaled by the user's [level]. */
private fun veilAlpha(palette: Palette, level: VeilLevel): Float = (if (palette.isDark) 0.25f else 0.72f) * level.strength

/**
 * Backdrop colours for [spec]: measured against the colour or both gradient ends as typed, or
 * against pictures as they look under the veil.
 */
private fun backdropColors(spec: BackgroundSpec, picture: ImageBitmap?, palette: Palette, level: VeilLevel): BackdropColors {
    val veil = palette.background.toArgb()
    val underVeil: (Int) -> Int = { BackdropColors.blend(it, veil, veilAlpha(palette, level)) }
    val own = spec.samples(picture?.let(::pictureAverage))
    // Pictures are seen through the veil; colours and gradients exactly as typed
    val samples = when (spec) {
        is BackgroundSpec.Preset, is BackgroundSpec.Image -> own.map(underVeil).ifEmpty { listOf(veil) }
        is BackgroundSpec.Solid, is BackgroundSpec.Gradient -> own
    }
    return BackdropColors.from(samples, palette.accent.toArgb())
}

/** Average colour of a picture, read from a tiny copy of it. */
private fun pictureAverage(picture: ImageBitmap): Int? = runCatching {
    val small = android.graphics.Bitmap.createScaledBitmap(picture.asAndroidBitmap(), 16, 9, true)
    val pixels = IntArray(16 * 9).also { small.getPixels(it, 0, 16, 0, 0, 16, 9) }
    ArtColor.average(pixels)
}.getOrNull()

/** Decodes the chosen picture at about screen size; null while loading or if it cannot be read. */
@Composable
private fun rememberUriBitmap(uri: String): State<ImageBitmap?> {
    val context = LocalContext.current
    val notify = LocalNotice.current
    return produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            context.decodeScaledBitmap(uri, 1920, 1080)?.asImageBitmap()
        }
        if (value == null) notify(Notice(context.getString(R.string.msg_image_failed), R.drawable.ic_image))
    }
}

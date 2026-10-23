package com.berkbb.otaghome

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Collator

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_NOTICE_SECONDS = "notice_seconds"
        private const val KEY_VEIL = "veil"
    }

    private val prefs by lazy { getSharedPreferences(LauncherViewModel.PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialTheme = ThemeMode.fromName(prefs.getString(KEY_THEME_MODE, null))
        val initialBackground = BackgroundSpec.decode(prefs.getString(KEY_BACKGROUND, null))
        val initialVeil = VeilLevel.fromName(prefs.getString(KEY_VEIL, null))
        val initialNoticeSeconds = NoticeTimer.fromStored(prefs.getInt(KEY_NOTICE_SECONDS, NoticeTimer.DEFAULT))
        // Every colour the user picked, by preferences key (see ColorRole.prefKey)
        val initialColors = ColorRole.entries.flatMap { listOf(it.prefKey(true), it.prefKey(false)) }.distinct()
            .mapNotNull { key -> prefs.getString(key, null)?.let(HexColor::parse)?.let { key to it } }.toMap()

        // The UI language is the TV's system language, as in every launcher
        setContent {
            var themeMode by remember { mutableStateOf(initialTheme) }
            var background by remember { mutableStateOf(initialBackground) }
            var storedColors by remember { mutableStateOf(initialColors) }
            var noticeSeconds by remember { mutableIntStateOf(initialNoticeSeconds) }
            var veil by remember { mutableStateOf(initialVeil) }
            // Average colour of the user's own picture, reported once it has loaded
            var pictureAverage by remember { mutableStateOf<Int?>(null) }
            val backgroundIsDark = background.isDark(pictureAverage)
            val isDark = themeMode.isDark(isSystemInDarkTheme(), backgroundIsDark)
            // The colours that apply now: the shared accent and the current theme's own
            val customColors = ColorRole.entries.mapNotNull { role -> storedColors[role.prefKey(isDark)]?.let { role to it } }.toMap()
            val palette = Palette.of(themeMode, isSystemInDarkTheme(), customColors, backgroundIsDark)

            // Opened as the Home app, Back has nowhere to go; opened from another launcher it closes
            val isHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true
            BackHandler(enabled = isHome) { }

            CompositionLocalProvider(LocalPalette provides palette) {
                LauncherScreen(
                    viewModel = viewModel,
                    themeMode = themeMode,
                    background = background,
                    onPictureAverage = { pictureAverage = it },
                    // Theme, background, colours and the rest of the screen's settings are read in onCreate
                    onBackupRestored = { recreate() },
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
                        prefs.edit { putString(KEY_BACKGROUND, spec.encode()) }
                    }
                )
            }
        }
    }

    /** A picture that is no longer the background does not need its read permission any more. */
    private fun releaseImagePermission(old: BackgroundSpec, new: BackgroundSpec) {
        if (old is BackgroundSpec.Image && old != new) {
            runCatching {
                contentResolver.releasePersistableUriPermission(Uri.parse(old.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
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
    onPictureAverage: (Int?) -> Unit,
    veil: VeilLevel,
    onCycleVeil: () -> Unit,
    noticeSeconds: Int,
    onNoticeSecondsChange: (Int) -> Unit,
    /** After a backup is restored: the screen's own settings are read again. */
    onBackupRestored: () -> Unit,
    customColors: Map<ColorRole, Int>,
    onColorChange: (ColorRole, Int?) -> Unit,
    onResetColors: () -> Unit,
    onToggleTheme: () -> Unit,
    onBackgroundChange: (BackgroundSpec) -> Unit
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val uiState by viewModel.uiState.collectAsState()
    val weatherSettings by viewModel.weatherSettings.collectAsState()
    val weather by viewModel.weather.collectAsState()
    // Checks every minute whether the chosen refresh interval has passed
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshWeather()
            delay(60_000L)
        }
    }
    // Back on the consent dialog postpones it to the next start
    var consentPostponed by remember { mutableStateOf(false) }
    var settingsOnWeather by remember { mutableStateOf(false) }

    // Footer messages: each one shows for a few seconds; a new one restarts the timer
    var notice by remember { mutableStateOf<Pair<Notice, Long>?>(null) }
    val notify: (Notice) -> Unit = remember { { message -> notice = message to System.nanoTime() } }
    val noticeMillis = noticeSeconds * 1000L
    val collator = remember(locale) { Collator.getInstance(locale) }
    val display by viewModel.display.collectAsState()
    // Apps meant for the viewer, minus the ones they hid
    val visibleApps = remember(uiState.apps, uiState.systemApps, uiState.hidden) {
        AppCatalog.visible(uiState.apps, uiState.systemApps).filter { it.packageName !in uiState.hidden }
    }
    val allApps = remember(visibleApps, uiState.appOrder, uiState.appsSort, collator) {
        AppCatalog.sorted(visibleApps, uiState.appsSort, collator) { AppCatalog.ordered(visibleApps, uiState.appOrder, collator) }
    }
    val favoriteApps = remember(visibleApps, uiState.favorites, uiState.favoritesSort, collator) {
        val favorites = AppCatalog.favoriteApps(visibleApps, uiState.favorites)
        AppCatalog.sorted(favorites, uiState.favoritesSort, collator) { favorites }
    }

    // Until the user picks a tab, open on Favorites when there are any
    // The start tab is settled once the apps are known; after that the user's choice holds
    var chosenTab by remember { mutableStateOf<LauncherTab?>(null) }
    val tab = chosenTab ?: display.startTab.resolve(display.lastTab, favoriteApps.isNotEmpty())
    LaunchedEffect(tab, uiState.isLoading) { if (!uiState.isLoading) viewModel.rememberTab(tab) }
    val apps = if (tab == LauncherTab.FAVORITES) favoriteApps else allApps
    var showSettings by remember { mutableStateOf(false) }

    // Index of the card to focus next: the first one after loading, a neighbour after a removal
    var pendingFocusIndex by remember { mutableStateOf<Int?>(null) }
    var initialFocusDone by remember { mutableStateOf(false) }
    val favoritesTabFocus = remember { FocusRequester() }
    val allAppsTabFocus = remember { FocusRequester() }
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && !initialFocusDone) {
            initialFocusDone = true
            pendingFocusIndex = 0
        }
    }

    val onLaunch: (AppEntry) -> Unit = { entry ->
        if (!viewModel.launch(entry)) notify(Notice(context.getString(R.string.msg_launch_failed, entry.label), R.drawable.ic_error))
    }
    val onToggleFavorite: (Int, AppEntry) -> Unit = { index, entry ->
        val added = viewModel.toggleFavorite(entry.packageName)
        val message = if (added) R.string.msg_favorite_added else R.string.msg_favorite_removed
        notify(Notice(context.getString(message, entry.label), if (added) R.drawable.ic_star else R.drawable.ic_star_border))
        // Removing a card from the Favorites tab would drop the focus; move it to a neighbour
        if (tab == LauncherTab.FAVORITES && !added) {
            if (apps.size > 1) pendingFocusIndex = index.coerceAtMost(apps.size - 2)
            else runCatching { favoritesTabFocus.requestFocus() }
        }
    }
    val focusSelectedTab: () -> Unit = {
        runCatching { (if (tab == LauncherTab.FAVORITES) favoritesTabFocus else allAppsTabFocus).requestFocus() }
    }
    // Up from the first row reaches the top bar control above the card, not always the tabs
    val upTargets = remember { UpTargets() }
    val upFromFirstRow: (Float) -> Unit = { x -> if (!upTargets.focusAbove(x)) focusSelectedTab() }

    // The long-press menu's app, and the app being moved with the arrows (Back or OK drops it)
    var menuFor by remember { mutableStateOf<Pair<Int, AppEntry>?>(null) }
    var movingPackage by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = movingPackage != null) { movingPackage = null }
    val columns = if (tab == LauncherTab.FAVORITES) display.cardSize.favoriteColumns else display.cardSize.allAppsColumns
    val onMoveKey: (AppEntry, Key) -> Unit = { entry, key ->
        val delta = when (key) {
            Key.DirectionLeft -> -1
            Key.DirectionRight -> 1
            Key.DirectionUp -> -columns
            else -> columns
        }
        val shown = apps.map { it.packageName }
        pendingFocusIndex = if (tab == LauncherTab.FAVORITES) viewModel.moveFavorite(entry.packageName, delta, shown)
        else viewModel.moveApp(entry.packageName, delta, shown)
    }
    val moveKeysFor: (AppEntry) -> ((Key) -> Unit)? = { entry ->
        if (entry.packageName == movingPackage) { key -> onMoveKey(entry, key) } else null
    }

    val (busy, pulseBusy) = rememberBusy(holding = uiState.isLoading)
    val busyThen: (() -> Unit) -> Unit = { change -> pulseBusy(); change() }
    CompositionLocalProvider(LocalNotice provides notify, LocalBusy provides pulseBusy) {
        val open = rememberOpener()
        // A chosen picture is decoded here so its colours can steer the backdrop colours too
        val pictureState = (background as? BackgroundSpec.Image)?.let { rememberUriBitmap(it.uri) }
        val picture = pictureState?.value
        // "Match background" needs the picture's colour, which only this screen knows
        LaunchedEffect(picture) { onPictureAverage(picture?.let(::pictureAverage)) }
        val backdrop = remember(background, picture, palette, veil) { backdropColors(background, picture, palette, veil) }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(modifier = Modifier.fillMaxSize()) {
                LauncherBackground(background, picture, veil)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 28.dp)
                ) {
                    TopBar(
                        tab = tab,
                        onSelectTab = { chosenTab = it },
                        favoritesTabFocus = favoritesTabFocus,
                        allAppsTabFocus = allAppsTabFocus,
                        onOpenSettings = { showSettings = true },
                        upTargets = upTargets,
                        onFocusTabs = focusSelectedTab,
                        weather = weather.takeIf { weatherSettings.enabled },
                        temperatureUnit = weatherSettings.unit,
                        clockFormat = display.clockFormat,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    when {
                        // The full-screen ring covers loading; this keeps the layout in place under it
                        uiState.isLoading -> Spacer(modifier = Modifier.weight(1f))
                        apps.isEmpty() -> CenteredStatus(
                            stringResource(if (tab == LauncherTab.FAVORITES) R.string.favorites_empty else R.string.status_no_apps),
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
                                if (tab == LauncherTab.FAVORITES) {
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
                                                    modifier = Modifier
                                                        .animatePlacement()
                                                        .width(cardWidth)
                                                        .focusRequester(requesters[index]),
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
                                                // Slides to its new place when apps are moved
                                                modifier = Modifier
                                                    .animateItem()
                                                    .focusRequester(requesters[index]),
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
            val infoLabel = stringResource(R.string.menu_info)
            val uninstallLabel = stringResource(R.string.menu_uninstall)
            val packageUri = Uri.fromParts("package", entry.packageName, null)
            val favoriteAction = MenuAction(
                iconRes = if (isFavorite) R.drawable.ic_star_border else R.drawable.ic_star,
                labelRes = if (isFavorite) R.string.menu_remove_favorite else R.string.menu_add_favorite,
                run = { onToggleFavorite(index, entry) }
            )
            val moveAction = MenuAction(R.drawable.ic_move, R.string.menu_move) {
                movingPackage = entry.packageName
                pendingFocusIndex = index
            }
            val hideAction = MenuAction(R.drawable.ic_visibility_off, R.string.menu_hide) {
                viewModel.toggleHidden(entry.packageName)
                notify(Notice(context.getString(R.string.msg_hidden, entry.label), R.drawable.ic_visibility_off))
            }
            val infoAction = MenuAction(R.drawable.ic_info, R.string.menu_info) {
                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri), infoLabel)
            }
            val uninstallAction = MenuAction(R.drawable.ic_delete, R.string.menu_uninstall) {
                open(Intent(Intent.ACTION_DELETE, packageUri), uninstallLabel)
            }
            // Move only makes sense while the tab is in the user's own order
            val canMove = (if (tab == LauncherTab.FAVORITES) uiState.favoritesSort else uiState.appsSort) == SortMode.CUSTOM
            AppMenu(
                entry = entry,
                art = uiState.art[entry.packageName],
                actions = if (tab == LauncherTab.FAVORITES) listOfNotNull(moveAction.takeIf { canMove }, infoAction, favoriteAction, hideAction)
                else listOfNotNull(favoriteAction, moveAction.takeIf { canMove }, infoAction, hideAction, uninstallAction),
                onDismiss = {
                    menuFor = null
                    // Back on the card the menu was opened from
                    if (pendingFocusIndex == null) pendingFocusIndex = index
                }
            )
        }

        // Before the internet address is used to find the place, the user decides
        if (weatherSettings.needsConsent && !uiState.isLoading && !consentPostponed && !showSettings) {
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

        if (showSettings) {
            SettingsPanel(
                themeMode = themeMode,
                background = background,
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
                onClose = {
                    showSettings = false
                    settingsOnWeather = false
                }
            )
        }
    }
}

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
            runCatching {
                val parsed = Uri.parse(uri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
            }.getOrNull()
        }
        if (value == null) notify(Notice(context.getString(R.string.msg_image_failed), R.drawable.ic_image))
    }
}

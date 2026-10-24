package com.berkbb.otaghome

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

private val SHEET_SHAPE = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)

private enum class SettingsPage { MAIN, BACKGROUND, PRESETS, COLORS, WEATHER, DISPLAY, APPS, BACKUP }

/**
 * Side panel opened by the gear, like Google TV's quick settings: the launcher's theme, background,
 * colours, which system apps it lists and the tabs' order, then shortcuts into the system's language, network and full settings screens.
 * Back on the Background page returns to the main page.
 */
@Composable
internal fun SettingsPanel(
    themeMode: ThemeMode,
    background: BackgroundSpec,
    customColors: Map<ColorRole, Int>,
    onColorChange: (ColorRole, Int?) -> Unit,
    onResetColors: () -> Unit,
    systemApps: SystemAppsMode,
    favoritesSort: SortMode,
    appsSort: SortMode,
    onToggleTheme: () -> Unit,
    onBackgroundChange: (BackgroundSpec) -> Unit,
    onCycleSystemApps: () -> Unit,
    onCycleFavoritesSort: () -> Unit,
    onCycleAppsSort: () -> Unit,
    veil: VeilLevel,
    onCycleVeil: () -> Unit,
    noticeSeconds: Int,
    onNoticeSecondsChange: (Int) -> Unit,
    weatherSettings: WeatherSettings,
    onWeatherEnabled: (Boolean) -> Unit,
    onWeatherPlace: (Place?) -> Unit,
    onWeatherConsent: (Boolean) -> Unit,
    onCycleWeatherInterval: () -> Unit,
    searchCities: suspend (String, String) -> List<Place>?,
    /** Opens straight on the weather page, as "Pick a city" in the consent dialog does. */
    openOnWeather: Boolean = false,
    viewModel: LauncherViewModel,
    /** Shows the full-screen ring inside the panel while a change redraws the screen. */
    busy: Boolean,
    onBackupRestored: () -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalPalette.current
    var page by remember { mutableStateOf(if (openOnWeather) SettingsPage.WEATHER else SettingsPage.MAIN) }
    var showAbout by remember { mutableStateOf(false) }
    val firstRowFocus = remember { FocusRequester() }
    // Back from a page lands on the row that opened it, not on the first row of the settings
    val openerFocus = remember { SettingsPage.entries.associateWith { FocusRequester() } }
    var returnFrom by remember { mutableStateOf<SettingsPage?>(null) }
    fun open(target: SettingsPage) = openerFocus.getValue(target)

    Dialog(
        // Back steps out one page at a time: pictures → background → settings → closed
        onDismissRequest = {
            page = when (page) {
                SettingsPage.PRESETS -> SettingsPage.BACKGROUND
                SettingsPage.BACKGROUND, SettingsPage.COLORS, SettingsPage.WEATHER,
                SettingsPage.DISPLAY, SettingsPage.APPS, SettingsPage.BACKUP -> SettingsPage.MAIN.also { returnFrom = page }
                SettingsPage.MAIN -> SettingsPage.MAIN.also { onClose() }
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(page) {
            val target = returnFrom?.takeIf { page == SettingsPage.MAIN }?.let(::open) ?: firstRowFocus
            returnFrom = null
            runCatching { target.requestFocus() }
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            val panel = Modifier
                .fillMaxHeight()
                .width(400.dp)
                // A soft shadow along the panel's edge lifts it off the screen behind; M3 side sheets
                // round the corners that face the screen
                .shadow(16.dp, SHEET_SHAPE)
                .clip(SHEET_SHAPE)
                .background(palette.background)
            if (page == SettingsPage.PRESETS) {
                // A lazy grid draws only the pictures on screen, however many there are
                Column(modifier = panel.padding(horizontal = 24.dp, vertical = 32.dp)) {
                    PanelTitle(stringResource(R.string.bg_presets))
                    Spacer(modifier = Modifier.height(20.dp))
                    PresetsGrid(background, firstRowFocus, onBackgroundChange)
                }
                return@Box
            }
            if (showAbout) AboutWindow(viewModel = viewModel, noticeSeconds = noticeSeconds, onClose = { showAbout = false })
            Column(
                modifier = panel
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PanelTitle(
                    stringResource(
                        when (page) {
                            SettingsPage.BACKGROUND -> R.string.settings_background
                            SettingsPage.COLORS -> R.string.settings_colors
                            SettingsPage.WEATHER -> R.string.settings_weather
                            SettingsPage.DISPLAY -> R.string.settings_display
                            SettingsPage.APPS -> R.string.settings_apps
                            SettingsPage.BACKUP -> R.string.settings_backup
                            else -> R.string.settings_title
                        }
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                when (page) {
                    SettingsPage.MAIN -> {
                        MainPage(themeMode, background, firstRowFocus, open(SettingsPage.BACKGROUND), onToggleTheme) {
                            page = SettingsPage.BACKGROUND
                        }
                        SettingsRow(
                            iconRes = R.drawable.ic_palette,
                            title = stringResource(R.string.settings_colors),
                            value = stringResource(if (customColors.isEmpty()) R.string.btn_default else R.string.colors_customised),
                            modifier = Modifier.focusRequester(open(SettingsPage.COLORS)),
                            onClick = { page = SettingsPage.COLORS }
                        )
                        SettingsRow(
                            iconRes = R.drawable.ic_view_module,
                            title = stringResource(R.string.settings_display),
                            value = null,
                            modifier = Modifier.focusRequester(open(SettingsPage.DISPLAY)),
                            onClick = { page = SettingsPage.DISPLAY }
                        )
                        SettingsRow(
                            iconRes = R.drawable.ic_apps,
                            title = stringResource(R.string.settings_apps),
                            value = null,
                            modifier = Modifier.focusRequester(open(SettingsPage.APPS)),
                            onClick = { page = SettingsPage.APPS }
                        )
                        SettingsRow(
                            iconRes = R.drawable.ic_w_partly,
                            title = stringResource(R.string.settings_weather),
                            value = stringResource(if (weatherSettings.enabled) R.string.state_on else R.string.state_off),
                            modifier = Modifier.focusRequester(open(SettingsPage.WEATHER)),
                            onClick = { page = SettingsPage.WEATHER }
                        )
                        SettingsRow(
                            iconRes = R.drawable.ic_backup,
                            title = stringResource(R.string.settings_backup),
                            value = null,
                            modifier = Modifier.focusRequester(open(SettingsPage.BACKUP)),
                            onClick = { page = SettingsPage.BACKUP }
                        )
                        SystemRows()
                        SettingsRow(
                            iconRes = R.drawable.ic_info,
                            title = stringResource(R.string.settings_about),
                            value = "v${BuildConfig.VERSION_NAME}",
                            onClick = { showAbout = true }
                        )
                    }
                    SettingsPage.BACKGROUND -> BackgroundPage(background, veil, onCycleVeil, firstRowFocus, onBackgroundChange) {
                        page = SettingsPage.PRESETS
                    }
                    SettingsPage.COLORS -> ColorsPage(customColors, firstRowFocus, onColorChange, onResetColors)
                    SettingsPage.WEATHER -> WeatherPage(
                        settings = weatherSettings,
                        firstRowFocus = firstRowFocus,
                        onEnabledChange = onWeatherEnabled,
                        onPlaceChange = onWeatherPlace,
                        onConsentChange = onWeatherConsent,
                        onCycleInterval = onCycleWeatherInterval,
                        onCycleUnit = viewModel::cycleTempUnit,
                        search = searchCities
                    )
                    SettingsPage.DISPLAY -> DisplayPage(viewModel, noticeSeconds, onNoticeSecondsChange, firstRowFocus)
                    SettingsPage.APPS -> AppsPage(viewModel, systemApps, favoritesSort, appsSort, onCycleSystemApps, onCycleFavoritesSort, onCycleAppsSort, firstRowFocus)
                    SettingsPage.BACKUP -> BackupPage(viewModel, firstRowFocus, onBackupRestored)
                    SettingsPage.PRESETS -> Unit
                }
            }
            BusyOverlay(visible = busy)
        }
    }
}

@Composable
private fun PanelTitle(text: String) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(28.dp)
                .background(palette.accent, RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = text, color = palette.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MainPage(
    themeMode: ThemeMode,
    background: BackgroundSpec,
    firstRowFocus: FocusRequester,
    backgroundFocus: FocusRequester,
    onToggleTheme: () -> Unit,
    onOpenBackground: () -> Unit
) {
    val palette = LocalPalette.current

    SettingsRow(
        iconRes = if (palette.isDark) R.drawable.ic_dark_mode else R.drawable.ic_light_mode,
        title = stringResource(R.string.settings_theme),
        value = stringResource(themeMode.labelRes),
        modifier = Modifier.focusRequester(firstRowFocus),
        onClick = onToggleTheme
    )
    SettingsRow(
        iconRes = R.drawable.ic_wallpaper,
        title = stringResource(R.string.settings_background),
        value = backgroundLabel(background),
        modifier = Modifier.focusRequester(backgroundFocus),
        onClick = onOpenBackground
    )
}

/** How the home screen looks and opens: clock format, card size, names, start tab, Home button, message time. */
@Composable
private fun DisplayPage(viewModel: LauncherViewModel, noticeSeconds: Int, onNoticeSecondsChange: (Int) -> Unit, firstRowFocus: FocusRequester) {
    val display by viewModel.display.collectAsState()
    var pickNoticeSeconds by remember { mutableStateOf(false) }
    val busy = LocalBusy.current
    SettingsRow(
        iconRes = R.drawable.ic_timer,
        title = stringResource(R.string.settings_clock_format),
        value = stringResource(display.clockFormat.labelRes),
        modifier = Modifier.focusRequester(firstRowFocus),
        onClick = viewModel::cycleClockFormat
    )
    SettingsRow(
        iconRes = R.drawable.ic_view_module,
        title = stringResource(R.string.settings_card_size),
        value = stringResource(display.cardSize.labelRes),
        // The grid is laid out again; the ring covers it
        onClick = { busy(); viewModel.cycleCardSize() }
    )
    SettingsRow(
        iconRes = if (display.showNames) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
        title = stringResource(R.string.settings_show_names),
        value = stringResource(if (display.showNames) R.string.state_on else R.string.state_off),
        onClick = viewModel::toggleShowNames
    )
    SettingsRow(
        iconRes = R.drawable.ic_apps,
        title = stringResource(R.string.settings_start_tab),
        value = stringResource(display.startTab.labelRes),
        onClick = viewModel::cycleStartTab
    )
    HomeOverrideRow()
    SettingsRow(
        iconRes = R.drawable.ic_timer,
        title = stringResource(R.string.settings_notice_time),
        value = stringResource(R.string.notice_seconds, noticeSeconds),
        // A list rather than a cycle: stepping past the wanted time would wrap round to the shortest
        onClick = { pickNoticeSeconds = true }
    )
    if (pickNoticeSeconds) {
        ChoiceDialog(
            title = stringResource(R.string.settings_notice_time),
            options = NoticeTimer.OPTIONS.map { stringResource(R.string.notice_seconds, it) },
            selected = NoticeTimer.OPTIONS.indexOf(noticeSeconds),
            onPick = { onNoticeSecondsChange(NoticeTimer.OPTIONS[it]) },
            onDismiss = { pickNoticeSeconds = false }
        )
    }
}

/**
 * The Home override, for TVs that never let another app be the home screen: OtagHome's
 * accessibility service opens it whenever the TV's own home screen comes up. Only the user can
 * switch the service on in Accessibility, so turning it on explains it first and opens that
 * screen; turning it off stops the service at once.
 */
@Composable
private fun HomeOverrideRow() {
    val on by HomeOverrideService.active.collectAsState()
    var explaining by remember { mutableStateOf(false) }
    val accessibilityLabel = stringResource(R.string.settings_accessibility)
    val open = rememberOpener()
    val openAccessibility = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), accessibilityLabel) }
    SettingsRow(
        iconRes = R.drawable.ic_home,
        title = stringResource(R.string.settings_home_override),
        value = stringResource(if (on) R.string.state_on else R.string.state_off),
        onClick = {
            when {
                !on -> explaining = true
                !HomeOverrideService.turnOff() -> openAccessibility()
            }
        }
    )
    if (explaining) {
        HomeOverrideDialog(
            onOpen = {
                explaining = false
                openAccessibility()
            },
            onDismiss = { explaining = false }
        )
    }
}

/** What the Home override does and how to turn it on; focus starts on Cancel. */
@Composable
private fun HomeOverrideDialog(onOpen: () -> Unit, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            delay(50)
            runCatching { cancelFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(560.dp)
                .shadow(16.dp, Corners.extraLarge)
                .clip(Corners.extraLarge)
                .background(palette.background)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).background(palette.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painter = painterResource(R.drawable.ic_home), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = stringResource(R.string.home_override_title), color = palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = stringResource(R.string.home_override_text), color = palette.textSecondary, fontSize = 16.sp, lineHeight = 23.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                PillButton(stringResource(R.string.btn_cancel), primary = false, modifier = Modifier.focusRequester(cancelFocus), onClick = onDismiss)
                PillButton(stringResource(R.string.home_override_open), primary = true, onClick = onOpen)
            }
        }
    }
}

/** Which apps show and in what order, and the apps the user hid (OK on one shows it again). */
@Composable
private fun AppsPage(
    viewModel: LauncherViewModel,
    systemApps: SystemAppsMode,
    favoritesSort: SortMode,
    appsSort: SortMode,
    onCycleSystemApps: () -> Unit,
    onCycleFavoritesSort: () -> Unit,
    onCycleAppsSort: () -> Unit,
    firstRowFocus: FocusRequester
) {
    val ui by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val notify = LocalNotice.current
    SettingsRow(
        iconRes = R.drawable.ic_apps,
        title = stringResource(R.string.settings_system_apps),
        value = stringResource(systemApps.labelRes),
        modifier = Modifier.focusRequester(firstRowFocus),
        onClick = onCycleSystemApps
    )
    SortRows(favoritesSort, appsSort, onCycleFavoritesSort, onCycleAppsSort)
    val hidden = ui.hidden.mapNotNull { pkg -> ui.apps.firstOrNull { it.packageName == pkg } }
    SectionLabel(stringResource(R.string.settings_hidden_apps), selected = false)
    if (hidden.isEmpty()) {
        Text(text = stringResource(R.string.hidden_none), color = LocalPalette.current.textSecondary, fontSize = 14.sp)
    }
    hidden.forEach { entry ->
        SettingsRow(
            iconRes = R.drawable.ic_visibility,
            title = entry.label,
            value = stringResource(R.string.hidden_show_again),
            onClick = {
                viewModel.toggleHidden(entry.packageName)
                notify(Notice(context.getString(R.string.msg_shown, entry.label), R.drawable.ic_visibility))
            }
        )
    }
}

/** Back up every setting to a file, or restore them from one, through the TV's document picker. */
@Composable
private fun BackupPage(viewModel: LauncherViewModel, firstRowFocus: FocusRequester, onRestored: () -> Unit) {
    val context = LocalContext.current
    val notify = LocalNotice.current
    val busy = LocalBusy.current
    val saveLabel = stringResource(R.string.backup_save)
    val restoreLabel = stringResource(R.string.backup_restore)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(viewModel.exportBackup().toByteArray()) } != null
        }.getOrDefault(false)
        notify(
            if (written) Notice(context.getString(R.string.msg_backup_saved), R.drawable.ic_backup)
            else Notice(context.getString(R.string.msg_backup_failed), R.drawable.ic_error)
        )
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()
        if (text != null && viewModel.importBackup(text)) {
            busy()
            onRestored()
        } else {
            notify(Notice(context.getString(R.string.msg_backup_invalid), R.drawable.ic_error))
        }
    }
    SettingsRow(
        iconRes = R.drawable.ic_backup,
        title = saveLabel,
        value = stringResource(R.string.backup_save_hint),
        modifier = Modifier.focusRequester(firstRowFocus),
        onClick = {
            if (runCatching { save.launch("otaghome-backup.json") }.isFailure) {
                notify(Notice(context.getString(R.string.msg_launch_failed, saveLabel), R.drawable.ic_error))
            }
        }
    )
    SettingsRow(
        iconRes = R.drawable.ic_restore,
        title = restoreLabel,
        value = stringResource(R.string.backup_restore_hint),
        onClick = {
            if (runCatching { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }.isFailure) {
                notify(Notice(context.getString(R.string.msg_launch_failed, restoreLabel), R.drawable.ic_error))
            }
        }
    )
}

/** Order of the Favorites and All apps tabs: the user's own, or by name either way. */
@Composable
private fun SortRows(
    favoritesSort: SortMode,
    appsSort: SortMode,
    onCycleFavoritesSort: () -> Unit,
    onCycleAppsSort: () -> Unit
) {
    SettingsRow(
        iconRes = R.drawable.ic_sort,
        title = stringResource(R.string.settings_sort_favorites),
        value = stringResource(favoritesSort.labelRes),
        onClick = onCycleFavoritesSort
    )
    SettingsRow(
        iconRes = R.drawable.ic_sort,
        title = stringResource(R.string.settings_sort_apps),
        value = stringResource(appsSort.labelRes),
        onClick = onCycleAppsSort
    )
}

/** Shortcuts into the system's language, network and full settings screens. */
@Composable
private fun SystemRows() {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val network by rememberNetworkStatus()
    val languageLabel = stringResource(R.string.settings_language)
    val networkLabel = stringResource(R.string.settings_network)
    val systemLabel = stringResource(R.string.settings_system)
    val open = rememberOpener()

    SettingsRow(
        iconRes = R.drawable.ic_language,
        title = languageLabel,
        value = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) },
        onClick = { open(Intent(Settings.ACTION_LOCALE_SETTINGS), languageLabel) }
    )
    SettingsRow(
        iconRes = network.iconRes,
        title = networkLabel,
        value = networkDescription(network),
        onClick = { open(context.wifiSettingsIntent(), networkLabel) }
    )
    SettingsRow(
        iconRes = R.drawable.ic_settings,
        title = systemLabel,
        value = null,
        onClick = { open(Intent(Settings.ACTION_SETTINGS), systemLabel) }
    )
}

@Composable
private fun backgroundLabel(spec: BackgroundSpec): String = when (spec) {
    is BackgroundSpec.Preset -> stringResource(spec.image.labelRes)
    is BackgroundSpec.Image -> stringResource(R.string.bg_image)
    is BackgroundSpec.Solid -> "${stringResource(R.string.bg_solid)} · ${HexColor.format(spec.color)}"
    is BackgroundSpec.Gradient -> "${stringResource(R.string.bg_gradient)} · ${HexColor.format(spec.start)} → ${HexColor.format(spec.end)}"
}

/** Which colour the picker on the Background page is choosing. */
private enum class PickTarget { SOLID, START, END }

/**
 * Background choices: OtagHome's own pictures, the user's picture, a solid colour or a gradient.
 * Colours open the colour picker; every choice applies at once.
 */
@Composable
private fun BackgroundPage(
    background: BackgroundSpec,
    veil: VeilLevel,
    onCycleVeil: () -> Unit,
    firstRowFocus: FocusRequester,
    onBackgroundChange: (BackgroundSpec) -> Unit,
    onOpenPresets: () -> Unit
) {
    val context = LocalContext.current
    val inUse = stringResource(R.string.bg_in_use)
    val chooseLabel = stringResource(R.string.bg_choose_image)
    val notify = LocalNotice.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            // Keep the right to read the picture after a reboot
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onBackgroundChange(BackgroundSpec.Image(uri.toString()))
        }
    }

    // The colours shown in the rows: the current ones, or sensible starting points
    val solidColor = (background as? BackgroundSpec.Solid)?.color ?: Palette.Dark.card.toArgb()
    val gradient = background as? BackgroundSpec.Gradient ?: BackgroundSpec.DEFAULT_GRADIENT
    var picking by remember { mutableStateOf<PickTarget?>(null) }

    SettingsRow(
        iconRes = R.drawable.ic_wallpaper,
        title = stringResource(R.string.bg_presets),
        value = (background as? BackgroundSpec.Preset)?.let { stringResource(it.image.labelRes) },
        modifier = Modifier.focusRequester(firstRowFocus),
        onClick = onOpenPresets
    )
    SettingsRow(
        iconRes = R.drawable.ic_image,
        title = chooseLabel,
        value = if (background is BackgroundSpec.Image) inUse else null,
        onClick = {
            val opened = runCatching { picker.launch(arrayOf("image/*")) }.isSuccess
            if (!opened) notify(Notice(context.getString(R.string.msg_launch_failed, chooseLabel), R.drawable.ic_error))
        }
    )
    // Only pictures are veiled, so the choice shows while one is the background
    if (background is BackgroundSpec.Preset || background is BackgroundSpec.Image) {
        SettingsRow(
            iconRes = R.drawable.ic_layers,
            title = stringResource(R.string.bg_veil),
            value = stringResource(veil.labelRes),
            onClick = onCycleVeil
        )
    }

    SectionLabel(stringResource(R.string.bg_solid), selected = background is BackgroundSpec.Solid)
    ColorRow(title = stringResource(R.string.bg_color), color = solidColor) { picking = PickTarget.SOLID }

    SectionLabel(stringResource(R.string.bg_gradient), selected = background is BackgroundSpec.Gradient)
    ColorRow(title = stringResource(R.string.bg_gradient_start), color = gradient.start) { picking = PickTarget.START }
    ColorRow(title = stringResource(R.string.bg_gradient_end), color = gradient.end) { picking = PickTarget.END }
    GradientPreview(gradient.start, gradient.end)
    SettingsRow(
        iconRes = R.drawable.ic_gradient,
        title = stringResource(R.string.bg_direction, stringResource(gradient.direction.labelRes)),
        value = null,
        onClick = { onBackgroundChange(gradient.copy(direction = gradient.direction.next())) }
    )

    picking?.let { target ->
        val (titleRes, initial) = when (target) {
            PickTarget.SOLID -> R.string.bg_color to solidColor
            PickTarget.START -> R.string.bg_gradient_start to gradient.start
            PickTarget.END -> R.string.bg_gradient_end to gradient.end
        }
        ColorPickerDialog(
            title = stringResource(titleRes),
            initial = initial,
            onPick = { color ->
                onBackgroundChange(
                    when (target) {
                        PickTarget.SOLID -> BackgroundSpec.Solid(color)
                        PickTarget.START -> gradient.copy(start = color)
                        PickTarget.END -> gradient.copy(end = color)
                    }
                )
            },
            onDismiss = { picking = null }
        )
    }
}

/** Every ready-made picture in two columns, grouped under their section names; choosing one applies it. */
@Composable
private fun PresetsGrid(
    background: BackgroundSpec,
    firstFocus: FocusRequester,
    onBackgroundChange: (BackgroundSpec) -> Unit
) {
    val first = PresetImage.entries.first()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        PresetGroup.entries.forEach { group ->
            val images = PresetImage.entries.filter { it.group == group }
            item(key = group.name, span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(
                    stringResource(group.labelRes),
                    selected = (background as? BackgroundSpec.Preset)?.image?.group == group
                )
            }
            items(images, key = { it.name }) { image ->
                PresetThumb(
                    image = image,
                    selected = background == BackgroundSpec.Preset(image),
                    modifier = if (image == first) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onBackgroundChange(BackgroundSpec.Preset(image)) }
                )
            }
        }
    }
}

/** A ready-made picture: its preview with its name; the one in use is framed in the accent. */
@Composable
private fun PresetThumb(image: PresetImage, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = Corners.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isFocused) palette.focus else palette.card)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) palette.accent else Color.Transparent,
                shape = shape
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(6.dp)
    ) {
        Image(
            painter = painterResource(image.drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(Corners.small)
        )
        Text(
            text = stringResource(image.labelRes),
            color = if (isFocused) palette.onFocus else palette.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
        )
    }
}

/**
 * Every theme colour with its current value: the shared accent, then the current theme's own.
 * Each opens the colour picker, whose Default button goes back to the theme's colour.
 */
@Composable
private fun ColorsPage(
    customColors: Map<ColorRole, Int>,
    firstRowFocus: FocusRequester,
    onColorChange: (ColorRole, Int?) -> Unit,
    onResetColors: () -> Unit
) {
    val palette = LocalPalette.current
    var picking by remember { mutableStateOf<ColorRole?>(null) }
    SectionLabel(stringResource(if (palette.isDark) R.string.colors_for_dark else R.string.colors_for_light), selected = false)
    ColorRole.entries.forEachIndexed { index, role ->
        ColorRow(
            title = stringResource(role.labelRes),
            color = role.of(palette).toArgb(),
            modifier = if (index == 0) Modifier.focusRequester(firstRowFocus) else Modifier
        ) { picking = role }
    }
    SettingsRow(
        iconRes = R.drawable.ic_palette,
        title = stringResource(R.string.colors_reset),
        value = null,
        onClick = onResetColors
    )
    picking?.let { role ->
        ColorPickerDialog(
            title = stringResource(role.labelRes),
            initial = customColors[role] ?: role.of(palette).toArgb(),
            onPick = { onColorChange(role, it) },
            onDismiss = { picking = null },
            onReset = { onColorChange(role, null) }
        )
    }
}

@Composable
private fun SectionLabel(text: String, selected: Boolean) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(LocalConfiguration.current.locales[0]),
            color = palette.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.bg_in_use), color = palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GradientPreview(start: Int, end: Int) {
    val colors = listOf(Color(start), Color(end))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(Corners.medium)
            .background(Brush.horizontalGradient(colors))
    )
}

@Composable
internal fun SettingsRow(
    @DrawableRes iconRes: Int,
    title: String,
    value: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val primary = if (isFocused) palette.onFocus else palette.textPrimary
    val secondary = if (isFocused) palette.onFocus.copy(alpha = 0.75f) else palette.textSecondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Corners.medium)
            .background(if (isFocused) palette.focus else palette.card)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, color = primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (value != null) {
                Text(text = value, color = secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

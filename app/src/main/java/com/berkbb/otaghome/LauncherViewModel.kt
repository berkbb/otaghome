package com.berkbb.otaghome

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Artwork of one app's card: the TV banner (if it has one), otherwise the logo, and the logo's
 * colour as the background when there is no banner.
 */
data class AppArt(val banner: ImageBitmap?, val icon: ImageBitmap?, val color: Int?)

data class LauncherUiState(
    val isLoading: Boolean = true,
    val apps: List<AppEntry> = emptyList(),
    val art: Map<String, AppArt> = emptyMap(),
    val favorites: List<String> = emptyList(),
    /** The user's order of the All apps tab; empty until they move an app there. */
    val appOrder: List<String> = emptyList(),
    val systemApps: SystemAppsMode = SystemAppsMode.TV_ONLY,
    val favoritesSort: SortMode = SortMode.CUSTOM,
    val appsSort: SortMode = SortMode.CUSTOM,
    /** Apps the user hid; they show nowhere until shown again from Settings. */
    val hidden: List<String> = emptyList(),
    val lastLaunched: Map<String, Long> = emptyMap(),
    val customLabels: Map<String, String> = emptyMap(),
    val categories: List<AppCategory> = emptyList(),
    val customArt: Map<String, String> = emptyMap()
)

/** How the home screen looks and opens. */
data class DisplaySettings(
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val cardSize: CardSize = CardSize.NORMAL,
    val showNames: Boolean = true,
    val startTab: StartTab = StartTab.AUTO,
    val lastTab: LauncherTab? = null,
    val mediaControls: Boolean = true,
    val cardOpacity: CardOpacity = CardOpacity.SOLID,
    val numberShortcuts: Boolean = true,
    val showSearch: Boolean = true,
    val showVoiceSearch: Boolean = true,
    val showInputs: Boolean = true,
    val showNetwork: Boolean = true,
    val autoUpdateInterval: UpdateCheckInterval = UpdateCheckInterval.OFF
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val PREFS_NAME = "otaghome_prefs"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_APP_ORDER = "app_order"
        private const val KEY_SYSTEM_APPS = "system_apps"
        private const val KEY_FAVORITES_SORT = "favorites_sort"
        private const val KEY_APPS_SORT = "apps_sort"
        private const val KEY_WEATHER_ENABLED = "weather_enabled"
        private const val KEY_WEATHER_PLACE = "weather_place"
        private const val KEY_WEATHER_IP_CONSENT = "weather_ip_consent"
        private const val KEY_WEATHER_INTERVAL = "weather_interval"
        private const val KEY_WEATHER_CACHE = "weather_cache"
        private const val KEY_WEATHER_UNIT = "weather_unit"
        private const val KEY_HIDDEN = "hidden_apps"
        private const val KEY_CLOCK_FORMAT = "clock_format"
        private const val KEY_CARD_SIZE = "card_size"
        private const val KEY_SHOW_NAMES = "show_names"
        private const val KEY_START_TAB = "start_tab"
        private const val KEY_LAST_TAB = "last_tab"
        private const val KEY_MEDIA_CONTROLS = "media_controls"
        private const val KEY_CARD_OPACITY = "card_opacity"
        private const val KEY_NUMBER_SHORTCUTS = "number_shortcuts"
        private const val KEY_SHOW_SEARCH = "show_search"
        private const val KEY_SHOW_VOICE_SEARCH = "show_voice_search"
        private const val KEY_SHOW_INPUTS = "show_inputs"
        private const val KEY_SHOW_NETWORK = "show_network"
        private const val KEY_AUTO_UPDATE_INTERVAL = "auto_update_interval"
        private const val KEY_UPDATE_LAST_CHECKED = "update_last_checked"
        private const val KEY_UPDATE_LAST_NOTIFIED = "update_last_notified"
        private const val KEY_LAST_LAUNCHED = "last_launched"
        private const val KEY_CUSTOM_LABELS = "custom_labels"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_CUSTOM_ART = "custom_art"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_LOCK_SETTINGS = "lock_settings"
        private const val KEY_LOCKED_APPS = "locked_apps"
        // Banners are 16:9 (320×180 dp in the platform guidelines); icons are drawn at most 96 dp
        private const val BANNER_WIDTH_PX = 480
        private const val BANNER_HEIGHT_PX = 270
        internal const val ICON_PX = 192
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private fun readUiPrefs(base: LauncherUiState) = base.copy(
        favorites = AppCatalog.decodePackages(prefs.getString(KEY_FAVORITES, null)),
        appOrder = AppCatalog.decodePackages(prefs.getString(KEY_APP_ORDER, null)),
        systemApps = SystemAppsMode.fromName(prefs.getString(KEY_SYSTEM_APPS, null)),
        favoritesSort = SortMode.fromName(prefs.getString(KEY_FAVORITES_SORT, null)),
        appsSort = SortMode.fromName(prefs.getString(KEY_APPS_SORT, null)),
        hidden = AppCatalog.decodePackages(prefs.getString(KEY_HIDDEN, null)),
        lastLaunched = AppCatalog.decodeTimestamps(prefs.getString(KEY_LAST_LAUNCHED, null)),
        customLabels = AppCatalog.decodeCustomLabels(prefs.getString(KEY_CUSTOM_LABELS, null)),
        categories = AppCatalog.decodeCategories(prefs.getString(KEY_CATEGORIES, null)),
        customArt = AppCatalog.decodeCustomArt(prefs.getString(KEY_CUSTOM_ART, null))
    )

    private fun readWeatherSettings() = WeatherSettings(
        enabled = prefs.getBoolean(KEY_WEATHER_ENABLED, true),
        place = WeatherStore.decodePlace(prefs.getString(KEY_WEATHER_PLACE, null)),
        ipConsent = if (prefs.contains(KEY_WEATHER_IP_CONSENT)) prefs.getBoolean(KEY_WEATHER_IP_CONSENT, false) else null,
        intervalMinutes = WeatherInterval.fromStored(prefs.getInt(KEY_WEATHER_INTERVAL, WeatherInterval.DEFAULT)),
        unit = TempUnit.fromName(prefs.getString(KEY_WEATHER_UNIT, null))
    )

    private fun readDisplay() = DisplaySettings(
        clockFormat = ClockFormat.fromName(prefs.getString(KEY_CLOCK_FORMAT, null)),
        cardSize = CardSize.fromName(prefs.getString(KEY_CARD_SIZE, null)),
        showNames = prefs.getBoolean(KEY_SHOW_NAMES, true),
        startTab = StartTab.fromName(prefs.getString(KEY_START_TAB, null)),
        lastTab = LauncherTab.entries.firstOrNull { it.name == prefs.getString(KEY_LAST_TAB, null) },
        mediaControls = prefs.getBoolean(KEY_MEDIA_CONTROLS, true),
        cardOpacity = CardOpacity.fromName(prefs.getString(KEY_CARD_OPACITY, null)),
        numberShortcuts = prefs.getBoolean(KEY_NUMBER_SHORTCUTS, true),
        showSearch = prefs.getBoolean(KEY_SHOW_SEARCH, true),
        showVoiceSearch = prefs.getBoolean(KEY_SHOW_VOICE_SEARCH, true),
        showInputs = prefs.getBoolean(KEY_SHOW_INPUTS, true),
        showNetwork = prefs.getBoolean(KEY_SHOW_NETWORK, true),
        autoUpdateInterval = UpdateCheckInterval.fromName(prefs.getString(KEY_AUTO_UPDATE_INTERVAL, null))
    )

    private fun readLockSettings() = LockSettings(
        pinHash = prefs.getString(KEY_PIN_HASH, null),
        pinSalt = prefs.getString(KEY_PIN_SALT, null),
        lockSettings = prefs.getBoolean(KEY_LOCK_SETTINGS, false),
        lockedApps = AppCatalog.decodePackages(prefs.getString(KEY_LOCKED_APPS, null)).toSet()
    )

    private val _uiState = MutableStateFlow(readUiPrefs(LauncherUiState()))
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private val _lockSettings = MutableStateFlow(readLockSettings())
    val lockSettings: StateFlow<LockSettings> = _lockSettings.asStateFlow()

    private var loadJob: Job? = null
    private var pendingRefresh = false
    /** Package → activity and last update time the current art was rendered for. */
    private var artStamps: Map<String, String> = emptyMap()
    private val launcherApps = application.getSystemService(LauncherApps::class.java)
    private var launcherCallbackRegistered = false
    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refreshApps()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refreshApps()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refreshApps()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refreshApps()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refreshApps()
    }

    private val _weatherSettings = MutableStateFlow(readWeatherSettings())
    val weatherSettings: StateFlow<WeatherSettings> = _weatherSettings.asStateFlow()

    /** The last weather fetched, kept across restarts so it shows at once and while offline. */
    private val _weather = MutableStateFlow(WeatherStore.decodeWeather(prefs.getString(KEY_WEATHER_CACHE, null)))
    val weather: StateFlow<WeatherNow?> = _weather.asStateFlow()

    private var weatherJob: Job? = null
    /** The place found from the internet connection, looked up once per run. */
    private var autoPlace: Place? = null

    /**
     * Fetches the weather when the chosen interval has passed since the last fetch, or now when
     * [force]d. Does nothing without somewhere to fetch for; the internet address is only used
     * to find the place when the user has allowed it.
     */
    fun refreshWeather(force: Boolean = false) {
        val settings = _weatherSettings.value
        if (!settings.canFetch || weatherJob?.isActive == true) return
        val last = _weather.value
        val fresh = last != null && System.currentTimeMillis() - last.fetchedAt < settings.intervalMinutes * 60_000L
        if (fresh && !force) return
        weatherJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val place = settings.place ?: autoPlace ?: WeatherClient.locate()?.also { autoPlace = it }
                place?.let { WeatherClient.fetchWeather(it, System.currentTimeMillis()) }
            }
            if (result != null) {
                _weather.value = result
                prefs.edit { putString(KEY_WEATHER_CACHE, WeatherStore.encodeWeather(result)) }
            }
        }
    }

    /** Null when the search could not be made; empty when nothing matched. Names come in [language]. */
    suspend fun searchCities(query: String, language: String): List<Place>? =
        withContext(Dispatchers.IO) { WeatherClient.search(query, language) }

    fun setWeatherEnabled(enabled: Boolean) = updateWeather(_weatherSettings.value.copy(enabled = enabled))

    /** A chosen city, or null to find the place from the internet connection. */
    fun setWeatherPlace(place: Place?) {
        updateWeather(_weatherSettings.value.copy(place = place), placeChanged = true)
    }

    fun setIpConsent(allowed: Boolean) {
        if (!allowed) autoPlace = null
        updateWeather(_weatherSettings.value.copy(ipConsent = allowed), placeChanged = _weatherSettings.value.place == null)
    }

    fun cycleTempUnit() = updateWeather(_weatherSettings.value.copy(unit = _weatherSettings.value.unit.next()))

    fun cycleWeatherInterval() =
        updateWeather(_weatherSettings.value.copy(intervalMinutes = WeatherInterval.next(_weatherSettings.value.intervalMinutes)))

    private fun updateWeather(settings: WeatherSettings, placeChanged: Boolean = false) {
        _weatherSettings.value = settings
        prefs.edit {
            putBoolean(KEY_WEATHER_ENABLED, settings.enabled)
            settings.place?.let { putString(KEY_WEATHER_PLACE, WeatherStore.encodePlace(it)) } ?: remove(KEY_WEATHER_PLACE)
            settings.ipConsent?.let { putBoolean(KEY_WEATHER_IP_CONSENT, it) } ?: remove(KEY_WEATHER_IP_CONSENT)
            putInt(KEY_WEATHER_INTERVAL, settings.intervalMinutes)
            putString(KEY_WEATHER_UNIT, settings.unit.name)
            // The old place's weather must not show under the new one
            if (placeChanged) remove(KEY_WEATHER_CACHE)
        }
        if (placeChanged) {
            weatherJob?.cancel()
            _weather.value = null
        }
        refreshWeather(force = placeChanged)
    }

    /** Re-reads the installed apps and refreshes status monitors on resume. */
    fun refresh() {
        mediaMonitor.refresh()
        networkMonitor.refresh()
        refreshApps()
    }

    private fun refreshApps() {
        if (loadJob?.isActive == true) {
            pendingRefresh = true
            return
        }
        pendingRefresh = false
        val previousArt = _uiState.value.art
        val previousStamps = artStamps
        loadJob = viewModelScope.launch {
            val (apps, loaded) = withContext(Dispatchers.IO) { loadApps(previousArt, previousStamps) }
            artStamps = loaded.stamps
            _uiState.update { it.copy(isLoading = false, apps = apps, art = loaded.art) }
            if (pendingRefresh) {
                pendingRefresh = false
                refreshApps()
            }
        }
    }

    private val mediaMonitor = NowPlayingMonitor(application)
    val nowPlaying: StateFlow<NowPlaying?> = mediaMonitor.state
    val hasMediaAccess: StateFlow<Boolean> = mediaMonitor.hasAccess

    private val networkMonitor = NetworkMonitor(application)
    val networkStatus: StateFlow<NetworkStatus> = networkMonitor.status

    fun setScreenActive(active: Boolean) {
        networkMonitor.setActive(active)
        mediaMonitor.setForeground(active)
    }

    private val _display = MutableStateFlow(readDisplay())
    val display: StateFlow<DisplaySettings> = _display.asStateFlow()

    private fun updateDisplay(change: (DisplaySettings) -> DisplaySettings) {
        val next = change(_display.value)
        _display.value = next
        prefs.edit {
            putString(KEY_CLOCK_FORMAT, next.clockFormat.name)
            putString(KEY_CARD_SIZE, next.cardSize.name)
            putBoolean(KEY_SHOW_NAMES, next.showNames)
            putString(KEY_START_TAB, next.startTab.name)
            next.lastTab?.let { putString(KEY_LAST_TAB, it.name) }
            putBoolean(KEY_MEDIA_CONTROLS, next.mediaControls)
            putString(KEY_CARD_OPACITY, next.cardOpacity.name)
            putBoolean(KEY_NUMBER_SHORTCUTS, next.numberShortcuts)
            putBoolean(KEY_SHOW_SEARCH, next.showSearch)
            putBoolean(KEY_SHOW_VOICE_SEARCH, next.showVoiceSearch)
            putBoolean(KEY_SHOW_INPUTS, next.showInputs)
            putBoolean(KEY_SHOW_NETWORK, next.showNetwork)
            putString(KEY_AUTO_UPDATE_INTERVAL, next.autoUpdateInterval.name)
        }
    }

    fun cycleClockFormat() = updateDisplay { it.copy(clockFormat = it.clockFormat.next()) }
    fun cycleCardSize() = updateDisplay { it.copy(cardSize = it.cardSize.next()) }
    fun toggleShowNames() = updateDisplay { it.copy(showNames = !it.showNames) }
    fun cycleStartTab() = updateDisplay { it.copy(startTab = it.startTab.next()) }
    fun cycleCardOpacity() = updateDisplay { it.copy(cardOpacity = it.cardOpacity.next()) }
    fun toggleNumberShortcuts() = updateDisplay { it.copy(numberShortcuts = !it.numberShortcuts) }
    fun toggleShowSearch() = updateDisplay { it.copy(showSearch = !it.showSearch) }
    fun toggleShowVoiceSearch() = updateDisplay { it.copy(showVoiceSearch = !it.showVoiceSearch) }
    fun toggleShowInputs() = updateDisplay { it.copy(showInputs = !it.showInputs) }
    fun toggleShowNetwork() = updateDisplay { it.copy(showNetwork = !it.showNetwork) }
    fun cycleAutoUpdateInterval() = updateDisplay { it.copy(autoUpdateInterval = it.autoUpdateInterval.next()) }

    fun setMediaControls(enabled: Boolean) {
        updateDisplay { it.copy(mediaControls = enabled) }
        mediaMonitor.setEnabled(enabled)
    }

    fun refreshMedia() = mediaMonitor.refresh()
    fun mediaPlayPause() = mediaMonitor.togglePlayPause()
    fun mediaPrevious() = mediaMonitor.skipPrevious()
    fun mediaNext() = mediaMonitor.skipNext()
    fun mediaSwitchSession() = mediaMonitor.switchSession()
    fun mediaToggleMute() = mediaMonitor.toggleMute()
    fun mediaStopOrDismiss() = mediaMonitor.stopOrDismiss()
    fun mediaOpenApp(): Boolean = mediaMonitor.openApp()

    /** Remembered for the "last used" start tab. */
    fun rememberTab(tab: LauncherTab) {
        if (_display.value.lastTab != tab) updateDisplay { it.copy(lastTab = tab) }
    }

    /** Hides the app everywhere, or shows it again; returns true when it is now hidden. */
    fun toggleHidden(packageName: String): Boolean {
        val updated = AppCatalog.toggleFavorite(_uiState.value.hidden, packageName)
        prefs.edit { putString(KEY_HIDDEN, AppCatalog.encodePackages(updated)) }
        _uiState.update { it.copy(hidden = updated) }
        return packageName in updated
    }

    /** Every setting as a backup file's text (see [Backup]). */
    fun exportBackup(): String = Backup.encode(prefs.all)

    /**
     * Replaces every setting with the backup's and reloads them; false when the text is not an
     * OtagHome backup. The screen's own settings are read again when the activity is recreated.
     */
    fun importBackup(json: String): Boolean {
        val values = Backup.decode(json) ?: return false
        prefs.edit(commit = true) {
            clear()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                }
            }
        }
        getApplication<Application>().clearInternalImageCaches()
        _uiState.update { readUiPrefs(it) }
        _weatherSettings.value = readWeatherSettings()
        _weather.value = null
        autoPlace = null
        _display.value = readDisplay()
        _lockSettings.value = readLockSettings()
        _autoUpdateNotice.value = null
        mediaMonitor.setEnabled(_display.value.mediaControls)
        mediaMonitor.refresh()
        refreshWeather(force = true)
        return true
    }

    /** Wipes every preference and internal cached cover/image, returning the launcher to defaults. */
    fun resetAllSettings() {
        prefs.edit(commit = true) { clear() }
        getApplication<Application>().clearInternalImageCaches()
        _uiState.update { readUiPrefs(it) }
        _weatherSettings.value = readWeatherSettings()
        _weather.value = null
        autoPlace = null
        _display.value = readDisplay()
        _lockSettings.value = readLockSettings()
        _autoUpdateNotice.value = null
        mediaMonitor.setEnabled(_display.value.mediaControls)
        mediaMonitor.refresh()
        refreshApps()
    }

    private val _update = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val update: StateFlow<UpdateStatus> = _update.asStateFlow()
    private val _updateInstall = MutableStateFlow<UpdateInstall>(UpdateInstall.Idle)
    val updateInstall: StateFlow<UpdateInstall> = _updateInstall.asStateFlow()
    private val _autoUpdateNotice = MutableStateFlow<String?>(null)
    val autoUpdateNotice: StateFlow<String?> = _autoUpdateNotice.asStateFlow()
    private var autoCheckJob: Job? = null

    init {
        mediaMonitor.setEnabled(_display.value.mediaControls)
        runCatching {
            launcherApps?.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
            launcherCallbackRegistered = launcherApps != null
        }
        viewModelScope.launch(Dispatchers.IO) {
            SelfUpdate.cleanCachedApks(application.cacheDir)
        }
        viewModelScope.launch {
            MediaListenerService.connected.collect { mediaMonitor.refresh() }
        }
        viewModelScope.launch {
            InstallFailures.events.collect { _updateInstall.value = UpdateInstall.Failed(R.string.update_install_failed) }
        }
    }

    override fun onCleared() {
        if (launcherCallbackRegistered) {
            runCatching { launcherApps?.unregisterCallback(packageCallback) }
            launcherCallbackRegistered = false
        }
        networkMonitor.clear()
        mediaMonitor.clear()
        super.onCleared()
    }

    fun checkForUpdates() {
        if (_update.value == UpdateStatus.Checking) return
        _update.value = UpdateStatus.Checking
        _updateInstall.value = UpdateInstall.Idle
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { SelfUpdate.check(BuildConfig.VERSION_NAME) }
            prefs.edit { putLong(KEY_UPDATE_LAST_CHECKED, System.currentTimeMillis()) }
            _update.value = result
        }
    }

    /** Optional background check run on start when enabled in Settings → Display. */
    fun maybeAutoCheckUpdate() {
        val interval = _display.value.autoUpdateInterval
        val lastChecked = prefs.getLong(KEY_UPDATE_LAST_CHECKED, 0L)
        if (!UpdateSchedule.shouldCheck(interval, lastChecked, System.currentTimeMillis())) return
        if (_update.value == UpdateStatus.Checking || autoCheckJob?.isActive == true) return
        autoCheckJob = viewModelScope.launch {
            val status = withContext(Dispatchers.IO) { SelfUpdate.check(BuildConfig.VERSION_NAME) }
            prefs.edit { putLong(KEY_UPDATE_LAST_CHECKED, System.currentTimeMillis()) }
            _update.value = status
            if (status is UpdateStatus.Available) {
                val lastNotified = prefs.getString(KEY_UPDATE_LAST_NOTIFIED, null)
                if (UpdateSchedule.shouldNotify(status.latest, lastNotified)) {
                    prefs.edit { putString(KEY_UPDATE_LAST_NOTIFIED, status.latest) }
                    _autoUpdateNotice.value = status.latest
                }
            }
        }
    }

    fun clearAutoUpdateNotice() {
        _autoUpdateNotice.value = null
    }

    /**
     * Downloads the newer release, checks its digest and hands it to the system installer. Without
     * the right to install apps, opens that setting for OtagHome first.
     */
    fun installUpdate() {
        val apk = (_update.value as? UpdateStatus.Available)?.apk ?: return
        if (_updateInstall.value is UpdateInstall.Downloading) return
        val app = getApplication<Application>()
        if (!app.packageManager.canRequestPackageInstalls()) {
            _updateInstall.value = UpdateInstall.Failed(R.string.update_permission)
            runCatching {
                app.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        viewModelScope.launch {
            _updateInstall.value = UpdateInstall.Downloading(0)
            val target = java.io.File(app.cacheDir, "otaghome-${apk.tag}.apk")
            val downloaded = withContext(Dispatchers.IO) {
                SelfUpdate.download(apk, target) { progress -> _updateInstall.value = UpdateInstall.Downloading(progress) }
            }
            if (!downloaded) {
                _updateInstall.value = UpdateInstall.Failed(R.string.update_download_failed)
                return@launch
            }
            // Reopen OtagHome once the system has replaced it
            app.getSharedPreferences(SelfUpdateRestartReceiver.PREFS, Context.MODE_PRIVATE)
                .edit { putBoolean(SelfUpdateRestartReceiver.KEY_PENDING, true) }
            val started = withContext(Dispatchers.IO) { SelfUpdate.install(app, target) }
            _updateInstall.value = if (started) UpdateInstall.WaitingForConfirmation else UpdateInstall.Failed(R.string.update_install_failed)
        }
    }

    /** Steps to the next choice of which system apps are listed. */
    fun cycleSystemApps() {
        val next = _uiState.value.systemApps.next()
        prefs.edit { putString(KEY_SYSTEM_APPS, next.name) }
        _uiState.update { it.copy(systemApps = next) }
    }

    fun cycleFavoritesSort() {
        val next = _uiState.value.favoritesSort.next()
        prefs.edit { putString(KEY_FAVORITES_SORT, next.name) }
        _uiState.update { it.copy(favoritesSort = next) }
    }

    fun cycleAppsSort() {
        val next = _uiState.value.appsSort.next()
        prefs.edit { putString(KEY_APPS_SORT, next.name) }
        _uiState.update { it.copy(appsSort = next) }
    }

    /** Returns true when the app is now a favorite. */
    fun toggleFavorite(packageName: String): Boolean {
        val updated = AppCatalog.toggleFavorite(_uiState.value.favorites, packageName)
        prefs.edit { putString(KEY_FAVORITES, AppCatalog.encodePackages(updated)) }
        _uiState.update { it.copy(favorites = updated) }
        return packageName in updated
    }

    /**
     * Moves a favorite by [delta] places within [shown], the favorites on screen, and returns its
     * new position there. Favorites whose app is not installed now keep their place at the end.
     */
    fun moveFavorite(packageName: String, delta: Int, shown: List<String>): Int {
        val moved = AppCatalog.move(shown, packageName, delta)
        val updated = moved + (_uiState.value.favorites - shown.toSet())
        prefs.edit { putString(KEY_FAVORITES, AppCatalog.encodePackages(updated)) }
        _uiState.update { it.copy(favorites = updated) }
        return moved.indexOf(packageName)
    }

    /**
     * Moves an app on the All apps tab by [delta] places within [shown], the order on screen, and
     * keeps that whole order from then on. Returns the app's new position.
     */
    fun moveApp(packageName: String, delta: Int, shown: List<String>): Int {
        val updated = AppCatalog.move(shown, packageName, delta)
        prefs.edit { putString(KEY_APP_ORDER, AppCatalog.encodePackages(updated)) }
        _uiState.update { it.copy(appOrder = updated) }
        return updated.indexOf(packageName)
    }

    /** Sets or clears a custom display name for [packageName]. */
    fun setCustomLabel(packageName: String, label: String?) {
        val cleaned = label?.replace('\t', ' ')?.replace('\n', ' ')?.replace('\r', ' ')?.trim()?.take(40)
        val updated = if (cleaned.isNullOrEmpty()) {
            _uiState.value.customLabels - packageName
        } else {
            _uiState.value.customLabels + (packageName to cleaned)
        }
        prefs.edit {
            if (updated.isEmpty()) remove(KEY_CUSTOM_LABELS)
            else putString(KEY_CUSTOM_LABELS, AppCatalog.encodeCustomLabels(updated))
        }
        _uiState.update { it.copy(customLabels = updated) }
    }

    /** Creates a new user category tab; false when at the limit, blank or duplicate. */
    fun addCategory(name: String): Boolean {
        val current = _uiState.value.categories
        val id = "cat_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(4)}"
        val updated = AppCatalog.addCategory(current, name, id)
        if (updated == current) return false
        saveCategories(updated)
        return true
    }

    fun renameCategory(id: String, newName: String): Boolean {
        val current = _uiState.value.categories
        val updated = AppCatalog.renameCategory(current, id, newName)
        if (updated == current) return false
        saveCategories(updated)
        return true
    }

    fun deleteCategory(id: String) {
        val updated = AppCatalog.deleteCategory(_uiState.value.categories, id)
        saveCategories(updated)
    }

    fun toggleAppCategory(categoryId: String, packageName: String): Boolean {
        val updated = AppCatalog.toggleAppInCategory(_uiState.value.categories, categoryId, packageName)
        saveCategories(updated)
        return updated.firstOrNull { it.id == categoryId }?.packages?.contains(packageName) == true
    }

    fun moveInCategory(categoryId: String, packageName: String, delta: Int, shown: List<String>): Int {
        val (updated, index) = AppCatalog.moveInCategory(_uiState.value.categories, categoryId, packageName, delta, shown)
        if (index >= 0) saveCategories(updated)
        return index
    }

    private fun saveCategories(updated: List<AppCategory>) {
        prefs.edit {
            if (updated.isEmpty()) remove(KEY_CATEGORIES)
            else putString(KEY_CATEGORIES, AppCatalog.encodeCategories(updated))
        }
        _uiState.update { it.copy(categories = updated) }
    }

    /** Assigns a custom 16:9 card cover copied into app storage; returns false if unreadable. */
    suspend fun setCustomArt(packageName: String, sourceUriString: String): Boolean {
        val app = getApplication<Application>()
        val imported = withContext(Dispatchers.IO) { app.importAppCover(packageName, sourceUriString) } ?: return false
        val previous = _uiState.value.customArt[packageName]
        val updated = _uiState.value.customArt + (packageName to imported)
        prefs.edit { putString(KEY_CUSTOM_ART, AppCatalog.encodeCustomArt(updated)) }
        _uiState.update { it.copy(customArt = updated) }
        if (previous != null && previous != imported) {
            withContext(Dispatchers.IO) { app.cleanupCustomAppCover(previous) }
        }
        refreshApps()
        return true
    }

    /** Restores the app's own banner or icon by removing its custom cover. */
    fun clearCustomArt(packageName: String) {
        val app = getApplication<Application>()
        val previous = _uiState.value.customArt[packageName] ?: return
        val updated = _uiState.value.customArt - packageName
        prefs.edit {
            if (updated.isEmpty()) remove(KEY_CUSTOM_ART)
            else putString(KEY_CUSTOM_ART, AppCatalog.encodeCustomArt(updated))
        }
        _uiState.update { it.copy(customArt = updated) }
        viewModelScope.launch(Dispatchers.IO) { app.cleanupCustomAppCover(previous) }
        refreshApps()
    }

    fun verifyPin(pin: String): Boolean {
        val current = _lockSettings.value
        return current.hasPin && PinLock.verify(pin, current.pinSalt!!, current.pinHash!!)
    }

    fun setPin(pin: String) {
        if (!PinLock.isValidPin(pin)) return
        val salt = PinLock.generateSalt()
        val hash = PinLock.hash(pin, salt)
        prefs.edit {
            putString(KEY_PIN_SALT, salt)
            putString(KEY_PIN_HASH, hash)
        }
        _lockSettings.update { it.copy(pinSalt = salt, pinHash = hash) }
    }

    fun removePin() {
        prefs.edit {
            remove(KEY_PIN_HASH)
            remove(KEY_PIN_SALT)
            remove(KEY_LOCK_SETTINGS)
            remove(KEY_LOCKED_APPS)
        }
        _lockSettings.value = LockSettings()
    }

    fun toggleLockSettings() {
        if (!_lockSettings.value.hasPin) return
        val next = !_lockSettings.value.lockSettings
        prefs.edit { putBoolean(KEY_LOCK_SETTINGS, next) }
        _lockSettings.update { it.copy(lockSettings = next) }
    }

    fun toggleAppLock(packageName: String): Boolean {
        val current = _lockSettings.value
        if (!current.hasPin) return false
        val nextLocked = if (packageName in current.lockedApps) current.lockedApps - packageName else current.lockedApps + packageName
        prefs.edit {
            if (nextLocked.isEmpty()) remove(KEY_LOCKED_APPS)
            else putString(KEY_LOCKED_APPS, AppCatalog.encodePackages(nextLocked.toList()))
        }
        _lockSettings.update { it.copy(lockedApps = nextLocked) }
        return packageName in nextLocked
    }

    fun launch(entry: AppEntry): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(if (entry.isTvApp) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            component = ComponentName(entry.packageName, entry.className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        val launched = runCatching { getApplication<Application>().startActivity(intent) }.isSuccess
        if (launched) {
            val updated = AppCatalog.recordLaunch(_uiState.value.lastLaunched, entry.packageName, System.currentTimeMillis())
            prefs.edit { putString(KEY_LAST_LAUNCHED, AppCatalog.encodeTimestamps(updated)) }
            _uiState.update { it.copy(lastLaunched = updated) }
        }
        return launched
    }

    private class LoadedArt(val art: Map<String, AppArt>, val stamps: Map<String, String>)

    /**
     * Reads the installed apps and their art. Art of an app that has not changed since [previousStamps]
     * is reused: rendering every banner again on each resume kept a second copy of all of them.
     */
    private fun loadApps(previousArt: Map<String, AppArt>, previousStamps: Map<String, String>): Pair<List<AppEntry>, LoadedArt> {
        val pm = getApplication<Application>().packageManager
        val self = getApplication<Application>().packageName
        val tvInfos = query(pm, Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phoneInfos = query(pm, Intent.CATEGORY_LAUNCHER)
        val infoByKey = (tvInfos + phoneInfos).associateBy { it.activityInfo.packageName to it.activityInfo.name }

        fun toEntry(info: ResolveInfo, isTv: Boolean): AppEntry {
            val flags = info.activityInfo.applicationInfo.flags
            return AppEntry(
                packageName = info.activityInfo.packageName,
                className = info.activityInfo.name,
                label = info.loadLabel(pm).toString().trim().ifEmpty { info.activityInfo.packageName },
                isTvApp = isTv,
                isSystemApp = AppCatalog.isSystemImageApp(
                    isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    isUpdatedSystem = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                )
            )
        }

        val apps = AppCatalog.merge(
            tvApps = tvInfos.map { toEntry(it, isTv = true) },
            phoneApps = phoneInfos.map { toEntry(it, isTv = false) },
            selfPackage = self
        )
        val app = getApplication<Application>()
        val customArtMap = _uiState.value.customArt
        val stamps = apps.associate { entry ->
            val updated = runCatching { pm.getPackageInfo(entry.packageName, 0).lastUpdateTime }.getOrDefault(0L)
            val customUri = customArtMap[entry.packageName].orEmpty()
            entry.packageName to "${entry.className}@$updated|$customUri"
        }
        // A broken drawable from one app must not take the whole launcher down
        val art = apps.associate { entry ->
            val kept = previousArt[entry.packageName]?.takeIf { previousStamps[entry.packageName] == stamps[entry.packageName] }
            if (kept != null) return@associate entry.packageName to kept
            val customBitmap = customArtMap[entry.packageName]?.let { uri ->
                app.decodeScaledBitmap(uri, BANNER_WIDTH_PX, BANNER_HEIGHT_PX)?.asImageBitmap()
            }
            if (customBitmap != null) {
                return@associate entry.packageName to AppArt(banner = customBitmap, icon = null, color = null)
            }
            val info = infoByKey[entry.packageName to entry.className]
            val banner = info?.let { runCatching { it.activityInfo.loadBanner(pm) ?: it.activityInfo.applicationInfo.loadBanner(pm) }.getOrNull() }
            val icon = info?.let { runCatching { it.loadIcon(pm) }.getOrNull() }
            val iconBitmap = icon?.let { runCatching { it.toBitmap(ICON_PX, ICON_PX) }.getOrNull() }
            val color = iconBitmap?.let { bmp ->
                IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
            }?.let(ArtColor::dominant)
            val bannerBitmap = banner?.render(BANNER_WIDTH_PX, BANNER_HEIGHT_PX)
            entry.packageName to AppArt(
                banner = bannerBitmap,
                // The card draws the logo only without a banner; keeping it anyway costs ~150 KB per app
                icon = if (bannerBitmap == null) iconBitmap?.asImageBitmap() else null,
                color = color
            )
        }
        return apps to LoadedArt(art, stamps)
    }

    private fun query(pm: PackageManager, category: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
    }

    private fun Drawable.render(width: Int, height: Int): ImageBitmap? =
        runCatching { toBitmap(width, height).asImageBitmap() }.getOrNull()
}

package com.berkbb.otaghome

import androidx.annotation.StringRes
import java.text.Collator

/** A launchable activity, independent of Android types so the ordering rules are unit-testable. */
data class AppEntry(
    val packageName: String,
    val className: String,
    val label: String,
    /** Declares LEANBACK_LAUNCHER, i.e. built for TV. */
    val isTvApp: Boolean,
    /** Part of the TV's system image and never updated from a store (live TV, factory menu, WebView…). */
    val isSystemApp: Boolean = false
)

/** Which system-image apps the launcher lists; chosen in Settings. */
enum class SystemAppsMode(@param:StringRes val labelRes: Int) {
    /** Built into the TV for the TV screen (live TV, media player, channel editor), like the stock launcher. */
    TV_ONLY(R.string.sys_apps_tv),
    /** No system-image apps at all. */
    HIDE(R.string.sys_apps_hide),
    /** Every launchable app, the factory menu and WebView included. */
    ALL(R.string.sys_apps_all);

    fun next(): SystemAppsMode = entries[(ordinal + 1) % entries.size]

    fun shows(entry: AppEntry): Boolean = when (this) {
        TV_ONLY -> !entry.isSystemApp || entry.isTvApp
        HIDE -> !entry.isSystemApp
        ALL -> true
    }

    companion object {
        fun fromName(name: String?): SystemAppsMode = entries.firstOrNull { it.name == name } ?: TV_ONLY
    }
}

/** How a tab is ordered; chosen in Settings for Favorites and All apps separately. */
enum class SortMode(@param:StringRes val labelRes: Int) {
    /** The user's own order, changed with Move (All apps: alphabetical until the first move). */
    CUSTOM(R.string.sort_custom),
    NAME_ASC(R.string.sort_name_asc),
    NAME_DESC(R.string.sort_name_desc);

    fun next(): SortMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): SortMode = entries.firstOrNull { it.name == name } ?: CUSTOM
    }
}

object AppCatalog {

    /**
     * A system-image app that was never updated from a store. Preinstalled apps the store keeps up
     * to date (Netflix, YouTube, Play Store) count as the user's.
     */
    fun isSystemImageApp(isSystem: Boolean, isUpdatedSystem: Boolean): Boolean = isSystem && !isUpdatedSystem

    /** The apps the launcher lists under [mode]. */
    fun visible(apps: List<AppEntry>, mode: SystemAppsMode): List<AppEntry> = apps.filter(mode::shows)

    /**
     * One entry per package: the TV activity wins over a phone LAUNCHER activity of the same app.
     * The launcher itself is left out.
     */
    fun merge(tvApps: List<AppEntry>, phoneApps: List<AppEntry>, selfPackage: String): List<AppEntry> {
        val byPackage = LinkedHashMap<String, AppEntry>()
        (tvApps + phoneApps).forEach { entry ->
            if (entry.packageName != selfPackage && entry.packageName !in byPackage) {
                byPackage[entry.packageName] = entry
            }
        }
        return byPackage.values.toList()
    }

    /** Apps sorted by label with the UI language's collator. */
    fun allApps(apps: List<AppEntry>, collator: Collator): List<AppEntry> =
        apps.sortedWith(compareBy(collator) { it.label })

    /**
     * The All apps tab: apps the user has placed come first in their order; the rest (all of
     * them until the first move, later new installs) follow alphabetically.
     */
    fun ordered(apps: List<AppEntry>, order: List<String>, collator: Collator): List<AppEntry> {
        val byPackage = apps.associateBy { it.packageName }
        val placed = order.mapNotNull { byPackage[it] }
        val placedSet = placed.map { it.packageName }.toSet()
        return placed + allApps(apps.filter { it.packageName !in placedSet }, collator)
    }

    /** [customOrder] (the tab's own order) when [mode] is CUSTOM, otherwise [apps] sorted by name. */
    fun sorted(apps: List<AppEntry>, mode: SortMode, collator: Collator, customOrder: () -> List<AppEntry>): List<AppEntry> =
        when (mode) {
            SortMode.CUSTOM -> customOrder()
            SortMode.NAME_ASC -> allApps(apps, collator)
            SortMode.NAME_DESC -> allApps(apps, collator).asReversed()
        }

    /** Moves [item] by [delta] places, stopping at either end; a missing item changes nothing. */
    fun move(list: List<String>, item: String, delta: Int): List<String> {
        val from = list.indexOf(item)
        if (from < 0) return list
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return list
        return list.toMutableList().apply {
            removeAt(from)
            add(to, item)
        }
    }

    /** The Favorites tab: in the user's order; uninstalled favorites are skipped. */
    fun favoriteApps(apps: List<AppEntry>, favorites: List<String>): List<AppEntry> {
        val byPackage = apps.associateBy { it.packageName }
        return favorites.mapNotNull { byPackage[it] }
    }

    /** Adds the package at the end of Favorites, or removes it if it is already there. */
    fun toggleFavorite(favorites: List<String>, packageName: String): List<String> =
        if (packageName in favorites) favorites - packageName else favorites + packageName

    /** Package lists (Favorites, the All apps order) are stored as one string; package names never contain a newline. */
    fun encodePackages(packages: List<String>): String = packages.joinToString("\n")

    fun decodePackages(raw: String?): List<String> =
        raw.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

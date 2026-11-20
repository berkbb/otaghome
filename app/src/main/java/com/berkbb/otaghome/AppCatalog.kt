package com.berkbb.otaghome

import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator

/** User-defined category tab holding an ordered list of package names. */
data class AppCategory(val id: String, val name: String, val packages: List<String> = emptyList())

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
    NAME_DESC(R.string.sort_name_desc),
    RECENT(R.string.sort_recent);

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

    /** Apps sorted by most recent launch first, then alphabetically for never-launched apps. */
    fun recentApps(apps: List<AppEntry>, lastLaunched: Map<String, Long>, collator: Collator): List<AppEntry> =
        apps.sortedWith { a, b ->
            val ta = lastLaunched[a.packageName]
            val tb = lastLaunched[b.packageName]
            when {
                ta != null && tb != null -> tb.compareTo(ta).let { if (it != 0) it else collator.compare(a.label, b.label) }
                ta != null -> -1
                tb != null -> 1
                else -> collator.compare(a.label, b.label)
            }
        }

    /** [customOrder] (the tab's own order) when [mode] is CUSTOM, otherwise sorted by name or recency. */
    fun sorted(
        apps: List<AppEntry>,
        mode: SortMode,
        collator: Collator,
        lastLaunched: Map<String, Long> = emptyMap(),
        customOrder: () -> List<AppEntry>
    ): List<AppEntry> = when (mode) {
        SortMode.CUSTOM -> customOrder()
        SortMode.NAME_ASC -> allApps(apps, collator)
        SortMode.NAME_DESC -> allApps(apps, collator).asReversed()
        SortMode.RECENT -> recentApps(apps, lastLaunched, collator)
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

    fun recordLaunch(timestamps: Map<String, Long>, packageName: String, now: Long): Map<String, Long> =
        if (packageName.isBlank() || now <= 0L) timestamps else timestamps + (packageName to now)

    fun encodeTimestamps(timestamps: Map<String, Long>): String =
        timestamps.entries
            .filter { it.key.isNotBlank() && it.value > 0L }
            .sortedBy { it.key }
            .joinToString("\n") { "${it.key}=${it.value}" }

    fun decodeTimestamps(raw: String?): Map<String, Long> =
        raw.orEmpty()
            .split('\n')
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val pkg = line.substring(0, idx).trim()
                val time = line.substring(idx + 1).trim().toLongOrNull() ?: return@mapNotNull null
                if (pkg.isEmpty() || time <= 0L) null else pkg to time
            }
            .toMap()

    private fun encodeKeyMap(entries: Map<String, String>): String =
        entries.entries
            .mapNotNull { (pkg, value) ->
                val cleanPkg = pkg.trim()
                val cleanValue = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
                if (cleanPkg.isEmpty() || cleanValue.isEmpty()) null else cleanPkg to cleanValue
            }
            .sortedBy { it.first }
            .joinToString("\n") { (pkg, value) -> "$pkg\t$value" }

    private fun decodeKeyMap(raw: String?): Map<String, String> =
        raw.orEmpty()
            .split('\n')
            .mapNotNull { line ->
                val idx = line.indexOf('\t')
                if (idx <= 0) return@mapNotNull null
                val pkg = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (pkg.isEmpty() || value.isEmpty()) null else pkg to value
            }
            .toMap()

    fun encodeCustomLabels(labels: Map<String, String>): String = encodeKeyMap(labels)

    fun decodeCustomLabels(raw: String?): Map<String, String> = decodeKeyMap(raw)

    fun encodeCustomArt(art: Map<String, String>): String = encodeKeyMap(art)

    fun decodeCustomArt(raw: String?): Map<String, String> = decodeKeyMap(raw)

    const val MAX_CATEGORIES = 6
    private const val MAX_CATEGORY_NAME_LENGTH = 24

    private fun cleanCategoryName(raw: String): String =
        raw.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim().take(MAX_CATEGORY_NAME_LENGTH)

    fun encodeCategories(categories: List<AppCategory>): String {
        val array = JSONArray()
        categories.take(MAX_CATEGORIES).forEach { cat ->
            val pkgs = JSONArray().apply { cat.packages.distinct().forEach(::put) }
            array.put(JSONObject().put("id", cat.id).put("name", cat.name).put("packages", pkgs))
        }
        return array.toString()
    }

    fun decodeCategories(raw: String?): List<AppCategory> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val seenIds = HashSet<String>()
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").trim()
                val name = cleanCategoryName(obj.optString("name"))
                if (id.isEmpty() || name.isEmpty() || !seenIds.add(id)) return@mapNotNull null
                val pkgArr = obj.optJSONArray("packages")
                val packages = (0 until (pkgArr?.length() ?: 0)).mapNotNull { j ->
                    pkgArr?.optString(j)?.trim()?.takeIf { it.isNotEmpty() }
                }.distinct()
                AppCategory(id, name, packages)
            }.take(MAX_CATEGORIES)
        }.getOrDefault(emptyList())
    }

    fun addCategory(categories: List<AppCategory>, name: String, id: String): List<AppCategory> {
        val cleanName = cleanCategoryName(name)
        val cleanId = id.trim()
        if (cleanName.isEmpty() || cleanId.isEmpty() || categories.size >= MAX_CATEGORIES) return categories
        if (categories.any { it.id == cleanId || it.name.equals(cleanName, ignoreCase = true) }) return categories
        return categories + AppCategory(cleanId, cleanName)
    }

    fun renameCategory(categories: List<AppCategory>, id: String, newName: String): List<AppCategory> {
        val cleanName = cleanCategoryName(newName)
        if (cleanName.isEmpty()) return categories
        if (categories.any { it.id != id && it.name.equals(cleanName, ignoreCase = true) }) return categories
        return categories.map { if (it.id == id) it.copy(name = cleanName) else it }
    }

    fun deleteCategory(categories: List<AppCategory>, id: String): List<AppCategory> =
        categories.filterNot { it.id == id }

    fun toggleAppInCategory(categories: List<AppCategory>, categoryId: String, packageName: String): List<AppCategory> =
        categories.map { cat ->
            if (cat.id == categoryId) cat.copy(packages = toggleFavorite(cat.packages, packageName)) else cat
        }

    fun categoryApps(apps: List<AppEntry>, category: AppCategory): List<AppEntry> =
        favoriteApps(apps, category.packages)

    fun moveInCategory(
        categories: List<AppCategory>,
        categoryId: String,
        packageName: String,
        delta: Int,
        shown: List<String>
    ): Pair<List<AppCategory>, Int> {
        val category = categories.firstOrNull { it.id == categoryId } ?: return categories to -1
        val moved = move(shown, packageName, delta)
        val updatedPackages = moved + (category.packages - shown.toSet())
        val updated = categories.map { if (it.id == categoryId) it.copy(packages = updatedPackages) else it }
        return updated to moved.indexOf(packageName)
    }

    fun applyCustomLabels(apps: List<AppEntry>, customLabels: Map<String, String>): List<AppEntry> {
        if (customLabels.isEmpty()) return apps
        return apps.map { entry ->
            val custom = customLabels[entry.packageName]?.trim()?.takeIf { it.isNotEmpty() }
            if (custom != null) entry.copy(label = custom) else entry
        }
    }
}

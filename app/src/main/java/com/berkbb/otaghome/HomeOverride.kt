package com.berkbb.otaghome

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/** A Home app of the TV as the package manager lists it. */
data class HomeCandidate(
    val packageName: String,
    val activityName: String,
    /** The real activity when [activityName] is an alias. */
    val targetActivity: String?,
    val priority: Int,
    val isSystem: Boolean
)

/** The pure parts of the Home override, apart from Android so they can be tested. */
object HomeOverride {

    /**
     * The TV's own home screens, as package → activity class names: every system Home app but
     * OtagHome. Fallback homes with a negative priority (the settings app's boot screen) are left
     * alone, and so are launchers the user installed.
     */
    fun targets(candidates: List<HomeCandidate>, ownPackage: String): Map<String, Set<String>> =
        candidates
            .filter { it.packageName != ownPackage && it.isSystem && it.priority >= 0 }
            .groupBy { it.packageName }
            .mapValues { (_, homes) -> homes.flatMap { listOfNotNull(it.activityName, it.targetActivity) }.toSet() }

    /**
     * True when the window that just came up is one of the TV's home screens. Only the home
     * activity counts: other screens of the same app (Google's input panel lives in its launcher)
     * open as usual.
     */
    fun isHomeScreen(packageName: CharSequence?, className: CharSequence?, targets: Map<String, Set<String>>): Boolean {
        if (packageName == null || className == null) return false
        return targets[packageName.toString()]?.contains(className.toString()) == true
    }

    /** Apps that stand in for settings screens a TV left out and only say no app can open them. */
    private val STUB_PACKAGES = setOf("com.google.android.tv.frameworkpackagestubs")

    /** True when the screen that answers the Accessibility intent is a real one, not a stub. */
    fun isRealScreen(packageName: String?): Boolean = packageName != null && packageName !in STUB_PACKAGES

    /** The enabled-services setting (colon separated) with [service] in it once. */
    fun withService(enabled: String?, service: String): String {
        val services = enabled.orEmpty().split(':').filter { it.isNotBlank() }
        val present = services.any { sameService(it, service) }
        return (if (present) services else services + service).joinToString(":")
    }

    /** Android may keep a service as "package/.Class"; both spellings name the same one. */
    private fun sameService(a: String, b: String): Boolean = fullName(a).equals(fullName(b), ignoreCase = true)

    private fun fullName(service: String): String {
        val pkg = service.substringBefore('/')
        val cls = service.substringAfter('/')
        return if (cls.startsWith('.')) "$pkg/$pkg$cls" else service
    }
}

/** The adb line that lets OtagHome switch its service itself, on TVs without an Accessibility screen. */
internal fun Context.homeOverrideGrantCommand(): String =
    "adb shell pm grant $packageName ${Manifest.permission.WRITE_SECURE_SETTINGS}"

/** Accessibility in the TV's settings, or null when the TV has none (only a stub that says so). */
internal fun Context.accessibilityScreen(): Intent? {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    val handler = runCatching { packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }.getOrNull()
    return intent.takeIf { HomeOverride.isRealScreen(handler) }
}

/** True once OtagHome was allowed, over adb, to switch its accessibility service itself. */
internal fun Context.canSwitchHomeOverride(): Boolean =
    checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

/** Turns the service on as Accessibility would; false when OtagHome may not. */
internal fun Context.turnOnHomeOverride(): Boolean = canSwitchHomeOverride() && runCatching {
    val service = ComponentName(this, HomeOverrideService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    Settings.Secure.putString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, HomeOverride.withService(enabled, service))
    Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
}.isSuccess

/** Every Home app this TV offers. */
internal fun Context.homeCandidates(): List<HomeCandidate> =
    runCatching { packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0) }
        .getOrDefault(emptyList())
        .map { info ->
            val activity = info.activityInfo
            HomeCandidate(
                packageName = activity.packageName,
                activityName = activity.name,
                targetActivity = activity.targetActivity,
                priority = info.priority,
                isSystem = activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            )
        }

/**
 * Opens OtagHome whenever the TV's own home screen comes up, for TVs that never let the user pick
 * another Home app. Off until the user turns it on in Accessibility; it hears only the window
 * changes of the TV's home screens and reads nothing on the screen.
 */
class HomeOverrideService : AccessibilityService() {

    companion object {
        private val _active = MutableStateFlow(false)
        /** True while the service is turned on and connected. */
        val active: StateFlow<Boolean> = _active.asStateFlow()

        private var running: WeakReference<HomeOverrideService>? = null

        /** Turns the service off as Accessibility would; false when it was not running. */
        fun turnOff(): Boolean = running?.get()?.let { it.disableSelf(); true } ?: false
    }

    private var targets: Map<String, Set<String>> = emptyMap()

    override fun onServiceConnected() {
        targets = HomeOverride.targets(homeCandidates(), packageName)
        // Events from the TV's home screens only; with none to watch, only OtagHome's own (ignored)
        val watched = targets.keys.ifEmpty { setOf(packageName) }.toTypedArray()
        serviceInfo = serviceInfo.apply { packageNames = watched }
        running = WeakReference(this)
        _active.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!HomeOverride.isHomeScreen(event.packageName, event.className, targets)) return
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setClass(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        running = null
        _active.value = false
        super.onDestroy()
    }
}

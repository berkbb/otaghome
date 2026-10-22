package com.berkbb.otaghome

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.annotation.StringRes
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

const val SELF_REPO = "berkbb/otaghome"
const val SELF_RELEASES_PAGE = "github.com/$SELF_REPO/releases"
private const val SELF_RELEASE_API_URL = "https://api.github.com/repos/$SELF_REPO/releases/latest"

/** The release APK to install: its tag, address, size and GitHub's SHA-256 digest when given. */
data class ReleaseApk(val tag: String, val url: String, val size: Long, val sha256: String?)

/** Result of checking OtagHome's GitHub releases. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class UpToDate(val current: String) : UpdateStatus
    /** [apk] is null when the release has no APK to install. */
    data class Available(val latest: String, val current: String, val apk: ReleaseApk?) : UpdateStatus
    /** No published release, or the repository is not public (GitHub answers 404). */
    data object Unavailable : UpdateStatus
    data object Failed : UpdateStatus
}

/** Downloading and installing a newer OtagHome over itself. */
sealed interface UpdateInstall {
    data object Idle : UpdateInstall
    data class Downloading(val progress: Int) : UpdateInstall
    /** The system installer was started; the TV asks for confirmation. */
    data object WaitingForConfirmation : UpdateInstall
    data class Failed(@param:StringRes val messageRes: Int) : UpdateInstall
}

/** The pure parts of the update check, apart from the network so they can be tested. */
object ReleaseParser {
    private val VERSION = Regex("""^v?(\d+(?:\.\d+)*)""")

    fun parseVersion(raw: String?): List<Int> =
        VERSION.find(raw?.trim().orEmpty())?.groupValues?.get(1)?.split('.')?.map { it.toIntOrNull() ?: 0 } ?: emptyList()

    /** Segment by segment; missing segments count as 0. */
    fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    /** GitHub's asset digest ("sha256:abc…") as lowercase hex, or null. */
    fun parseSha256(digest: String?): String? {
        val value = digest?.trim()?.lowercase() ?: return null
        val hex = value.removePrefix("sha256:")
        return if (value.startsWith("sha256:") && hex.length == 64 && hex.all { it in "0123456789abcdef" }) hex else null
    }

    /** The latest-release reply compared with the installed [current] version. */
    fun status(json: String, current: String): UpdateStatus {
        val release = JSONObject(json)
        val tag = release.optString("tag_name")
        val latest = parseVersion(tag)
        if (latest.isEmpty()) return UpdateStatus.Unavailable
        if (compareVersions(latest, parseVersion(current)) <= 0) return UpdateStatus.UpToDate(current)
        val assets = release.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith("-release.apk") && it.optString("browser_download_url").isNotBlank() }
            ?.let { ReleaseApk(tag, it.getString("browser_download_url"), it.optLong("size"), parseSha256(it.optString("digest"))) }
        return UpdateStatus.Available(latest.joinToString("."), current, apk)
    }
}

object SelfUpdate {

    /** Blocking; call off the main thread. */
    fun check(current: String): UpdateStatus = runCatching {
        val connection = (URL(SELF_RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "OtagHome/$current")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> ReleaseParser.status(connection.inputStream.bufferedReader().use { it.readText() }, current)
                HttpURLConnection.HTTP_NOT_FOUND -> UpdateStatus.Unavailable
                else -> UpdateStatus.Failed
            }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(UpdateStatus.Failed)

    /** Downloads into a .part file and keeps it only when it matches the digest (or the size). */
    fun download(apk: ReleaseApk, target: File, onProgress: (Int) -> Unit): Boolean {
        val part = File(target.parentFile, target.name + ".part")
        return runCatching {
            val connection = (URL(apk.url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return false
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: apk.size
                var done = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(part).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            val valid = apk.sha256?.let { sha256Of(part) == it } ?: (apk.size > 0 && part.length() == apk.size)
            if (valid) part.renameTo(target) else part.delete().let { false }
        }.getOrElse { part.delete(); false }
    }

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Hands the APK to the system installer; the TV shows its confirmation. */
    fun install(context: Context, apk: File): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        var session: PackageInstaller.Session? = null
        return runCatching {
            val id = installer.createSession(params)
            session = installer.openSession(id).also { s ->
                apk.inputStream().use { input ->
                    s.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        s.fsync(output)
                    }
                }
                val pending = PendingIntent.getBroadcast(
                    context, id, Intent(context, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                s.commit(pending.intentSender)
            }
            true
        }.getOrElse {
            runCatching { session?.abandon() }
            false
        }.also { runCatching { session?.close() } }
    }
}

/** A failed install reported by the system installer, for the About window. */
object InstallFailures {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()
    fun emit() { _events.tryEmit(Unit) }
}

/**
 * Receives the installer's callbacks. Not exported: only the app's own PendingIntent, handed to
 * the system installer, delivers here.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let {
                    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> InstallFailures.emit()
        }
    }
}

/** Reopens OtagHome after it updated itself, only when the update was started from the app. */
class SelfUpdateRestartReceiver : BroadcastReceiver() {
    companion object {
        const val PREFS = "self_update"
        const val KEY_PENDING = "restart_after_update"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return
        prefs.edit().remove(KEY_PENDING).apply()
        runCatching { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

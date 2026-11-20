package com.berkbb.otaghome

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.Locale

/** A picture found on the TV or in OtagHome's own pictures folder. */
data class DeviceImage(
    val uri: String,
    val name: String,
    val fileName: String,
    val folder: String,
    val modifiedMillis: Long
)

/**
 * Pure, JVM-testable helpers for scanning device pictures, encoding file URIs, choosing the
 * right photo permission and sizing bitmaps for decoding.
 */
object DeviceImageCatalog {
    private val BASE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private val API28_EXTENSIONS = setOf("heic", "heif")

    fun isSupportedImageName(name: String, sdkInt: Int = 28): Boolean {
        if (name.isBlank() || name.startsWith('.')) return false
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isEmpty()) return false
        return ext in BASE_EXTENSIONS || (sdkInt >= 28 && ext in API28_EXTENSIONS)
    }

    fun displayName(fileName: String): String =
        fileName.substringBeforeLast('.').ifBlank { fileName }

    fun fileUri(file: File): String {
        val path = file.absolutePath.let { if (it.startsWith("/")) it else "/$it" }
        return URI("file", "", path, null).toASCIIString()
    }

    fun storageImagePermission(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        if (width <= 0 || height <= 0 || reqWidth <= 0 || reqHeight <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= reqWidth && height / (sample * 2) >= reqHeight) {
            sample *= 2
        }
        return sample
    }

    fun scanDirectory(
        dir: File,
        folderLabel: String,
        sdkInt: Int = 28,
        maxDepth: Int = 2,
        maxItems: Int = 120
    ): List<DeviceImage> {
        if (!dir.isDirectory || maxItems <= 0) return emptyList()
        return runCatching {
            dir.walkTopDown()
                .maxDepth(maxDepth)
                .onEnter { it == dir || !it.name.startsWith('.') }
                .filter { it.isFile && isSupportedImageName(it.name, sdkInt) }
                .map { file ->
                    DeviceImage(
                        uri = fileUri(file),
                        name = displayName(file.name),
                        fileName = file.name,
                        folder = folderLabel,
                        modifiedMillis = file.lastModified()
                    )
                }
                .sortedWith(compareByDescending<DeviceImage> { it.modifiedMillis }.thenBy { it.name.lowercase(Locale.ROOT) })
                .take(maxItems)
                .toList()
        }.getOrDefault(emptyList())
    }

    /**
     * Merges images from multiple sources while preserving first-seen folder ordering. Earlier
     * sources win on duplicate URIs or duplicate `(folder, fileName)` pairs (ignoring case), and
     * items inside each folder are sorted newest-first.
     */
    fun mergeAndSort(sources: List<List<DeviceImage>>, maxItems: Int = 120): List<DeviceImage> {
        if (maxItems <= 0) return emptyList()
        val seenUris = HashSet<String>()
        val seenKeys = HashSet<Pair<String, String>>()
        val byFolder = LinkedHashMap<String, MutableList<DeviceImage>>()
        for (source in sources) {
            for (item in source) {
                val key = item.folder.lowercase(Locale.ROOT) to item.fileName.lowercase(Locale.ROOT)
                if (item.uri in seenUris || key in seenKeys) continue
                seenUris += item.uri
                seenKeys += key
                byFolder.getOrPut(item.folder) { mutableListOf() }.add(item)
            }
        }
        val comparator = compareByDescending<DeviceImage> { it.modifiedMillis }
            .thenBy { it.name.lowercase(Locale.ROOT) }
        val result = ArrayList<DeviceImage>()
        for (items in byFolder.values) {
            items.sortWith(comparator)
            for (item in items) {
                result.add(item)
                if (result.size >= maxItems) return result
            }
        }
        return result
    }

    /** Next picture URI after [currentUri] sorted deterministically, or null with fewer than two images. */
    fun nextImageUri(currentUri: String, images: List<DeviceImage>): String? {
        if (images.size < 2) return null
        val ordered = images.sortedWith(compareBy<DeviceImage> { it.fileName.lowercase(Locale.ROOT) }.thenBy { it.uri })
        val index = ordered.indexOfFirst { it.uri == currentUri }
        return if (index < 0) ordered.first().uri else ordered[(index + 1) % ordered.size].uri
    }

    fun safeFileKey(packageName: String): String =
        packageName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "app" }.take(64)
}

/** OtagHome's own pictures directory, readable without any storage permission. */
internal fun Context.localPicturesDir(): File =
    getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(filesDir, "backgrounds")

/** Internal copies of pictures imported from transient pickers (`ACTION_GET_CONTENT`). */
internal fun Context.importedBackgroundsDir(): File = File(filesDir, "imported_bg")

/** Internal copies of custom app cover images assigned from the app menu. */
internal fun Context.customAppArtDir(): File = File(filesDir, "custom_app_art")

internal fun Context.hasStorageImagePermission(): Boolean =
    ContextCompat.checkSelfPermission(this, DeviceImageCatalog.storageImagePermission(Build.VERSION.SDK_INT)) ==
        PackageManager.PERMISSION_GRANTED

/** True when a non-stub app (such as a file manager) handles `ACTION_GET_CONTENT` for images. */
internal fun Context.hasImageContentPicker(): Boolean {
    val intent = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
    val handler = runCatching {
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()
    return HomeOverride.isRealScreen(handler)
}

internal fun Context.openImageStream(uriString: String): InputStream? {
    val parsed = Uri.parse(uriString)
    return if (parsed.scheme == ContentResolver.SCHEME_FILE) {
        parsed.path?.let(::File)?.takeIf(File::isFile)?.inputStream()
    } else {
        contentResolver.openInputStream(parsed)
    }
}

internal fun Context.decodeScaledBitmap(uriString: String, reqWidth: Int, reqHeight: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openImageStream(uriString)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    val options = BitmapFactory.Options().apply {
        inSampleSize = DeviceImageCatalog.calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
    }
    openImageStream(uriString)?.use { BitmapFactory.decodeStream(it, null, options) }
}.getOrNull()

private fun Context.importValidatedImage(sourceUriString: String, targetDir: File, prefix: String): String? {
    val dir = targetDir.apply { mkdirs() }
    val dest = runCatching { File.createTempFile(prefix, ".img", dir) }.getOrNull() ?: return null
    return try {
        val bytes = openImageStream(sourceUriString)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: -1L
        if (bytes <= 0L) {
            dest.delete()
            return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(dest.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            dest.delete()
            return null
        }
        DeviceImageCatalog.fileUri(dest)
    } catch (_: Throwable) {
        dest.delete()
        null
    }
}

private fun deleteFileUnderDir(filePath: String?, rootDir: File) {
    if (filePath.isNullOrBlank()) return
    runCatching {
        val file = File(filePath).canonicalFile
        val root = rootDir.canonicalFile
        val prefix = root.path.trimEnd(File.separatorChar) + File.separatorChar
        if (file.path.startsWith(prefix) && file.isFile) file.delete()
    }
}

/**
 * Copies a transient picker URI into [importedBackgroundsDir] so it remains readable across
 * reboots, validates that it decodes as an image, and returns its `file://` URI (or null).
 */
internal fun Context.importBackgroundImage(sourceUri: Uri): String? =
    importValidatedImage(sourceUri.toString(), importedBackgroundsDir(), "bg_")

/** Copies a custom app cover URI into [customAppArtDir] so it stays isolated from device changes. */
internal fun Context.importAppCover(packageName: String, sourceUriString: String): String? =
    importValidatedImage(sourceUriString, customAppArtDir(), "${DeviceImageCatalog.safeFileKey(packageName)}_")

/** Deletes an old imported background only when its canonical path lives under [importedBackgroundsDir]. */
internal fun Context.cleanupImportedBackground(filePath: String?) =
    deleteFileUnderDir(filePath, importedBackgroundsDir())

/** Deletes an old imported app cover when replaced or cleared. */
internal fun Context.cleanupCustomAppCover(uriString: String?) {
    if (uriString.isNullOrBlank()) return
    val parsed = Uri.parse(uriString)
    if (parsed.scheme == ContentResolver.SCHEME_FILE) {
        deleteFileUnderDir(parsed.path, customAppArtDir())
    }
}

/** Removes internal temporary copies used for custom covers and transient backgrounds (never touches [localPicturesDir]). */
internal fun Context.clearInternalImageCaches() {
    listOf(importedBackgroundsDir(), customAppArtDir()).forEach { dir ->
        runCatching {
            dir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}

/** Loads pictures from OtagHome's folder and, when permitted, from MediaStore and TV storage folders. */
internal fun Context.loadDeviceImages(appFolderLabel: String, deviceFolderLabel: String): List<DeviceImage> {
    val appDir = localPicturesDir().apply { runCatching { mkdirs() } }
    val appImages = DeviceImageCatalog.scanDirectory(appDir, appFolderLabel, Build.VERSION.SDK_INT)
    if (!hasStorageImagePermission()) return appImages
    val mediaStoreImages = queryMediaStoreImages(deviceFolderLabel)
    val publicDirImages = scanPublicImageDirs()
    return DeviceImageCatalog.mergeAndSort(listOf(appImages, mediaStoreImages, publicDirImages))
}

private fun Context.queryMediaStoreImages(fallbackFolder: String): List<DeviceImage> = runCatching {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.DATE_MODIFIED
    )
    val results = ArrayList<DeviceImage>()
    contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
    )?.use { cursor ->
        val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
        val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
        if (idIdx < 0 || nameIdx < 0) return@use
        val bucketIdx = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val dateIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
        while (cursor.moveToNext() && results.size < 120) {
            val fileName = cursor.getString(nameIdx) ?: continue
            if (!DeviceImageCatalog.isSupportedImageName(fileName, Build.VERSION.SDK_INT)) continue
            val id = cursor.getLong(idIdx)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id).toString()
            val folder = if (bucketIdx >= 0) cursor.getString(bucketIdx)?.takeIf { it.isNotBlank() } else null
            val modified = if (dateIdx >= 0) cursor.getLong(dateIdx) * 1000L else 0L
            results += DeviceImage(
                uri = uri,
                name = DeviceImageCatalog.displayName(fileName),
                fileName = fileName,
                folder = folder ?: fallbackFolder,
                modifiedMillis = modified
            )
        }
    }
    results
}.getOrDefault(emptyList())

@Suppress("DEPRECATION")
private fun scanPublicImageDirs(): List<DeviceImage> = runCatching {
    listOf(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    ).flatMap { dir ->
        DeviceImageCatalog.scanDirectory(dir, dir.name, Build.VERSION.SDK_INT)
    }
}.getOrDefault(emptyList())

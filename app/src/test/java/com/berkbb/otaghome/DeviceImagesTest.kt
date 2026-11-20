package com.berkbb.otaghome

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI
import java.nio.file.Files

class DeviceImagesTest {

    @Test
    fun supportedImageNamesRespectExtensionCaseHiddenFilesAndSdk() {
        listOf("photo.jpg", "PHOTO.JPEG", "wall.png", "hero.webp", "tile.bmp", "anim.gif").forEach { name ->
            assertTrue(name, DeviceImageCatalog.isSupportedImageName(name, sdkInt = 26))
        }
        listOf("readme.txt", "archive.zip", "noext", "", "   ", ".hidden.jpg").forEach { name ->
            assertFalse(name, DeviceImageCatalog.isSupportedImageName(name, sdkInt = 35))
        }
        assertFalse(DeviceImageCatalog.isSupportedImageName("modern.heic", sdkInt = 27))
        assertFalse(DeviceImageCatalog.isSupportedImageName("pic.HEIF", sdkInt = 27))
        assertTrue(DeviceImageCatalog.isSupportedImageName("modern.heic", sdkInt = 28))
        assertTrue(DeviceImageCatalog.isSupportedImageName("pic.HEIF", sdkInt = 34))
    }

    @Test
    fun displayNameStripsLastExtensionOnly() {
        assertEquals("sunset", DeviceImageCatalog.displayName("sunset.jpg"))
        assertEquals("my.wallpaper.v2", DeviceImageCatalog.displayName("my.wallpaper.v2.png"))
        assertEquals("plain", DeviceImageCatalog.displayName("plain"))
        assertEquals(".jpg", DeviceImageCatalog.displayName(".jpg"))
    }

    @Test
    fun fileUriEncodesSpacesAndHashes() {
        val file = File("/tmp/My Wallpapers/bg #1.png")
        val uri = DeviceImageCatalog.fileUri(file)
        assertTrue(uri.startsWith("file:///"))
        assertTrue(uri.contains("%20"))
        assertTrue(uri.contains("%23"))
        assertEquals(file.absolutePath, URI(uri).path)
    }

    @Test
    fun storageImagePermissionMatchesAndroidSdk() {
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, DeviceImageCatalog.storageImagePermission(26))
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, DeviceImageCatalog.storageImagePermission(32))
        assertEquals(Manifest.permission.READ_MEDIA_IMAGES, DeviceImageCatalog.storageImagePermission(33))
        assertEquals(Manifest.permission.READ_MEDIA_IMAGES, DeviceImageCatalog.storageImagePermission(35))
    }

    @Test
    fun inSampleSizeScalesPowersOfTwoSafely() {
        assertEquals(1, DeviceImageCatalog.calculateInSampleSize(1920, 1080, 1920, 1080))
        assertEquals(2, DeviceImageCatalog.calculateInSampleSize(3840, 2160, 1920, 1080))
        assertEquals(4, DeviceImageCatalog.calculateInSampleSize(7680, 4320, 1920, 1080))
        assertEquals(1, DeviceImageCatalog.calculateInSampleSize(800, 600, 1920, 1080))
        assertEquals(1, DeviceImageCatalog.calculateInSampleSize(0, 1080, 1920, 1080))
        assertEquals(1, DeviceImageCatalog.calculateInSampleSize(1920, 1080, 0, 0))
    }

    @Test
    fun scanDirectoryFindsNestedImagesSkipsHiddenAndDeepDirsAndSortsNewestFirst() {
        val root = Files.createTempDirectory("otaghome-img-test-").toFile()
        try {
            val oldFile = File(root, "old.jpg").apply { writeBytes(byteArrayOf(1)); setLastModified(1_000_000L) }
            File(root, ".hidden.png").writeBytes(byteArrayOf(1))
            File(root, "notes.txt").writeText("ignored")
            val sub = File(root, "sub").apply { mkdirs() }
            val newFile = File(sub, "new.png").apply { writeBytes(byteArrayOf(1)); setLastModified(2_000_000L) }
            val hiddenDir = File(root, ".thumb").apply { mkdirs() }
            File(hiddenDir, "ignored.jpg").writeBytes(byteArrayOf(1))
            val deepDir = File(sub, "deep").apply { mkdirs() }
            File(deepDir, "too_deep.jpg").writeBytes(byteArrayOf(1))

            val found = DeviceImageCatalog.scanDirectory(root, folderLabel = "OtagHome")
            assertEquals(listOf("new", "old"), found.map { it.name })
            assertEquals(listOf(DeviceImageCatalog.fileUri(newFile), DeviceImageCatalog.fileUri(oldFile)), found.map { it.uri })
            assertTrue(found.all { it.folder == "OtagHome" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun mergeAndSortDeduplicatesPreservesFolderOrderAndCapsItems() {
        val appSource = listOf(
            DeviceImage("file:///app/bg.jpg", "bg", "bg.jpg", "OtagHome", 1_000L)
        )
        val mediaStore = listOf(
            DeviceImage("content://media/1", "sunset", "sunset.jpg", "Pictures", 3_000L),
            DeviceImage("content://media/2", "sunset", "sunset.png", "Pictures", 2_000L),
            DeviceImage("content://media/3", "poster", "poster.webp", "Download", 1_500L)
        )
        val publicDirs = listOf(
            DeviceImage("file:///storage/Pictures/SUNSET.JPG", "SUNSET", "SUNSET.JPG", "pictures", 9_000L),
            DeviceImage("file:///storage/Pictures/extra.jpg", "extra", "extra.jpg", "Pictures", 4_000L)
        )

        val merged = DeviceImageCatalog.mergeAndSort(listOf(appSource, mediaStore, publicDirs))
        assertEquals(
            listOf(
                "file:///app/bg.jpg",
                "file:///storage/Pictures/extra.jpg",
                "content://media/1",
                "content://media/2",
                "content://media/3"
            ),
            merged.map { it.uri }
        )
        assertEquals(listOf("OtagHome", "Pictures", "Pictures", "Pictures", "Download"), merged.map { it.folder })
        assertEquals(2, DeviceImageCatalog.mergeAndSort(listOf(appSource, mediaStore, publicDirs), maxItems = 2).size)
    }

    @Test
    fun nextImageUriRotatesDeterministicallyWithTwoOrMoreImages() {
        val empty = emptyList<DeviceImage>()
        assertEquals(null, DeviceImageCatalog.nextImageUri("file:///a.jpg", empty))

        val single = listOf(DeviceImage("file:///a.jpg", "a", "a.jpg", "OtagHome", 10L))
        assertEquals(null, DeviceImageCatalog.nextImageUri("file:///a.jpg", single))

        val pool = listOf(
            DeviceImage("file:///c.jpg", "c", "c.jpg", "OtagHome", 30L),
            DeviceImage("file:///a.jpg", "a", "a.jpg", "OtagHome", 10L),
            DeviceImage("file:///b.jpg", "b", "b.jpg", "OtagHome", 20L)
        )
        assertEquals("file:///b.jpg", DeviceImageCatalog.nextImageUri("file:///a.jpg", pool))
        assertEquals("file:///c.jpg", DeviceImageCatalog.nextImageUri("file:///b.jpg", pool))
        assertEquals("file:///a.jpg", DeviceImageCatalog.nextImageUri("file:///c.jpg", pool))
        assertEquals("file:///a.jpg", DeviceImageCatalog.nextImageUri("file:///other.jpg", pool))
    }

    @Test
    fun safeFileKeySanitizesPackageNames() {
        assertEquals("com.example.tv_1", DeviceImageCatalog.safeFileKey("com.example.tv:1"))
        assertEquals("app", DeviceImageCatalog.safeFileKey(""))
    }
}

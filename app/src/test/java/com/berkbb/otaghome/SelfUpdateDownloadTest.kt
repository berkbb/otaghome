package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** The download's fallback from github.com to the api.github.com asset address, without the network. */
class SelfUpdateDownloadTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val bytes = "otaghome release apk".toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val pageUrl = "https://github.com/berkbb/otaghome/releases/download/v1.0.4/otaghome-v1.0.4-universal-release.apk"
    private val apiUrl = "https://api.github.com/repos/berkbb/otaghome/releases/assets/123"

    /** One canned answer: an HTTP status with a body, or an I/O failure when [code] is null. */
    private class Answer(val code: Int?, val body: ByteArray = ByteArray(0))

    private class FakeConnection(url: URL, private val answer: Answer) : HttpURLConnection(url) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = answer.code ?: throw IOException("connection reset")
        override fun getContentLengthLong(): Long = answer.body.size.toLong()
        override fun getInputStream(): InputStream = ByteArrayInputStream(answer.body)
    }

    private class Network(private val answers: Map<String, Answer>) {
        val opened = mutableListOf<FakeConnection>()
        val open: (URL) -> HttpURLConnection = { url ->
            FakeConnection(url, answers.getValue(url.toString())).also { opened += it }
        }
        val urls get() = opened.map { it.url.toString() }
        fun header(index: Int, name: String) = opened[index].getRequestProperty(name)
    }

    private fun download(apk: ReleaseApk, network: Network): Pair<Boolean, java.io.File> {
        val target = folder.newFolder().resolve("update.apk")
        return SelfUpdate.download(apk, target, network.open) {} to target
    }

    @Test
    fun aServerErrorOnGithubFallsBackToTheApiAssetAddress() {
        val network = Network(mapOf(pageUrl to Answer(504), apiUrl to Answer(200, bytes)))
        val (ok, target) = download(ReleaseApk("v1.0.4", pageUrl, bytes.size.toLong(), sha, apiUrl), network)
        assertTrue(ok)
        assertEquals(listOf(pageUrl, apiUrl), network.urls)
        assertNull(network.header(0, "Accept"))
        assertEquals("application/octet-stream", network.header(1, "Accept"))
        assertEquals("OtagHome/${BuildConfig.VERSION_NAME}", network.header(1, "User-Agent"))
        assertTrue(bytes.contentEquals(target.readBytes()))
        assertFalse(target.resolveSibling(target.name + ".part").exists())
    }

    @Test
    fun aBrokenConnectionAlsoFallsBack() {
        val network = Network(mapOf(pageUrl to Answer(null), apiUrl to Answer(200, bytes)))
        val (ok, _) = download(ReleaseApk("v1.0.4", pageUrl, bytes.size.toLong(), sha, apiUrl), network)
        assertTrue(ok)
        assertEquals(listOf(pageUrl, apiUrl), network.urls)
    }

    @Test
    fun aMissingFileIsNotRetriedAndNeitherIsAReleaseWithoutAnApiAddress() {
        val notFound = Network(mapOf(pageUrl to Answer(404), apiUrl to Answer(200, bytes)))
        assertFalse(download(ReleaseApk("v1.0.4", pageUrl, bytes.size.toLong(), sha, apiUrl), notFound).first)
        assertEquals(listOf(pageUrl), notFound.urls)

        val noApi = Network(mapOf(pageUrl to Answer(503)))
        assertFalse(download(ReleaseApk("v1.0.4", pageUrl, bytes.size.toLong(), sha, null), noApi).first)
        assertEquals(listOf(pageUrl), noApi.urls)
    }

    @Test
    fun theFallbackStillHasToMatchTheDigest() {
        val network = Network(mapOf(pageUrl to Answer(502), apiUrl to Answer(200, "something else".toByteArray())))
        val (ok, target) = download(ReleaseApk("v1.0.4", pageUrl, bytes.size.toLong(), sha, apiUrl), network)
        assertFalse(ok)
        assertFalse(target.exists())
        assertFalse(target.resolveSibling(target.name + ".part").exists())
    }

    @Test
    fun theFirstAddressIsEnoughWhenItWorks() {
        val network = Network(mapOf(pageUrl to Answer(200, bytes)))
        val (ok, target) = download(ReleaseApk("v1.0.4", pageUrl, bytes.size.toLong(), sha, apiUrl), network)
        assertTrue(ok)
        assertEquals(listOf(pageUrl), network.urls)
        assertEquals(sha, SelfUpdate.sha256Of(target))
    }

    @Test
    fun cachedUpdateApksAreCleanedWhileOtherFilesRemain() {
        val cache = folder.newFolder()
        val oldApk = cache.resolve("otaghome-v1.0.3.apk").apply { writeText("apk") }
        val partialApk = cache.resolve("otaghome-v1.0.4.apk.part").apply { writeText("part") }
        val otherFile = cache.resolve("other-cache.json").apply { writeText("{}") }
        SelfUpdate.cleanCachedApks(cache)
        assertFalse(oldApk.exists())
        assertFalse(partialApk.exists())
        assertTrue(otherFile.exists())
    }
}

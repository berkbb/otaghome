package com.berkbb.otaghome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {

    private val sha = "a".repeat(64)

    private fun release(tag: String, assets: String = "[]") = """{"tag_name":"$tag","assets":$assets}"""

    @Test
    fun versionsCompareSegmentBySegment() {
        assertEquals(listOf(0, 2, 0), ReleaseParser.parseVersion("v0.2.0"))
        assertTrue(ReleaseParser.compareVersions(listOf(0, 10), listOf(0, 9, 9)) > 0)
        assertEquals(0, ReleaseParser.compareVersions(listOf(1), listOf(1, 0, 0)))
        assertTrue(ReleaseParser.parseVersion("nightly").isEmpty())
    }

    @Test
    fun onlyRealSha256DigestsAreKept() {
        assertEquals(sha, ReleaseParser.parseSha256("sha256:$sha"))
        assertNull(ReleaseParser.parseSha256("sha1:$sha"))
        assertNull(ReleaseParser.parseSha256("sha256:abc"))
    }

    @Test
    fun aNewerReleaseOffersItsApk() {
        val json = release("v0.2.0", """[{"name":"otaghome-v0.2.0-release.apk","browser_download_url":"https://x/a.apk","size":1500000,"digest":"sha256:$sha"},
            {"name":"SHA256SUMS","browser_download_url":"https://x/s"}]""")
        assertEquals(
            UpdateStatus.Available("0.2.0", "0.1.0", ReleaseApk("v0.2.0", "https://x/a.apk", 1_500_000, sha)),
            ReleaseParser.status(json, "0.1.0")
        )
    }

    @Test
    fun theSameOrAnOlderReleaseIsUpToDate() {
        assertEquals(UpdateStatus.UpToDate("0.1.0"), ReleaseParser.status(release("v0.1.0"), "0.1.0"))
        assertEquals(UpdateStatus.UpToDate("0.2.0"), ReleaseParser.status(release("v0.1.9"), "0.2.0"))
    }

    @Test
    fun aReleaseWithoutAnApkOrTagSaysSo() {
        assertEquals(UpdateStatus.Available("0.3.0", "0.1.0", null), ReleaseParser.status(release("v0.3.0"), "0.1.0"))
        assertEquals(UpdateStatus.Unavailable, ReleaseParser.status(release("latest"), "0.1.0"))
    }
}

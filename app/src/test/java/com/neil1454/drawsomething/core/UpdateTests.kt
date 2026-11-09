package com.neil1454.drawsomething.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    private val url = "https://github.com/Neil1454/Draw-Something/releases/download/v1.5.0/TimeKillerGames-v1.5.0.apk"
    private val hash = "a".repeat(64)

    private fun release(extra: String = "", assetUrl: String = url, name: String = "TimeKillerGames-v1.5.0.apk") =
        """{"tag_name":"v1.5.0","draft":false,"prerelease":false,"body":"New games\nand fixes",$extra
           "assets":[{"name":"notes.txt","browser_download_url":"https://github.com/Neil1454/Draw-Something/releases/download/v1.5.0/notes.txt","size":10},
           {"name":"$name","browser_download_url":"$assetUrl","size":9500000,"digest":"sha256:$hash"}]}"""

    @Test fun versionsCompareNumerically() {
        assertTrue(UpdateRules.isNewer("v1.5.0", "1.4.0"))
        assertTrue(UpdateRules.isNewer("1.10.0", "1.9.0")) // not alphabetical
        assertTrue(UpdateRules.isNewer("2.0", "1.99.9"))
        assertFalse(UpdateRules.isNewer("1.4.0", "1.4.0"))
        assertFalse(UpdateRules.isNewer("v1.3.9", "1.4.0"))
        assertFalse(UpdateRules.isNewer("1.4", "1.4.0")) // same version, shorter spelling
        assertTrue(UpdateRules.isNewer("1.4.1", "1.4"))
        assertFalse(UpdateRules.isNewer("garbage", "1.0.0"))
    }

    @Test fun readsTheApkAssetAndIgnoresOtherFiles() {
        val r = parseRelease(release())!!
        assertEquals("v1.5.0", r.tag)
        assertEquals("1.5.0", r.version)
        assertEquals("TimeKillerGames-v1.5.0.apk", r.apkName)
        assertEquals(url, r.apkUrl)
        assertEquals(9_500_000L, r.sizeBytes)
        assertEquals(hash, r.sha256)
        assertTrue(r.notes.startsWith("New games"))
    }

    @Test fun refusesDownloadsFromAnywhereButThisRepo() {
        assertNull(parseRelease(release(assetUrl = "https://evil.example.com/app.apk")))
        assertNull(parseRelease(release(assetUrl = "http://github.com/Neil1454/Draw-Something/releases/download/v1.5.0/x.apk")))
        assertNull(parseRelease(release(assetUrl = "https://github.com/someone-else/Draw-Something/releases/download/v1.5.0/x.apk")))
    }

    @Test fun ignoresDraftsPrereleasesAndReleasesWithoutAnApk() {
        assertNull(parseRelease(release().replace("\"draft\":false", "\"draft\":true")))
        assertNull(parseRelease(release().replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(parseRelease(release(name = "readme.txt")))
        assertNull(parseRelease("""{"tag_name":"v1","assets":[]}"""))
        assertNull(parseRelease("not json"))
        assertNull(parseRelease("""{"message":"Not Found"}"""))
    }

    @Test fun badDigestsAreDroppedNotTrusted() {
        val r = parseRelease(release().replace(hash, "zzz"))!!
        assertNull(r.sha256)
        assertEquals(hash, parseRelease(release().replace(hash, hash.uppercase()))!!.sha256)
    }

    /** A real answer from api.github.com for this repository (trimmed), so format changes get noticed. */
    @Test fun readsARealGitHubResponse() {
        val real = """{"tag_name": "v1.3.0", "name": "Neil & Teri's Time Killer Games v1.3.0", "draft": false, "prerelease": false, "body": "Download **TimeKillerGames-v1.3.0.apk** below on your Android phone or tablet and open it to install (allow installs from your browser or Files app when asked). Install it on both devices. Ideal for long flights: no internet needed. Made by Neil Sampson.", "assets": [{"name": "TimeKillerGames-v1.3.0.apk", "size": 10049202, "digest": "sha256:a0b7ec9d144696928e0d9ef19bb6466cd62b547a2a5dfbd3c839a532751b4ee1", "browser_download_url": "https://github.com/Neil1454/Draw-Something/releases/download/v1.3.0/TimeKillerGames-v1.3.0.apk", "content_type": "application/vnd.android.package-archive"}]}"""
        val r = parseRelease(real)!!
        assertEquals("v1.3.0", r.tag)
        assertEquals("1.3.0", r.version)
        assertEquals("TimeKillerGames-v1.3.0.apk", r.apkName)
        assertTrue(r.apkUrl.startsWith(UpdateRules.ASSET_PREFIX))
        assertEquals(10_049_202L, r.sizeBytes)
        assertEquals(64, r.sha256!!.length)
        assertTrue(UpdateRules.isNewer(r.version, "1.2.0"))
        assertFalse(UpdateRules.isNewer(r.version, "1.3.0"))
        assertFalse(UpdateRules.isNewer(r.version, "1.5.0"))
    }

    @Test fun onlyGitHubHostsOverHttpsAreAllowed() {
        assertTrue(UpdateRules.hostAllowed("api.github.com"))
        assertTrue(UpdateRules.hostAllowed("github.com"))
        assertTrue(UpdateRules.hostAllowed("objects.githubusercontent.com"))
        assertTrue(UpdateRules.hostAllowed("release-assets.githubusercontent.com"))
        assertFalse(UpdateRules.hostAllowed("evilgithub.com"))
        assertFalse(UpdateRules.hostAllowed("github.com.evil.example"))
        assertFalse(UpdateRules.hostAllowed("example.com"))
    }
}

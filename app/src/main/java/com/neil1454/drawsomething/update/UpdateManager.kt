package com.neil1454.drawsomething.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.neil1454.drawsomething.core.ReleaseInfo
import com.neil1454.drawsomething.core.UpdateRules
import com.neil1454.drawsomething.core.parseRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** What the "Check for updates" button found. */
sealed class UpdateCheck {
    data class Available(val info: ReleaseInfo) : UpdateCheck()
    data class UpToDate(val version: String) : UpdateCheck()
}

/**
 * In-app updates from this project's GitHub releases.
 *
 * The network is used ONLY when the player taps the button: first to ask GitHub what the latest
 * release is, then (if they agree) to download it. Everything is HTTPS, only GitHub's own hosts
 * are ever contacted (also across redirects), the file must come from this repository's releases,
 * its size and SHA-256 are checked, and before Android is asked to install it we confirm it is
 * this same app, newer, and signed by the same key as the copy that's installed.
 */
class UpdateManager(private val context: Context) {

    val currentVersion: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        val conn = open(UpdateRules.LATEST_URL, accept = "application/vnd.github+json")
        try {
            when (conn.responseCode) {
                200 -> Unit
                404 -> throw IOException("Couldn't find any releases. The GitHub page needs to be public for in-app updates.")
                403, 429 -> throw IOException("GitHub is limiting requests right now. Try again in a few minutes.")
                else -> throw IOException("GitHub answered with error ${conn.responseCode}.")
            }
            val body = conn.inputStream.use { it.readBytes(limit = 1_000_000) }.toString(Charsets.UTF_8)
            val info = parseRelease(body) ?: throw IOException("The latest release has no app file attached.")
            if (UpdateRules.isNewer(info.version, currentVersion)) UpdateCheck.Available(info) else UpdateCheck.UpToDate(currentVersion)
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads the update into the app's private cache and checks it. Returns the file. */
    suspend fun download(info: ReleaseInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        require(info.apkUrl.startsWith(UpdateRules.ASSET_PREFIX)) { "Unexpected download address." }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "update.apk")
        val conn = open(info.apkUrl)
        try {
            if (conn.responseCode != 200) throw IOException("The download failed (error ${conn.responseCode}).")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (done > MAX_BYTES) throw IOException("The download is far bigger than expected, so it was stopped.")
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (info.sizeBytes > 0 && done != info.sizeBytes) throw IOException("The download was incomplete. Please try again.")
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256 != null && actual != info.sha256) throw IOException("The downloaded file didn't match GitHub's checksum, so it was deleted.")
            file
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** Deletes every leftover downloaded update (there is only ever one, in the app's private cache). */
    fun cleanUp() {
        File(context.cacheDir, "updates").deleteRecursively()
    }

    /** Returns null if the file is safe to hand to Android, otherwise a plain-English problem. */
    fun problemWith(file: File): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: return "The downloaded file isn't a valid app."
        if (archive.packageName != context.packageName) return "The downloaded file is a different app, so it was not installed."
        val own = pm.getPackageInfo(context.packageName, flags)
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(own)) {
            return "The downloaded version isn't newer than the one you have."
        }
        if (Build.VERSION.SDK_INT >= 28) {
            val theirs = archive.signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }?.toSet()
            val mine = own.signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }?.toSet()
            if (theirs != null && mine != null && theirs != mine) {
                return "This update was signed with a different key, so Android won't install it over this copy. " +
                    "Uninstall this app once, then install the new one."
            }
        }
        return null
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the Android screen where the player lets this app install updates. */
    fun openInstallPermissionScreen() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the file to Android's installer (the player still taps Install). */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // ---------------------------------------------------------------- network plumbing

    /** Opens [start], following redirects by hand so every hop can be checked: HTTPS and a GitHub host only. */
    private fun open(start: String, accept: String? = null): HttpURLConnection {
        var url = URL(start)
        for (hop in 0..MAX_REDIRECTS) {
            if (url.protocol != "https" || !UpdateRules.hostAllowed(url.host)) {
                throw IOException("Blocked a connection to an unexpected address.")
            }
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "TimeKillerGames-Updater")
            if (accept != null) c.setRequestProperty("Accept", accept)
            val code = try {
                c.responseCode
            } catch (e: IOException) {
                c.disconnect()
                throw IOException("Couldn't reach GitHub. Check you're online (this is the one thing that needs internet).")
            }
            if (code in 301..308 && code != 304) {
                val location = c.getHeaderField("Location") ?: throw IOException("A redirect had no destination.")
                c.disconnect()
                url = URL(url, location)
                continue
            }
            return c
        }
        throw IOException("Too many redirects.")
    }

    private fun java.io.InputStream.readBytes(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > limit) throw IOException("The answer was far bigger than expected.")
        }
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_BYTES = 150L * 1024 * 1024
    }
}

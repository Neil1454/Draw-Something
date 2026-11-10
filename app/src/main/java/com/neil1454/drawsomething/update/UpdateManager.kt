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

/** One downloaded file found on the phone, and what the version check says about it. */
data class UpdateFile(
    val name: String,
    val sizeBytes: Long,
    /** The version inside the APK, or null if it could not be read. */
    val versionName: String?,
    val versionCode: Long?,
    /** Plain-English reason it is (not) needed. */
    val verdict: String,
    val keep: Boolean,
    val path: String,
)

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
        cleanUp() // only one update file is ever on the phone: remove anything older before starting
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // the version is part of the name, so a file can always be recognised: TimeKillerGames-v2.4.3.apk
        val safeVersion = info.version.filter { it.isLetterOrDigit() || it == '.' || it == '-' }.take(24).ifEmpty { "new" }
        val file = File(dir, "TimeKillerGames-v$safeVersion.apk")
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

    /** Folders where an update file could ever be: the app's private cache (internal and external). */
    private fun cacheDirs(): List<File> = listOfNotNull(context.cacheDir, context.externalCacheDir)

    /** Every file that could be a leftover: the whole "updates" folder, plus stray .apk/.tmp/.part files next to it. */
    private fun candidateFiles(): List<File> = cacheDirs().flatMap { dir ->
        val inFolder = File(dir, "updates").takeIf { it.exists() }?.walkTopDown()?.filter { it.isFile }?.toList().orEmpty()
        val strays = dir.listFiles().orEmpty().filter { it.isFile && (it.name.endsWith(".apk") || it.name.endsWith(".tmp") || it.name.endsWith(".part")) }
        inFolder + strays
    }

    private fun installedVersionCode(): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    /**
     * Looks at every downloaded file and decides whether it is still needed, by reading the version out of the
     * APK itself and comparing it with the version that is installed right now. Only the file that is being
     * installed at this moment ([inUse]) is ever kept.
     */
    fun inspect(inUse: File? = null): List<UpdateFile> {
        val pm = context.packageManager
        val mine = installedVersionCode()
        val inUsePath = inUse?.let { runCatching { it.canonicalPath }.getOrNull() }
        return candidateFiles().map { f ->
            val size = f.length()
            val info = if (f.name.endsWith(".apk")) {
                try { pm.getPackageArchiveInfo(f.path, 0) } catch (e: Exception) { null }
            } else null
            val code = info?.let { PackageInfoCompat.getLongVersionCode(it) }
            val name = info?.versionName
            val isInUse = inUsePath != null && runCatching { f.canonicalPath }.getOrNull() == inUsePath
            val (verdict, keep) = when {
                !f.name.endsWith(".apk") -> "Unfinished download" to false
                info == null || code == null -> "Damaged or unfinished" to false
                info.packageName != context.packageName -> "Not this app" to false
                code == mine -> "Already installed (v$name)" to false
                code < mine -> "Older than the installed version (v$name)" to false
                isInUse -> "Newer version, installing now (v$name)" to true
                else -> "Newer version that was never installed (v$name)" to false
            }
            UpdateFile(f.name, size, name, code, verdict, keep, f.path)
        }
    }

    /** Deletes every downloaded file that is not needed (by default that is all of them). Returns what it found. */
    fun sweep(inUse: File? = null): List<UpdateFile> {
        val report = inspect(inUse)
        report.filter { !it.keep }.forEach { runCatching { File(it.path).delete() } }
        // tidy empty folders
        cacheDirs().forEach { dir -> File(dir, "updates").takeIf { it.exists() && it.listFiles().isNullOrEmpty() }?.delete() }
        return report
    }

    /** Deletes every downloaded update file. */
    fun cleanUp() {
        sweep(null)
    }

    /** What is stored right now (after any clean-up). */
    fun stored(inUse: File? = null): List<UpdateFile> = inspect(inUse)

    /** How many bytes of downloaded update files are on the phone right now. */
    fun storedBytes(): Long = candidateFiles().sumOf { it.length() }

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

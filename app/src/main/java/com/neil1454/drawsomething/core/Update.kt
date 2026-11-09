package com.neil1454.drawsomething.core

import org.json.JSONObject

/** Where updates come from, and the rules for trusting them. Pure Kotlin so it can be tested. */
object UpdateRules {
    const val OWNER = "Neil1454"
    const val REPO = "Draw-Something"
    const val LATEST_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    /** The only place an update file may be downloaded from. */
    const val ASSET_PREFIX = "https://github.com/$OWNER/$REPO/releases/download/"

    /** Hosts we are willing to talk to, including GitHub's download redirects. HTTPS only. */
    fun hostAllowed(host: String): Boolean =
        host == "github.com" || host == "api.github.com" || host.endsWith(".githubusercontent.com")

    /** "v1.4.0" -> [1, 4, 0]. Anything that isn't a number is ignored. */
    fun parseVersion(s: String): List<Int> =
        s.trim().removePrefix("v").removePrefix("V").split('.', '-', '+')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

    /** True if [latest] is a higher version than [current] (1.10.0 beats 1.9.0). */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parseVersion(latest)
        val b = parseVersion(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val apkName: String,
    val apkUrl: String,
    val sizeBytes: Long,
    /** Lower-case hex SHA-256 from GitHub, when it provides one. */
    val sha256: String?,
    val notes: String,
)

/** Reads GitHub's "latest release" JSON. Null if it is unreadable or has no usable .apk. */
fun parseRelease(json: String): ReleaseInfo? = try {
    val o = JSONObject(json)
    if (o.optBoolean("draft", false) || o.optBoolean("prerelease", false)) {
        null
    } else {
        val tag = o.getString("tag_name").filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }.take(40)
        val assets = o.getJSONArray("assets")
        var found: ReleaseInfo? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.getString("name")
            val url = a.getString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.startsWith(UpdateRules.ASSET_PREFIX)) {
                val digest = a.optString("digest", "").removePrefix("sha256:").lowercase()
                found = ReleaseInfo(
                    tag = tag,
                    version = tag.removePrefix("v").removePrefix("V"),
                    apkName = name.filter { it.isLetterOrDigit() || it in ".-_" }.take(80),
                    apkUrl = url,
                    sizeBytes = a.optLong("size", 0L).coerceIn(0L, 500_000_000L),
                    sha256 = digest.takeIf { it.length == 64 && it.all { c -> c in "0123456789abcdef" } },
                    notes = o.optString("body", "").filter { !it.isISOControl() || it == '\n' }.trim().take(600),
                )
                break
            }
        }
        found
    }
} catch (e: Exception) {
    null
}

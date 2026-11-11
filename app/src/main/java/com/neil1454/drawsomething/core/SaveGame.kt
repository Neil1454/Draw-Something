package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject

// ====================================================================================
// Saved games. Long games (Monopoly, Ludo, Draughts) are saved on the host's phone as they are played, so a game
// can be finished later, even if the app is closed or crashes. The partner just joins again the normal way.
// Pure Kotlin so the format can be tested; SaveStore (Android) keeps the files.
// ====================================================================================

/** Games that are long enough to be worth saving. */
val GameKind.canSave: Boolean get() = this == GameKind.MONOPOLY || this == GameKind.LUDO || this == GameKind.DRAUGHTS

class SavedGame(
    val kind: GameKind,
    /** When it was saved (milliseconds since 1970). */
    val savedAt: Long,
    val host: String,
    val guest: String,
    /** A line such as "Turn 14 · Neil £1,320 · Teri £880". */
    val summary: String,
    val model: JSONObject,
)

object SaveFormat {
    const val VERSION = 1
    const val MAX_CHARS = 400_000

    fun encode(g: SavedGame): String = JSONObject()
        .put("v", VERSION).put("kind", g.kind.name).put("at", g.savedAt).put("host", g.host).put("guest", g.guest)
        .put("summary", g.summary).put("model", g.model).toString()

    /** Null if the text isn't a save this version understands. */
    fun decode(text: String): SavedGame? = try {
        if (text.length > MAX_CHARS) {
            null
        } else {
            val o = JSONObject(text)
            val kind = GameKind.values().firstOrNull { it.name == o.getString("kind") }
            if (o.getInt("v") != VERSION || kind == null || !kind.canSave) null
            else SavedGame(
                kind, o.getLong("at").coerceAtLeast(0), clean(o.optString("host"), 20), clean(o.optString("guest"), 24),
                clean(o.optString("summary"), 120), o.getJSONObject("model"),
            )
        }
    } catch (e: Exception) {
        null
    }

    private fun clean(s: String, max: Int) = s.filter { !it.isISOControl() }.trim().take(max)

    /** "just now", "5 minutes ago", "2 hours ago", "3 days ago". */
    fun ago(savedAt: Long, now: Long): String {
        val mins = ((now - savedAt) / 60_000).coerceAtLeast(0)
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "$mins minute${if (mins == 1L) "" else "s"} ago"
            mins < 60 * 24 -> "${mins / 60} hour${if (mins / 60 == 1L) "" else "s"} ago"
            else -> "${mins / (60 * 24)} day${if (mins / (60 * 24) == 1L) "" else "s"} ago"
        }
    }

    // small helpers for the engines
    fun ints(a: IntArray): JSONArray = JSONArray(a.toList())

    /** Reads [key] as exactly [size] ints, each within [range]; null if it isn't. */
    fun readInts(o: JSONObject, key: String, size: Int, range: IntRange): IntArray? {
        val arr = o.optJSONArray(key) ?: return null
        if (arr.length() != size) return null
        val out = IntArray(size)
        for (i in 0 until size) {
            val v = arr.optInt(i, Int.MIN_VALUE)
            if (v !in range) return null
            out[i] = v
        }
        return out
    }
}

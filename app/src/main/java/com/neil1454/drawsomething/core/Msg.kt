package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject

/** Everything the two phones say to each other. Small JSON messages. */
sealed class Msg {
    /** Host starts (or restarts) a game. */
    data class Setup(val rounds: Int, val seconds: Int, val hostDrawsFirst: Boolean, val minDifficulty: Int = 0) : Msg()

    /** "I agree to the rules." */
    data object RulesAgreed : Msg()

    /** Guesser -> drawer: "that drawing breaks this rule" (a [RuleBreak] name). */
    data class RuleReport(val rule: String) : Msg()

    /** Drawer -> guesser: did they agree it was a rule break? */
    data class ReportResponse(val accepted: Boolean) : Msg()

    /** The drawer is picking a word. */
    data class Choosing(val round: Int) : Msg()

    /** The drawer picked a word; the clock starts. The word itself is never sent. */
    data class RoundStart(val round: Int, val category: String, val pattern: String) : Msg()

    data class StrokePart(val id: Int, val color: Int, val width: Float, val pts: List<Float>) : Msg()
    data object Undo : Msg()
    data object Clear : Msg()

    /** The drawer skipped the word (costs them points). */
    data object Skipped : Msg()

    data class Guess(val text: String) : Msg()
    data class GuessDeclined(val text: String) : Msg()

    /** Final word on the round; both phones apply the same numbers. */
    data class RoundResult(
        val round: Int,
        val word: String,
        val category: String,
        val correct: Boolean,
        val drawerDelta: Int,
        val guesserDelta: Int,
        val elapsedMs: Long,
        val wrongGuesses: Int,
        val skips: Int,
        val multiplier: Double,
        val breaks: Int = 0,
    ) : Msg()

    /** "I've seen the result, let's move on." */
    data class Ready(val round: Int) : Msg()

    fun encode(): ByteArray = toJson().toString().toByteArray(Charsets.UTF_8)

    private fun toJson(): JSONObject {
        val o = JSONObject()
        when (this) {
            is Setup -> o.put("t", "setup").put("rounds", rounds).put("secs", seconds).put("hostFirst", hostDrawsFirst)
                .put("minDiff", minDifficulty)
            RulesAgreed -> o.put("t", "agree")
            is RuleReport -> o.put("t", "report").put("rule", rule)
            is ReportResponse -> o.put("t", "reportReply").put("ok", accepted)
            is Choosing -> o.put("t", "choosing").put("r", round)
            is RoundStart -> o.put("t", "start").put("r", round).put("cat", category).put("pat", pattern)
            is StrokePart -> {
                val arr = JSONArray()
                pts.forEach { arr.put(Math.round(it * 10000f) / 10000.0) }
                o.put("t", "stroke").put("id", id).put("c", color).put("w", width.toDouble()).put("p", arr)
            }
            Undo -> o.put("t", "undo")
            Clear -> o.put("t", "clear")
            Skipped -> o.put("t", "skipped")
            is Guess -> o.put("t", "guess").put("s", text)
            is GuessDeclined -> o.put("t", "declined").put("s", text)
            is RoundResult -> o.put("t", "result").put("r", round).put("word", word).put("cat", category)
                .put("ok", correct).put("dd", drawerDelta).put("gd", guesserDelta).put("ms", elapsedMs)
                .put("wrong", wrongGuesses).put("skips", skips).put("mult", multiplier).put("breaks", breaks)
            is Ready -> o.put("t", "ready").put("r", round)
        }
        return o
    }

    companion object {
        /** Anything bigger than this is not a message from this game. */
        const val MAX_BYTES = 64 * 1024
        const val MAX_STROKE_VALUES = 4000 // x,y pairs in a single message
        private const val MAX_TEXT = 40

        private fun clean(s: String, max: Int) = s.filter { !it.isISOControl() }.take(max)
        private fun unit(v: Double): Float = if (v.isNaN()) 0f else v.toFloat().coerceIn(0f, 1f)

        /**
         * Returns null for anything unreadable or implausible, so a damaged, hostile or
         * buggy packet can never crash the game. Numbers are clamped and text is trimmed.
         */
        fun decode(bytes: ByteArray): Msg? = try {
            if (bytes.size > MAX_BYTES) null else decodeJson(JSONObject(String(bytes, Charsets.UTF_8)))
        } catch (e: Exception) {
            null
        }

        internal fun decodeJson(o: JSONObject): Msg? = when (o.getString("t")) {
            "setup" -> Setup(
                o.getInt("rounds").coerceIn(2, 20), o.getInt("secs").coerceIn(30, 300),
                o.getBoolean("hostFirst"), o.optInt("minDiff", 0).coerceIn(0, 3),
            )
            "agree" -> RulesAgreed
            "report" -> RuleReport(clean(o.getString("rule"), 20))
            "reportReply" -> ReportResponse(o.getBoolean("ok"))
            "choosing" -> Choosing(o.getInt("r").coerceIn(0, 100))
            "start" -> RoundStart(
                o.getInt("r").coerceIn(0, 100), clean(o.getString("cat"), 30),
                o.getString("pat").filter { it == '_' || it == ' ' }.take(MAX_TEXT),
            )
            "stroke" -> {
                val arr = o.getJSONArray("p")
                val n = minOf(arr.length(), MAX_STROKE_VALUES) and 1.inv() // whole x,y pairs only
                StrokePart(
                    o.getInt("id"), o.getInt("c"), o.getDouble("w").toFloat().coerceIn(0.002f, 0.08f),
                    List(n) { unit(arr.getDouble(it)) },
                )
            }
            "undo" -> Undo
            "clear" -> Clear
            "skipped" -> Skipped
            "guess" -> Guess(clean(o.getString("s"), MAX_TEXT))
            "declined" -> GuessDeclined(clean(o.getString("s"), MAX_TEXT))
            "result" -> RoundResult(
                o.getInt("r").coerceIn(0, 100), clean(o.getString("word"), MAX_TEXT), clean(o.getString("cat"), 30),
                o.getBoolean("ok"), o.getInt("dd").coerceIn(-2000, 2000), o.getInt("gd").coerceIn(-2000, 2000),
                o.getLong("ms").coerceIn(0, 600_000), o.getInt("wrong").coerceIn(0, 1000),
                o.getInt("skips").coerceIn(0, 1000),
                o.getDouble("mult").let { if (it.isNaN()) 1.0 else it.coerceIn(1.0, 3.0) },
                o.optInt("breaks", 0).coerceIn(0, 1000),
            )
            "ready" -> Ready(o.getInt("r").coerceIn(0, 100))
            else -> null
        }
    }
}

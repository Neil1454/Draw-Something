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
        /** Returns null for anything unreadable, so a bad packet can never crash the game. */
        fun decode(bytes: ByteArray): Msg? = try {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            when (o.getString("t")) {
                "setup" -> Setup(o.getInt("rounds"), o.getInt("secs"), o.getBoolean("hostFirst"), o.optInt("minDiff", 0))
                "agree" -> RulesAgreed
                "report" -> RuleReport(o.getString("rule"))
                "reportReply" -> ReportResponse(o.getBoolean("ok"))
                "choosing" -> Choosing(o.getInt("r"))
                "start" -> RoundStart(o.getInt("r"), o.getString("cat"), o.getString("pat"))
                "stroke" -> {
                    val arr = o.getJSONArray("p")
                    StrokePart(o.getInt("id"), o.getInt("c"), o.getDouble("w").toFloat(), List(arr.length()) { arr.getDouble(it).toFloat() })
                }
                "undo" -> Undo
                "clear" -> Clear
                "skipped" -> Skipped
                "guess" -> Guess(o.getString("s"))
                "declined" -> GuessDeclined(o.getString("s"))
                "result" -> RoundResult(
                    o.getInt("r"), o.getString("word"), o.getString("cat"), o.getBoolean("ok"),
                    o.getInt("dd"), o.getInt("gd"), o.getLong("ms"), o.getInt("wrong"),
                    o.getInt("skips"), o.getDouble("mult"), o.optInt("breaks", 0),
                )
                "ready" -> Ready(o.getInt("r"))
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

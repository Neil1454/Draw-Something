package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// "Do you know your Holiday?": a head-to-head quiz about where you are going.
//
// HOW TO ADD A HOLIDAY: drop a JSON file into app/src/main/assets/holidays/ (see the README, or
// copy cape_verde_sal.json). Nothing else needs changing; the app finds it on start-up.
//
// HOW TO REMOVE THE FEATURE: set Features.HOLIDAY to false (hides it everywhere), or delete this
// file, HolidayScreens.kt, the assets/holidays folder and the HOLIDAY lines the compiler points at.
// ====================================================================================

/** Switches for optional features. */
object Features {
    /** The "Do you know your Holiday?" quiz. */
    const val HOLIDAY = true
}

class HolidayQuestion(
    val text: String,
    val options: List<String>,
    /** Index into [options] of the right answer. */
    val correct: Int,
    /** A "did you know" shown after the answer is revealed. */
    val fact: String,
    val category: String,
    /** "" for a plain text question, "pic:<name>" to show a picture, "map:<name>" to answer by tapping a map. */
    val art: String = "",
) {
    val isMap get() = art.startsWith("map:")
}

class HolidayPack(
    val id: String,
    val title: String,
    val emoji: String,
    val blurb: String,
    val questions: List<HolidayQuestion>,
) {
    companion object {
        private fun clean(s: String?, max: Int) = (s ?: "").filter { !it.isISOControl() || it == ' ' }.trim().take(max)

        /**
         * Reads a pack from JSON text:
         * {"id":"x","title":"..","emoji":"..","blurb":"..","questions":[{"q":"..","a":["..",".."],"c":0,"fact":"..","cat":".."}]}
         * Questions that don't make sense (bad answer number, too few options) are skipped. Null if nothing usable.
         */
        fun parse(text: String): HolidayPack? = try {
            val o = JSONObject(text)
            val id = clean(o.optString("id"), 40).lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            val title = clean(o.optString("title"), 40)
            val arr = o.optJSONArray("questions") ?: JSONArray()
            val qs = ArrayList<HolidayQuestion>()
            for (i in 0 until minOf(arr.length(), 500)) {
                val q = arr.optJSONObject(i) ?: continue
                val opts = q.optJSONArray("a") ?: continue
                val art = clean(q.optString("art"), 24).lowercase().filter { it.isLetterOrDigit() || it == ':' || it == '_' || it == '-' || it == ',' || it == '.' }
                val cap = if (art.startsWith("map:")) HolidayRules.MAX_OPTIONS else 4
                val options = List(minOf(opts.length(), cap)) { clean(opts.optString(it), 90) }.filter { it.isNotEmpty() }
                val c = q.optInt("c", -1)
                val qt = clean(q.optString("q"), 220)
                if (qt.isEmpty() || options.size < 3 || c !in 0 until minOf(opts.length(), cap) || opts.optString(c).isBlank()) continue
                // the answer index must survive removing any blank options before it
                val answerText = clean(opts.optString(c), 90)
                val ci = options.indexOf(answerText)
                if (ci < 0 || options.toSet().size != options.size) continue
                qs.add(HolidayQuestion(qt, options, ci, clean(q.optString("fact"), 260), clean(q.optString("cat"), 24), art))
            }
            if (id.isEmpty() || title.isEmpty() || qs.isEmpty()) null
            else HolidayPack(id, title, clean(o.optString("emoji", "🏝️"), 8).ifEmpty { "🏝️" }, clean(o.optString("blurb"), 200), qs)
        } catch (e: Exception) {
            null
        }
    }
}

/** The holidays this build knows about. The Android side fills it from the assets/holidays folder. */
object HolidayPacks {
    private var packs: List<HolidayPack> = emptyList()

    fun install(list: List<HolidayPack>) {
        packs = list.distinctBy { it.id }.sortedBy { it.title }
    }

    fun all(): List<HolidayPack> = packs
    fun byId(id: String?): HolidayPack? = packs.firstOrNull { it.id == id } ?: packs.firstOrNull()
}

enum class HolidayLength(val label: String, val questions: Int) {
    SHORT("Quick · 8", 8),
    STANDARD("Standard · 12", 12),
    LONG("Long · 20", 20),
}

object HolidayRules {
    const val DEFAULT_SECONDS = 30

    /** Most answers a question can have (map questions list the places you can tap). */
    const val MAX_OPTIONS = 10

    /** Time-per-question choices in the lobby. 0 means no timer at all. */
    val SECONDS = listOf(15, 30, 60, 0)

    /** With no timer, speed bonuses are worked out as if the question lasted this long. */
    const val UNTIMED_REFERENCE_MS = 20_000L

    // ---- the main round
    const val CORRECT = 100          // fastest correct answer
    const val SPEED_BONUS = 100      // up to this much extra for answering quickly
    const val ALSO_RIGHT = 40        // right, but not the quickest
    const val EXTENDED_RIGHT = 35    // right after pressing "give me more time": a flat score with no speed bonus
    const val WRONG = -25            // a wrong answer costs this (score never drops below 0)

    // ---- "give me more time"
    const val EXTRA_PER_QUIZ = 3     // how many times each player can ask in a quiz
    const val EXTRA_MIN_MS = 10_000L // the extra time is half the question time, but never less than this
    fun extraMs(limitMs: Long): Long = maxOf(EXTRA_MIN_MS, limitMs / 2)

    // ---- the bonus (redemption) round
    const val BONUS_MAX = 8          // most questions in a player's bonus round
    const val BONUS_SECONDS = 20     // time per bonus question (when the quiz has a timer)
    const val MISSED_REFUND = 10     // a question you didn't answer, or got wrong with no points to lose, is worth this
    const val PERFECT_RECALL = 10    // a bonus for getting every bonus question right
    const val BONUS_REVEAL_MS = 3500L

    const val GRACE_MS = 700L        // after the first right answer, wait this long for the other phone's answer
    const val LOCK_MS = 1900L        // both answers are shown (not yet marked right or wrong) for this long
    const val HURRY_SECS = 5         // the "hurry up" warning starts when this many seconds are left
    const val REVEAL_MS = 4500L
    const val REVEAL_NO_FACT_MS = 3000L
    const val MIN_REACTION_MS = 150
}

data class HolidayState(
    val myName: String,
    val theirName: String,
    val phase: GPhase = GPhase.LOBBY,
    /** ASK (question showing), LOCK (picks shown), REVEAL (answer showing) or BONUS (the redemption round). */
    val step: String = "",
    /** Changes with every question. */
    val seq: Int = 0,
    val index: Int = 0,
    val total: Int = 0,
    val packTitle: String = "",
    val packEmoji: String = "🏝️",
    val category: String = "",
    /** "", "pic:<name>" or "map:<name>" (see [HolidayQuestion.art]). */
    val art: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    /** 0 = no timer. */
    val limitSecs: Int = HolidayRules.DEFAULT_SECONDS,
    /** My seconds left on this question, or -1 when there is no timer. */
    val secsLeft: Int = HolidayRules.DEFAULT_SECONDS,
    /** The length of my time bar for this question (the limit, plus any extra time I asked for). */
    val myTotalSecs: Int = HolidayRules.DEFAULT_SECONDS,
    val myExtended: Boolean = false,
    val theirExtended: Boolean = false,
    /** How many times I can still ask for more time in this quiz. */
    val extraLeft: Int = HolidayRules.EXTRA_PER_QUIZ,
    val myChoice: Int = -1,
    val theyAnswered: Boolean = false,
    /** Revealed answer (display index), or -1 while the question is open. */
    val correct: Int = -1,
    val theirChoice: Int = -1,
    val myMs: Int = -1,
    val theirMs: Int = -1,
    val myDelta: Int = 0,
    val theirDelta: Int = 0,
    /** How each answer was scored: first, also, late (used extra time), wrong, none. */
    val myKind: String = "",
    val theirKind: String = "",
    val fact: String = "",
    val myScore: Int = 0,
    val theirScore: Int = 0,
    val myRight: Int = 0,
    val theirRight: Int = 0,
    /** What each player had after the main round, and what they won back in the bonus round. */
    val myMain: Int = 0,
    val theirMain: Int = 0,
    val myBonus: Int = 0,
    val theirBonus: Int = 0,
    val moves: Int = 0,
    // ---- my bonus round (only while step == "BONUS")
    val bonusOn: Boolean = false,
    /** ASK, REVEAL, DONE or NONE (I have no bonus questions). */
    val bStep: String = "",
    val bSeq: Int = 0,
    val bIndex: Int = 0,
    val bTotal: Int = 0,
    val bQuestion: String = "",
    val bCategory: String = "",
    val bArt: String = "",
    val bOptions: List<String> = emptyList(),
    val bChoice: Int = -1,
    val bCorrect: Int = -1,
    val bFact: String = "",
    /** Points I win back if I get this one right. */
    val bValue: Int = 0,
    val bSecsLeft: Int = -1,
    val bRight: Int = 0,
    val theirBIndex: Int = 0,
    val theirBTotal: Int = 0,
    val theirBDone: Boolean = false,
    /** 0 playing, 1 I won, 2 they won, 3 draw. */
    val result: Int = 0,
) {
    /** The bonus question dressed up as an ordinary one, so the same screen code can show it. */
    fun bonusView(): HolidayState = copy(
        step = if (bStep == "REVEAL") "REVEAL" else "ASK", seq = 1_000_000 + bSeq, index = bIndex, total = bTotal,
        category = bCategory, art = bArt, question = bQuestion, options = bOptions,
        limitSecs = if (bSecsLeft >= 0) HolidayRules.BONUS_SECONDS else 0, secsLeft = bSecsLeft, myTotalSecs = HolidayRules.BONUS_SECONDS,
        myChoice = bChoice, theyAnswered = false, correct = bCorrect, theirChoice = -1, fact = bFact,
        myDelta = if (bStep == "REVEAL" && bChoice == bCorrect) bValue else 0, theirDelta = 0, myExtended = false, theirExtended = false,
    )
}

class HolidayEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.HOLIDAY, myName, theirName, isHost, send, random, clock) {

    var state = HolidayState(myName, theirName)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    // ---- both phones
    private var askedAt = 0L
    private var myPick = -1
    private var bAskedAt = 0L
    private var bPick = -1

    // ---- host-only model (players: 1 = host, 2 = guest)
    private var qs: List<HolidayQuestion> = emptyList()
    private var packTitle = ""
    private var packEmoji = "🏝️"
    private var idx = 0
    private var seq = 0
    private var step = "ASK"
    private var perm: List<Int> = emptyList()
    private var limitMs = HolidayRules.DEFAULT_SECONDS * 1000L
    private var bonusEnabled = true
    private var startAt = 0L
    private var firstCorrectAt = -1L
    private var revealUntil = 0L
    private var lockUntil = 0L
    private var lastKey = ""
    private val deadline = LongArray(3)
    private val totalMs = LongArray(3)
    private val extendedNow = BooleanArray(3)
    private val extraUsed = IntArray(3)
    private val timedOut = BooleanArray(3)
    private val choice = IntArray(3) { -1 }
    private val ms = IntArray(3) { -1 }
    private val delta = IntArray(3)
    private val ansKind = Array(3) { "" }
    private val score = IntArray(3)
    private val mainScore = IntArray(3)
    private val bonusEarned = IntArray(3)
    private val right = IntArray(3)
    private var moves = 0
    private var over = false

    // the bonus round: each player has their own list of questions (index into qs) and what each is worth
    private val bq = Array(3) { ArrayList<Int>() }
    private val bv = Array(3) { ArrayList<Int>() }
    private val bIdx = IntArray(3)
    private val bStepOf = Array(3) { "NONE" }
    private val bPerm = Array(3) { emptyList<Int>() }
    private val bChoice = IntArray(3) { -1 }
    private val bStart = LongArray(3)
    private val bUntil = LongArray(3)
    private val bRight = IntArray(3)
    private val bSeq = IntArray(3)
    private val bLastSec = IntArray(3) { -1 }

    override fun hostBegin(opts: JSONObject) {
        val pack = HolidayPacks.byId(opts.optString("pack", ""))
        val len = HolidayLength.values()[opts.optInt("len", 1).coerceIn(0, HolidayLength.values().size - 1)].questions
        val secs = opts.optInt("secs", HolidayRules.DEFAULT_SECONDS).let { if (it <= 0) 0 else it.coerceIn(5, 120) }
        qs = pack?.questions?.shuffled(random)?.take(len) ?: emptyList()
        packTitle = pack?.title ?: ""
        packEmoji = pack?.emoji ?: "🏝️"
        limitMs = secs * 1000L
        bonusEnabled = opts.optBoolean("bonus", true)
        idx = 0; seq = 0; moves = 0; over = qs.isEmpty()
        score.fill(0); right.fill(0); mainScore.fill(0); bonusEarned.fill(0); extraUsed.fill(0)
        for (w in 1..2) { bq[w].clear(); bv[w].clear(); bStepOf[w] = "NONE"; bRight[w] = 0; bIdx[w] = 0 }
        if (!over) startQuestion(clock())
    }

    private fun current() = qs[idx]

    private fun startQuestion(now: Long) {
        seq++
        step = "ASK"
        perm = (0 until current().options.size).shuffled(random)
        choice.fill(-1); ms.fill(-1); delta.fill(0); ansKind.fill("")
        extendedNow.fill(false); timedOut.fill(false)
        firstCorrectAt = -1L
        startAt = now
        for (w in 1..2) { totalMs[w] = limitMs; deadline[w] = if (limitMs > 0) now + limitMs else Long.MAX_VALUE }
        lastKey = ""
    }

    private fun correctDisplay() = perm.indexOf(current().correct)

    private fun secsLeftOf(w: Int, now: Long): Int =
        if (limitMs == 0L) -1 else (((deadline[w] - now) + 999) / 1000).toInt().coerceAtLeast(0)

    /**
     * Host only: which option is right right now for player [who] (so the computer opponent can answer sensibly),
     * in the main round or in that player's bonus round.
     */
    fun secretCorrect(who: Int = if (isHost) 1 else 2): Int? {
        if (!isHost || over || qs.isEmpty()) return null
        if (step == "ASK") return correctDisplay()
        if (step == "BONUS" && bStepOf[who] == "ASK") return bPerm[who].indexOf(qs[bq[who][bIdx[who]]].correct)
        return null
    }

    /** Pick an answer (the position as shown on screen). [forcedMs] lets the computer opponent use its own reaction time. */
    fun pick(i: Int, forcedMs: Int? = null) {
        val s = state
        if (s.phase != GPhase.PLAYING || s.step != "ASK" || myPick != -1 || i !in s.options.indices) return
        myPick = i
        val took = (forcedMs ?: (clock() - askedAt).toInt()).coerceIn(HolidayRules.MIN_REACTION_MS, 600_000)
        state = state.copy(myChoice = i)
        onChange()
        act(JSONObject().put("a", "pick").put("i", i).put("ms", took).put("sq", s.seq))
    }

    /** "Give me more time": adds time to my clock for this question; a right answer is then worth a flat [HolidayRules.EXTENDED_RIGHT]. */
    fun moreTime() {
        val s = state
        if (s.phase != GPhase.PLAYING || s.step != "ASK" || s.myChoice != -1 || s.limitSecs <= 0 || s.myExtended || s.extraLeft <= 0) return
        act(JSONObject().put("a", "more").put("sq", s.seq))
    }

    /** Answer my bonus question (the position as shown on screen). */
    fun bonusPick(i: Int) {
        val s = state
        if (s.phase != GPhase.PLAYING || s.step != "BONUS" || s.bStep != "ASK" || bPick != -1 || i !in s.bOptions.indices) return
        bPick = i
        state = state.copy(bChoice = i)
        onChange()
        act(JSONObject().put("a", "bpick").put("i", i).put("bq", s.bSeq))
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (over) return
        val who = if (fromHost) 1 else 2
        when (a.getString("a")) {
            "pick" -> {
                if (step != "ASK" || a.getInt("sq") != seq || choice[who] != -1 || timedOut[who]) return
                val i = a.getInt("i")
                if (i !in perm.indices) return
                val now = clock()
                val cap = if (limitMs > 0) (totalMs[who]).toInt() else 600_000
                val took = a.getInt("ms").coerceIn(HolidayRules.MIN_REACTION_MS, cap.coerceAtLeast(HolidayRules.MIN_REACTION_MS))
                choice[who] = i
                ms[who] = took
                if (i == correctDisplay() && firstCorrectAt < 0L && !extendedNow[who]) firstCorrectAt = now
                if (answeredOrOut(1) && answeredOrOut(2)) resolve(now) else publish()
            }
            "more" -> {
                if (step != "ASK" || a.getInt("sq") != seq || limitMs == 0L) return
                val now = clock()
                if (choice[who] != -1 || timedOut[who] || extendedNow[who] || extraUsed[who] >= HolidayRules.EXTRA_PER_QUIZ || now >= deadline[who]) return
                val extra = HolidayRules.extraMs(limitMs)
                deadline[who] += extra
                totalMs[who] += extra
                extendedNow[who] = true
                extraUsed[who]++
                publish()
            }
            "bpick" -> {
                if (step != "BONUS" || bStepOf[who] != "ASK" || a.getInt("bq") != bSeq[who] || bChoice[who] != -1) return
                val i = a.getInt("i")
                if (i !in bPerm[who].indices) return
                answerBonus(who, i, clock())
                publish()
            }
        }
    }

    private fun answeredOrOut(w: Int) = choice[w] != -1 || timedOut[w]

    override fun hostTick(now: Long) {
        if (over) return
        when (step) {
            "ASK" -> {
                if (limitMs > 0) for (w in 1..2) if (choice[w] == -1 && !timedOut[w] && now >= deadline[w]) timedOut[w] = true
                val rival = { w: Int -> !answeredOrOut(w) }
                when {
                    answeredOrOut(1) && answeredOrOut(2) -> resolve(now)
                    // someone got it right: the other player has a moment more, unless they asked for extra time
                    firstCorrectAt >= 0L && now >= firstCorrectAt + HolidayRules.GRACE_MS && (1..2).none { rival(it) && extendedNow[it] } -> resolve(now)
                    else -> {
                        val key = "${secsLeftOf(1, now)}/${secsLeftOf(2, now)}"
                        if (key != lastKey) { lastKey = key; publish() }
                    }
                }
            }
            "LOCK" -> if (now >= lockUntil) reveal(now)
            "REVEAL" -> if (now >= revealUntil) {
                if (idx + 1 >= qs.size) {
                    startBonusOrFinish(now)
                } else {
                    idx++
                    startQuestion(now)
                    publish()
                }
            }
            "BONUS" -> bonusTick(now)
        }
    }

    /** Both answers are in (or time is up): show who picked what, without saying who is right yet. */
    private fun resolve(now: Long) {
        if (choice[1] == -1 && choice[2] == -1) {
            reveal(now)
            return
        }
        step = "LOCK"
        lockUntil = now + HolidayRules.LOCK_MS
        publish()
    }

    private fun reveal(now: Long) {
        step = "REVEAL"
        val cd = correctDisplay()
        for (w in 1..2) {
            ansKind[w] = when {
                choice[w] == -1 -> "none"
                choice[w] != cd -> "wrong"
                extendedNow[w] -> "late"
                else -> "also"
            }
        }
        val quickest = (1..2).filter { ansKind[it] == "also" }.minOfOrNull { ms[it] }
        for (w in 1..2) {
            if (ansKind[w] == "also" && ms[w] == quickest) ansKind[w] = "first"
            delta[w] = when (ansKind[w]) {
                "wrong" -> HolidayRules.WRONG
                "late" -> HolidayRules.EXTENDED_RIGHT
                "first" -> HolidayRules.CORRECT + speedBonus(ms[w])
                "also" -> HolidayRules.ALSO_RIGHT
                else -> 0
            }
            val before = score[w]
            score[w] = (score[w] + delta[w]).coerceAtLeast(0)
            if (ansKind[w] == "first" || ansKind[w] == "also" || ansKind[w] == "late") right[w]++
            // a question you can win back in the bonus round
            if (ansKind[w] == "wrong" || ansKind[w] == "none") {
                val lost = before - score[w]
                bq[w].add(idx)
                bv[w].add(if (ansKind[w] == "wrong" && lost > 0) lost else HolidayRules.MISSED_REFUND)
            }
        }
        moves++
        revealUntil = now + if (current().fact.isBlank()) HolidayRules.REVEAL_NO_FACT_MS else HolidayRules.REVEAL_MS
        publish()
    }

    private fun speedBonus(took: Int): Int {
        val frac = 1.0 - took.toDouble() / (if (limitMs > 0) limitMs else HolidayRules.UNTIMED_REFERENCE_MS)
        return ((frac.coerceIn(0.0, 1.0) * HolidayRules.SPEED_BONUS) / 5).toInt() * 5
    }

    // ---------------------------------------------------------------- the bonus round

    private fun startBonusOrFinish(now: Long) {
        mainScore[1] = score[1]; mainScore[2] = score[2]
        for (w in 1..2) {
            // keep the most valuable questions if there are too many, in the order they were asked
            val order = bq[w].indices.sortedByDescending { bv[w][it] }.take(HolidayRules.BONUS_MAX).sorted()
            val qList = order.map { bq[w][it] }
            val vList = order.map { bv[w][it] }
            bq[w].clear(); bq[w].addAll(qList)
            bv[w].clear(); bv[w].addAll(vList)
        }
        if (!bonusEnabled || (bq[1].isEmpty() && bq[2].isEmpty())) {
            over = true
            publish()
            return
        }
        step = "BONUS"
        for (w in 1..2) {
            bIdx[w] = 0; bRight[w] = 0; bSeq[w] = 0
            if (bq[w].isEmpty()) bStepOf[w] = "NONE" else startBonusQuestion(w, now)
        }
        publish()
    }

    private fun startBonusQuestion(w: Int, now: Long) {
        val q = qs[bq[w][bIdx[w]]]
        bSeq[w]++
        bStepOf[w] = "ASK"
        bPerm[w] = (0 until q.options.size).shuffled(random)
        bChoice[w] = -1
        bStart[w] = now
        bLastSec[w] = -1
    }

    private fun answerBonus(w: Int, pick: Int, now: Long) {
        val q = qs[bq[w][bIdx[w]]]
        bChoice[w] = pick
        val ok = bPerm[w][pick] == q.correct
        if (ok) {
            bRight[w]++
            val v = bv[w][bIdx[w]]
            score[w] += v
            bonusEarned[w] += v
        }
        bStepOf[w] = "REVEAL"
        bUntil[w] = now + HolidayRules.BONUS_REVEAL_MS
    }

    private fun bonusTick(now: Long) {
        var changed = false
        for (w in 1..2) {
            when (bStepOf[w]) {
                "ASK" -> if (limitMs > 0 && now >= bStart[w] + HolidayRules.BONUS_SECONDS * 1000L) {
                    bChoice[w] = -1
                    bStepOf[w] = "REVEAL"
                    bUntil[w] = now + HolidayRules.BONUS_REVEAL_MS
                    changed = true
                } else if (limitMs > 0) {
                    val sec = ((bStart[w] + HolidayRules.BONUS_SECONDS * 1000L - now + 999) / 1000).toInt()
                    if (sec != bLastSec[w]) { bLastSec[w] = sec; changed = true }
                }
                "REVEAL" -> if (now >= bUntil[w]) {
                    if (bIdx[w] + 1 >= bq[w].size) {
                        bStepOf[w] = "DONE"
                    } else {
                        bIdx[w]++
                        startBonusQuestion(w, now)
                    }
                    changed = true
                }
            }
        }
        if ((1..2).all { bStepOf[it] == "DONE" || bStepOf[it] == "NONE" }) {
            for (w in 1..2) if (bq[w].isNotEmpty() && bRight[w] == bq[w].size) {
                score[w] += HolidayRules.PERFECT_RECALL
                bonusEarned[w] += HolidayRules.PERFECT_RECALL
            }
            over = true
            publish()
        } else if (changed) {
            publish()
        }
    }

    private fun bonusJson(w: Int, now: Long, forWho: Int): JSONObject {
        val o = JSONObject().put("st", bStepOf[w]).put("ix", bIdx[w] + 1).put("n", bq[w].size).put("rt", bRight[w]).put("sq", bSeq[w])
        if (bStepOf[w] == "ASK" || bStepOf[w] == "REVEAL") {
            val q = qs[bq[w][bIdx[w]]]
            o.put("q", q.text).put("ct", q.category).put("ar", q.art).put("op", JSONArray(bPerm[w].map { q.options[it] })).put("val", bv[w][bIdx[w]])
            if (limitMs > 0 && bStepOf[w] == "ASK") o.put("sl", ((bStart[w] + HolidayRules.BONUS_SECONDS * 1000L - now + 999) / 1000).toInt().coerceIn(0, HolidayRules.BONUS_SECONDS))
            if (bStepOf[w] == "REVEAL") o.put("ch", bChoice[w]).put("cr", bPerm[w].indexOf(q.correct)).put("ft", q.fact)
        }
        return o
    }

    override fun hostSnapshot(): JSONObject {
        val now = clock()
        val reveal = step == "REVEAL" || over
        val o = JSONObject()
            .put("st", if (over) "END" else step).put("sq", seq).put("ix", idx + 1).put("n", qs.size)
            .put("pk", packTitle).put("em", packEmoji).put("lm", (limitMs / 1000).toInt())
            .put("sl1", secsLeftOf(1, now)).put("sl2", secsLeftOf(2, now))
            .put("tt1", (totalMs[1] / 1000).toInt()).put("tt2", (totalMs[2] / 1000).toInt())
            .put("x1", extendedNow[1]).put("x2", extendedNow[2]).put("xl1", HolidayRules.EXTRA_PER_QUIZ - extraUsed[1]).put("xl2", HolidayRules.EXTRA_PER_QUIZ - extraUsed[2])
            .put("a1", choice[1] != -1).put("a2", choice[2] != -1)
            .put("s1", score[1]).put("s2", score[2]).put("r1", right[1]).put("r2", right[2]).put("mv", moves)
            .put("m1", mainScore[1]).put("m2", mainScore[2]).put("e1", bonusEarned[1]).put("e2", bonusEarned[2])
            .put("ov", over)
        if (bonusEnabled && (step == "BONUS" || over && (bq[1].isNotEmpty() || bq[2].isNotEmpty()))) {
            o.put("bn", true)
            o.put("B1", bonusJson(1, now, 1)).put("B2", bonusJson(2, now, 2))
        }
        if (qs.isNotEmpty() && step != "BONUS" && !(over && bStepOf[1] != "NONE" || over && bStepOf[2] != "NONE")) {
            val q = current()
            o.put("q", q.text).put("ct", q.category).put("ar", q.art).put("op", JSONArray(perm.map { q.options[it] }))
            if (step == "LOCK") o.put("c1", choice[1]).put("c2", choice[2]) // picks are shown, the right answer is not
            if (reveal) {
                o.put("cr", correctDisplay()).put("c1", choice[1]).put("c2", choice[2])
                    .put("ms1", ms[1]).put("ms2", ms[2]).put("d1", delta[1]).put("d2", delta[2]).put("k1", ansKind[1]).put("k2", ansKind[2]).put("ft", q.fact)
            }
        }
        return o
    }

    override fun adopt(s: JSONObject) {
        val seq = s.getInt("sq").coerceIn(0, 100_000)
        val fresh = seq != state.seq
        if (fresh) {
            askedAt = clock()
            myPick = -1
        }
        val me1 = isHost
        val ov = s.optBoolean("ov", false)
        val stRaw = s.getString("st").take(8)
        val opsArr = s.optJSONArray("op")
        val options = if (opsArr == null) emptyList() else List(minOf(opsArr.length(), HolidayRules.MAX_OPTIONS)) { opsArr.optString(it, "").filter { c -> !c.isISOControl() }.take(100) }
        val revealed = stRaw == "REVEAL" || stRaw == "END"
        val picksShown = revealed || stRaw == "LOCK"
        val s1 = s.getInt("s1").coerceIn(0, 100_000)
        val s2 = s.getInt("s2").coerceIn(0, 100_000)
        val mine = if (me1) s1 else s2
        val theirs = if (me1) s2 else s1
        fun pickOf(key: String) = if (picksShown) s.optInt(key, -1).coerceIn(-1, HolidayRules.MAX_OPTIONS - 1) else -1
        val c1 = pickOf("c1")
        val c2 = pickOf("c2")
        fun kindOf(key: String) = if (revealed) s.optString(key, "").take(6) else ""
        fun mineOr(a: String, b: String) = if (me1) a else b
        // my bonus round
        val bn = s.optJSONObject(mineOr("B1", "B2"))
        val bt = s.optJSONObject(mineOr("B2", "B1"))
        val bFresh = bn != null && bn.optInt("sq", 0) != state.bSeq
        if (bFresh) { bAskedAt = clock(); bPick = -1 }
        val bOpsArr = bn?.optJSONArray("op")
        val bOptions = if (bOpsArr == null) emptyList() else List(minOf(bOpsArr.length(), 4)) { bOpsArr.optString(it, "").filter { c -> !c.isISOControl() }.take(100) }
        val bStepNow = bn?.optString("st", "")?.take(6) ?: ""
        val bRevealed = bStepNow == "REVEAL"
        state = state.copy(
            phase = if (ov) GPhase.OVER else GPhase.PLAYING,
            step = if (stRaw == "END") "REVEAL" else stRaw,
            seq = seq,
            index = s.getInt("ix").coerceIn(0, 100),
            total = s.getInt("n").coerceIn(0, 100),
            packTitle = s.optString("pk", "").filter { !it.isISOControl() }.take(40),
            packEmoji = s.optString("em", "🏝️").filter { !it.isISOControl() }.take(8),
            category = s.optString("ct", "").filter { !it.isISOControl() }.take(24),
            art = s.optString("ar", "").filter { it.isLetterOrDigit() || it == ':' || it == '_' || it == '-' || it == ',' || it == '.' }.take(24),
            question = s.optString("q", "").filter { !it.isISOControl() }.take(220),
            options = options,
            limitSecs = s.getInt("lm").coerceIn(0, 120),
            secsLeft = s.optInt(mineOr("sl1", "sl2"), -1).coerceIn(-1, 400),
            myTotalSecs = s.optInt(mineOr("tt1", "tt2"), 30).coerceIn(0, 400),
            myExtended = s.optBoolean(mineOr("x1", "x2"), false),
            theirExtended = s.optBoolean(mineOr("x2", "x1"), false),
            extraLeft = s.optInt(mineOr("xl1", "xl2"), 0).coerceIn(0, 10),
            myChoice = if (picksShown) (if (me1) c1 else c2) else myPick,
            theyAnswered = s.optBoolean(if (me1) "a2" else "a1", false),
            correct = if (revealed) s.optInt("cr", -1).coerceIn(-1, HolidayRules.MAX_OPTIONS - 1) else -1,
            theirChoice = if (me1) c2 else c1,
            myMs = if (revealed) s.optInt(mineOr("ms1", "ms2"), -1).coerceIn(-1, 600_000) else -1,
            theirMs = if (revealed) s.optInt(mineOr("ms2", "ms1"), -1).coerceIn(-1, 600_000) else -1,
            myDelta = if (revealed) s.optInt(mineOr("d1", "d2"), 0).coerceIn(-1000, 1000) else 0,
            theirDelta = if (revealed) s.optInt(mineOr("d2", "d1"), 0).coerceIn(-1000, 1000) else 0,
            myKind = kindOf(mineOr("k1", "k2")), theirKind = kindOf(mineOr("k2", "k1")),
            fact = if (revealed) s.optString("ft", "").filter { !it.isISOControl() }.take(260) else "",
            myScore = mine, theirScore = theirs,
            myRight = s.getInt(if (me1) "r1" else "r2").coerceIn(0, 100),
            theirRight = s.getInt(if (me1) "r2" else "r1").coerceIn(0, 100),
            myMain = s.optInt(mineOr("m1", "m2"), 0).coerceIn(0, 100_000), theirMain = s.optInt(mineOr("m2", "m1"), 0).coerceIn(0, 100_000),
            myBonus = s.optInt(mineOr("e1", "e2"), 0).coerceIn(0, 100_000), theirBonus = s.optInt(mineOr("e2", "e1"), 0).coerceIn(0, 100_000),
            moves = s.optInt("mv", 0).coerceIn(0, 100_000),
            bonusOn = s.optBoolean("bn", false),
            bStep = bStepNow,
            bSeq = bn?.optInt("sq", 0)?.coerceIn(0, 1000) ?: 0,
            bIndex = bn?.optInt("ix", 0)?.coerceIn(0, 100) ?: 0,
            bTotal = bn?.optInt("n", 0)?.coerceIn(0, 100) ?: 0,
            bQuestion = bn?.optString("q", "")?.filter { !it.isISOControl() }?.take(220) ?: "",
            bCategory = bn?.optString("ct", "")?.filter { !it.isISOControl() }?.take(24) ?: "",
            bArt = bn?.optString("ar", "")?.filter { it.isLetterOrDigit() || it == ':' || it == '_' || it == '-' || it == ',' || it == '.' }?.take(24) ?: "",
            bOptions = bOptions,
            bChoice = if (bRevealed) (bn?.optInt("ch", -1)?.coerceIn(-1, 3) ?: -1) else bPick,
            bCorrect = if (bRevealed) (bn?.optInt("cr", -1)?.coerceIn(-1, 3) ?: -1) else -1,
            bFact = if (bRevealed) (bn?.optString("ft", "")?.filter { !it.isISOControl() }?.take(260) ?: "") else "",
            bValue = bn?.optInt("val", 0)?.coerceIn(0, 1000) ?: 0,
            bSecsLeft = bn?.optInt("sl", -1)?.coerceIn(-1, 60) ?: -1,
            bRight = bn?.optInt("rt", 0)?.coerceIn(0, 100) ?: 0,
            theirBIndex = bt?.optInt("ix", 0)?.coerceIn(0, 100) ?: 0,
            theirBTotal = bt?.optInt("n", 0)?.coerceIn(0, 100) ?: 0,
            theirBDone = bt?.optString("st", "") .let { it == "DONE" || it == "NONE" },
            result = if (!ov) 0 else if (mine > theirs) 1 else if (mine < theirs) 2 else 3,
        )
    }

    override fun reset() {
        myPick = -1
        bPick = -1
        over = false
        state = state.copy(phase = GPhase.LOBBY, step = "", result = 0, myChoice = -1, correct = -1, bonusOn = false, bStep = "")
    }
}

/** The computer's quiz player: answers after a human-like delay, right some of the time. */
class HolidayBrain(
    private val e: HolidayEngine,
    private val isHost: Boolean,
    private val keyFromHost: () -> Int?,
    skill: Skill,
    random: Random,
) : Brain(skill, random) {
    private var seq = -1
    private var plan = -1L
    private var planMs = 0
    private var planned = -1
    private var bSeq = -1
    private var bPlan = -1L
    private var bPlanned = -1

    private fun key(): Int? = if (isHost) e.secretCorrect(1) else keyFromHost()

    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING) return
        if (s.step == "BONUS") {
            bonusThink(now, s)
            return
        }
        if (s.seq != seq) {
            seq = s.seq
            planned = -1
            plan = -1
            if (s.step != "ASK" || s.options.isEmpty()) return
            val limit = if (s.limitSecs > 0) s.limitSecs * 1000 else 20_000
            planMs = when (skill) {
                Skill.EASY -> random.nextInt(limit * 35 / 100, limit * 80 / 100)
                Skill.NORMAL -> random.nextInt(limit * 22 / 100, limit * 60 / 100)
                Skill.HARD -> random.nextInt(limit * 12 / 100, limit * 40 / 100)
            }
            plan = now + planMs
            val knows = pct(when (skill) { Skill.EASY -> 45; Skill.NORMAL -> 68; Skill.HARD -> 88 })
            val key = key()
            planned = if (key != null && knows) key else {
                val others = s.options.indices.filter { it != key }
                others[random.nextInt(others.size)]
            }
        }
        if (plan > 0 && now >= plan && planned >= 0 && s.myChoice == -1) {
            plan = -1
            e.pick(planned, forcedMs = planMs)
        }
    }

    private fun bonusThink(now: Long, s: HolidayState) {
        if (s.bStep != "ASK" || s.bOptions.isEmpty()) return
        if (s.bSeq != bSeq) {
            bSeq = s.bSeq
            bPlan = now + random.nextInt(3_000, 9_000)
            // remembering is harder than recognising
            val knows = pct(when (skill) { Skill.EASY -> 35; Skill.NORMAL -> 55; Skill.HARD -> 80 })
            val key = key()
            bPlanned = if (key != null && knows) key else {
                val others = s.bOptions.indices.filter { it != key }
                others[random.nextInt(others.size)]
            }
        }
        if (bPlan > 0 && now >= bPlan && bPlanned >= 0 && s.bChoice == -1) {
            bPlan = -1
            e.bonusPick(bPlanned)
        }
    }
}

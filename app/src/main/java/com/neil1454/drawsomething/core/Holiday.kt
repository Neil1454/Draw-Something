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
)

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
                val options = List(minOf(opts.length(), 4)) { clean(opts.optString(it), 90) }.filter { it.isNotEmpty() }
                val c = q.optInt("c", -1)
                val qt = clean(q.optString("q"), 220)
                if (qt.isEmpty() || options.size < 3 || c !in 0 until minOf(opts.length(), 4) || opts.optString(c).isBlank()) continue
                // the answer index must survive removing any blank options before it
                val answerText = clean(opts.optString(c), 90)
                val ci = options.indexOf(answerText)
                if (ci < 0 || options.toSet().size != options.size) continue
                qs.add(HolidayQuestion(qt, options, ci, clean(q.optString("fact"), 260), clean(q.optString("cat"), 24)))
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

    /** Time-per-question choices in the lobby. 0 means no timer at all. */
    val SECONDS = listOf(15, 30, 60, 0)

    /** With no timer, speed bonuses are worked out as if the question lasted this long. */
    const val UNTIMED_REFERENCE_MS = 20_000L
    const val CORRECT = 100          // fastest correct answer
    const val SPEED_BONUS = 100      // up to this much extra for answering quickly
    const val ALSO_RIGHT = 40        // right, but not the quickest
    const val WRONG = -25            // a wrong answer costs this (score never drops below 0)
    const val GRACE_MS = 700L        // after the first right answer, wait this long for the other phone's answer
    const val REVEAL_MS = 4500L
    const val REVEAL_NO_FACT_MS = 3000L
    const val MIN_REACTION_MS = 150
}

data class HolidayState(
    val myName: String,
    val theirName: String,
    val phase: GPhase = GPhase.LOBBY,
    /** ASK (question showing) or REVEAL (answer showing). */
    val step: String = "",
    /** Changes with every question. */
    val seq: Int = 0,
    val index: Int = 0,
    val total: Int = 0,
    val packTitle: String = "",
    val packEmoji: String = "🏝️",
    val category: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    /** 0 = no timer. */
    val limitSecs: Int = HolidayRules.DEFAULT_SECONDS,
    /** -1 when there is no timer. */
    val secsLeft: Int = HolidayRules.DEFAULT_SECONDS,
    val myChoice: Int = -1,
    val theyAnswered: Boolean = false,
    /** Revealed answer (display index), or -1 while the question is open. */
    val correct: Int = -1,
    val theirChoice: Int = -1,
    val myMs: Int = -1,
    val theirMs: Int = -1,
    val myDelta: Int = 0,
    val theirDelta: Int = 0,
    val fact: String = "",
    val myScore: Int = 0,
    val theirScore: Int = 0,
    val myRight: Int = 0,
    val theirRight: Int = 0,
    val moves: Int = 0,
    /** 0 playing, 1 I won, 2 they won, 3 draw. */
    val result: Int = 0,
)

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

    // ---- host-only model
    private var qs: List<HolidayQuestion> = emptyList()
    private var packTitle = ""
    private var packEmoji = "🏝️"
    private var idx = 0
    private var seq = 0
    private var step = "ASK"
    private var perm: List<Int> = emptyList()
    private var limitMs = HolidayRules.DEFAULT_SECONDS * 1000L
    private var startAt = 0L
    private var firstCorrectAt = -1L
    private var revealUntil = 0L
    private var lastSecs = -1
    private val choice = IntArray(3) { -1 }
    private val ms = IntArray(3) { -1 }
    private val delta = IntArray(3)
    private val score = IntArray(3)
    private val right = IntArray(3)
    private var moves = 0
    private var over = false

    override fun hostBegin(opts: JSONObject) {
        val pack = HolidayPacks.byId(opts.optString("pack", ""))
        val len = HolidayLength.values()[opts.optInt("len", 1).coerceIn(0, HolidayLength.values().size - 1)].questions
        val secs = opts.optInt("secs", HolidayRules.DEFAULT_SECONDS).let { if (it <= 0) 0 else it.coerceIn(5, 120) }
        qs = pack?.questions?.shuffled(random)?.take(len) ?: emptyList()
        packTitle = pack?.title ?: ""
        packEmoji = pack?.emoji ?: "🏝️"
        limitMs = secs * 1000L
        idx = 0; seq = 0; moves = 0; over = qs.isEmpty()
        score.fill(0); right.fill(0)
        if (!over) startQuestion(clock())
    }

    private fun current() = qs[idx]

    private fun startQuestion(now: Long) {
        seq++
        step = "ASK"
        perm = (0 until current().options.size).shuffled(random)
        choice.fill(-1); ms.fill(-1); delta.fill(0)
        firstCorrectAt = -1L
        startAt = now
        lastSecs = if (limitMs > 0) (limitMs / 1000).toInt() else -1
    }

    private fun correctDisplay() = perm.indexOf(current().correct)

    /** Host only: which option is right right now (so the computer opponent can answer sensibly). */
    fun secretCorrect(): Int? = if (isHost && !over && step == "ASK" && qs.isNotEmpty()) correctDisplay() else null

    /** Pick an answer (the position as shown on screen). [forcedMs] lets the computer opponent use its own reaction time. */
    fun pick(i: Int, forcedMs: Int? = null) {
        val s = state
        if (s.phase != GPhase.PLAYING || s.step != "ASK" || myPick != -1 || i !in s.options.indices) return
        myPick = i
        val took = (forcedMs ?: (clock() - askedAt).toInt()).coerceIn(HolidayRules.MIN_REACTION_MS, 120_000)
        state = state.copy(myChoice = i)
        onChange()
        act(JSONObject().put("a", "pick").put("i", i).put("ms", took).put("sq", s.seq))
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (over || step != "ASK" || a.getString("a") != "pick" || a.getInt("sq") != seq) return
        val who = if (fromHost) 1 else 2
        if (choice[who] != -1) return
        val i = a.getInt("i")
        if (i !in perm.indices) return
        val now = clock()
        val took = a.getInt("ms").coerceIn(HolidayRules.MIN_REACTION_MS, if (limitMs > 0) limitMs.toInt() else 600_000)
        choice[who] = i
        ms[who] = took
        if (i == correctDisplay() && firstCorrectAt < 0L) firstCorrectAt = now
        if (choice[1] != -1 && choice[2] != -1) resolve(now) else publish()
    }

    override fun hostTick(now: Long) {
        if (over) return
        if (step == "ASK") {
            when {
                firstCorrectAt >= 0L && now >= firstCorrectAt + HolidayRules.GRACE_MS -> resolve(now)
                limitMs > 0 && now >= startAt + limitMs -> resolve(now)
                limitMs == 0L -> Unit
                else -> {
                    val left = (((startAt + limitMs - now) + 999) / 1000).toInt().coerceAtLeast(0)
                    if (left != lastSecs) {
                        lastSecs = left
                        publish()
                    }
                }
            }
        } else if (now >= revealUntil) {
            if (idx + 1 >= qs.size) {
                over = true
                publish()
            } else {
                idx++
                startQuestion(now)
                publish()
            }
        }
    }

    private fun resolve(now: Long) {
        step = "REVEAL"
        val cd = correctDisplay()
        val rightWho = (1..2).filter { choice[it] == cd }
        val quickest = rightWho.minOfOrNull { ms[it] }
        for (who in 1..2) {
            delta[who] = when {
                choice[who] == -1 -> 0
                choice[who] != cd -> HolidayRules.WRONG
                ms[who] == quickest -> HolidayRules.CORRECT + speedBonus(ms[who])
                else -> HolidayRules.ALSO_RIGHT
            }
            if (choice[who] == cd) right[who]++
            score[who] = (score[who] + delta[who]).coerceAtLeast(0)
        }
        moves++
        revealUntil = now + if (current().fact.isBlank()) HolidayRules.REVEAL_NO_FACT_MS else HolidayRules.REVEAL_MS
        publish()
    }

    private fun speedBonus(took: Int): Int {
        val frac = 1.0 - took.toDouble() / (if (limitMs > 0) limitMs else HolidayRules.UNTIMED_REFERENCE_MS)
        return ((frac.coerceIn(0.0, 1.0) * HolidayRules.SPEED_BONUS) / 5).toInt() * 5
    }

    override fun hostSnapshot(): JSONObject {
        val reveal = step == "REVEAL" || over
        val o = JSONObject()
            .put("st", if (over) "END" else step).put("sq", seq).put("ix", idx + 1).put("n", qs.size)
            .put("pk", packTitle).put("em", packEmoji).put("lm", (limitMs / 1000).toInt()).put("sl", lastSecs)
            .put("a1", choice[1] != -1).put("a2", choice[2] != -1)
            .put("s1", score[1]).put("s2", score[2]).put("r1", right[1]).put("r2", right[2]).put("mv", moves)
            .put("ov", over)
        if (qs.isNotEmpty()) {
            val q = current()
            o.put("q", q.text).put("ct", q.category).put("op", JSONArray(perm.map { q.options[it] }))
            if (reveal) {
                o.put("cr", correctDisplay()).put("c1", choice[1]).put("c2", choice[2])
                    .put("m1", ms[1]).put("m2", ms[2]).put("d1", delta[1]).put("d2", delta[2]).put("ft", q.fact)
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
        val options = if (opsArr == null) emptyList() else List(minOf(opsArr.length(), 4)) { opsArr.optString(it, "").filter { c -> !c.isISOControl() }.take(100) }
        val revealed = stRaw == "REVEAL" || stRaw == "END"
        val s1 = s.getInt("s1").coerceIn(0, 100_000)
        val s2 = s.getInt("s2").coerceIn(0, 100_000)
        val mine = if (me1) s1 else s2
        val theirs = if (me1) s2 else s1
        fun pickOf(key: String) = if (revealed) s.optInt(key, -1).coerceIn(-1, 3) else -1
        val c1 = pickOf("c1")
        val c2 = pickOf("c2")
        state = state.copy(
            phase = if (ov) GPhase.OVER else GPhase.PLAYING,
            step = if (stRaw == "END") "REVEAL" else stRaw,
            seq = seq,
            index = s.getInt("ix").coerceIn(0, 100),
            total = s.getInt("n").coerceIn(0, 100),
            packTitle = s.optString("pk", "").filter { !it.isISOControl() }.take(40),
            packEmoji = s.optString("em", "🏝️").filter { !it.isISOControl() }.take(8),
            category = s.optString("ct", "").filter { !it.isISOControl() }.take(24),
            question = s.optString("q", "").filter { !it.isISOControl() }.take(220),
            options = options,
            limitSecs = s.getInt("lm").coerceIn(0, 120),
            secsLeft = s.getInt("sl").coerceIn(-1, 120),
            myChoice = if (revealed) (if (me1) c1 else c2) else myPick,
            theyAnswered = s.optBoolean(if (me1) "a2" else "a1", false),
            correct = if (revealed) s.optInt("cr", -1).coerceIn(-1, 3) else -1,
            theirChoice = if (me1) c2 else c1,
            myMs = if (revealed) s.optInt(if (me1) "m1" else "m2", -1).coerceIn(-1, 120_000) else -1,
            theirMs = if (revealed) s.optInt(if (me1) "m2" else "m1", -1).coerceIn(-1, 120_000) else -1,
            myDelta = if (revealed) s.optInt(if (me1) "d1" else "d2", 0).coerceIn(-1000, 1000) else 0,
            theirDelta = if (revealed) s.optInt(if (me1) "d2" else "d1", 0).coerceIn(-1000, 1000) else 0,
            fact = if (revealed) s.optString("ft", "").filter { !it.isISOControl() }.take(260) else "",
            myScore = mine, theirScore = theirs,
            myRight = s.getInt(if (me1) "r1" else "r2").coerceIn(0, 100),
            theirRight = s.getInt(if (me1) "r2" else "r1").coerceIn(0, 100),
            moves = s.optInt("mv", 0).coerceIn(0, 100_000),
            result = if (!ov) 0 else if (mine > theirs) 1 else if (mine < theirs) 2 else 3,
        )
    }

    override fun reset() {
        myPick = -1
        over = false
        state = state.copy(phase = GPhase.LOBBY, step = "", result = 0, myChoice = -1, correct = -1)
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

    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING) return
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
            val key = if (isHost) e.secretCorrect() else keyFromHost()
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
}

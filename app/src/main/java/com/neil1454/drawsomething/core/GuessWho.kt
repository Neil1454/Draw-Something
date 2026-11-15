package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Guess Who? Each player secretly picks one face from the same board. Take turns asking yes/no questions about the
// other player's face ("Do they wear glasses?") and flip down the faces that can't be it. When you think you know,
// make a guess: get it right and you win, get it wrong and you lose. The host's phone answers the questions, so each
// secret stays on its owner's phone until the game ends.
// ====================================================================================

class GwChar(
    val id: Int, val name: String,
    /** 0 black, 1 brown, 2 blonde, 3 red, 4 grey. */
    val hair: Int,
    /** 0 short, 1 long, 2 curly, 3 bald. */
    val style: Int,
    /** 0 brown, 1 blue, 2 green. */
    val eyes: Int,
    val glasses: Boolean, val hat: Boolean, val beard: Boolean, val moustache: Boolean, val earrings: Boolean,
)

class GwQuestion(val text: String, val test: (GwChar) -> Boolean)

object GuessWhoData {
    private fun c(id: Int, name: String, hair: Int, style: Int, eyes: Int, flags: String) =
        GwChar(id, name, hair, style, eyes, flags[0] == '1', flags[1] == '1', flags[2] == '1', flags[3] == '1', flags[4] == '1')

    // flags: glasses hat beard moustache earrings
    val ALL: List<GwChar> = listOf(
        c(0, "Alex", 0, 0, 0, "00100"), c(1, "Bea", 2, 1, 1, "10001"), c(2, "Cal", 1, 0, 2, "01010"), c(3, "Dee", 3, 2, 0, "00001"),
        c(4, "Eli", 4, 3, 1, "10110"), c(5, "Fay", 0, 1, 0, "01000"), c(6, "Gus", 1, 2, 2, "10100"), c(7, "Hal", 2, 0, 1, "00010"),
        c(8, "Ivy", 3, 1, 2, "10001"), c(9, "Jo", 0, 2, 1, "00000"), c(10, "Kit", 1, 1, 0, "11000"), c(11, "Lou", 4, 0, 2, "00100"),
        c(12, "Max", 2, 2, 0, "01100"), c(13, "Nia", 0, 0, 2, "10001"), c(14, "Oz", 3, 0, 1, "00110"), c(15, "Pip", 1, 3, 0, "00000"),
        c(16, "Quinn", 2, 1, 2, "01001"), c(17, "Rae", 4, 1, 1, "10000"), c(18, "Sam", 0, 3, 2, "00100"), c(19, "Tess", 3, 2, 2, "01000"),
        c(20, "Uma", 1, 0, 1, "10001"), c(21, "Vic", 4, 2, 0, "00010"), c(22, "Wes", 2, 3, 0, "10110"), c(23, "Zoe", 0, 1, 1, "00001"),
    )

    val HAIR_NAMES = listOf("black", "brown", "blonde", "red", "grey")
    val EYE_NAMES = listOf("brown", "blue", "green")

    val QUESTIONS: List<GwQuestion> = buildList {
        HAIR_NAMES.forEachIndexed { i, n -> add(GwQuestion("Do they have $n hair?") { it.hair == i && it.style != 3 }) }
        add(GwQuestion("Do they have short hair?") { it.style == 0 })
        add(GwQuestion("Do they have long hair?") { it.style == 1 })
        add(GwQuestion("Do they have curly hair?") { it.style == 2 })
        add(GwQuestion("Are they bald?") { it.style == 3 })
        EYE_NAMES.forEachIndexed { i, n -> add(GwQuestion("Do they have $n eyes?") { it.eyes == i }) }
        add(GwQuestion("Do they wear glasses?") { it.glasses })
        add(GwQuestion("Do they wear a hat?") { it.hat })
        add(GwQuestion("Do they have a beard?") { it.beard })
        add(GwQuestion("Do they have a moustache?") { it.moustache })
        add(GwQuestion("Do they wear earrings?") { it.earrings })
    }

    /** Which board sizes the host can pick. */
    val SIZES = listOf(12, 18, 24)
}

class GwQA(val asker: Int, val q: Int, val yes: Boolean)

data class GuessWhoState(
    val phase: GPhase = GPhase.LOBBY,
    /** PICK (choose your secret face), PLAY or END. */
    val stage: String = "PICK",
    val ids: List<Int> = emptyList(),
    val mySecret: Int = -1,
    val myPicked: Boolean = false,
    val theirPicked: Boolean = false,
    val myTurn: Boolean = false,
    /** Every question so far, with [GwQA.asker] 1 = me and 2 = them. */
    val history: List<GwQA> = emptyList(),
    val autoFlip: Boolean = true,
    val result: Int = 0,
    /** At the end: both secret faces, and who made the last guess and which face. */
    val theirSecret: Int = -1,
    val guessBy: Int = 0,
    val guessId: Int = -1,
    val why: String = "",
    val iAmHost: Boolean = true,
) {
    val phaseOver get() = phase == GPhase.OVER
    val myScore get() = if (result == 1) 1 else 0
    val theirScore get() = if (result == 2) 1 else 0
    val myQuestions get() = history.filter { it.asker == 1 }
}

class GuessWhoEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.GUESSWHO, myName, theirName, isHost, send, random, clock) {

    var state = GuessWhoState(iAmHost = isHost)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    private var localSecret = -1
    private var seenGame = -1

    fun pick(id: Int) {
        if (state.phase != GPhase.PLAYING || state.myPicked || id !in state.ids) return
        localSecret = id
        act(JSONObject().put("a", "pick").put("c", id))
    }
    fun ask(q: Int) = act(JSONObject().put("a", "ask").put("q", q))
    fun guess(id: Int) = act(JSONObject().put("a", "guess").put("c", id))

    // ---- the host's private game
    private var ids = ArrayList<Int>()
    private val secret = IntArray(3) { -1 }
    private var stage = "PICK"
    private var turn = 1
    private val history = ArrayList<GwQA>()
    private var autoFlip = true
    private var over = false
    private var winner = 0
    private var guessBy = 0
    private var guessId = -1
    private var why = ""
    private var gameId = 0

    override fun hostBegin(opts: JSONObject) {
        val n = GuessWhoData.SIZES.getOrElse(opts.optInt("size", 0)) { 12 }
        autoFlip = opts.optBoolean("auto", true)
        ids = ArrayList(GuessWhoData.ALL.map { it.id }.shuffled(random).take(n).sorted())
        secret.fill(-1); history.clear(); stage = "PICK"; over = false; winner = 0; guessBy = 0; guessId = -1; why = ""
        turn = if (random.nextBoolean()) 1 else 2
        localSecret = -1
        gameId++
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over) return
        val p = if (fromHost) 1 else 2
        when (a.getString("a")) {
            "pick" -> if (stage == "PICK" && secret[p] < 0) {
                val c = a.optInt("c", -1)
                if (c !in ids) return
                secret[p] = c
                if (secret[1] >= 0 && secret[2] >= 0) stage = "PLAY"
                publish()
            }
            "ask" -> if (stage == "PLAY" && turn == p) {
                val q = a.optInt("q", -1)
                if (q !in GuessWhoData.QUESTIONS.indices || history.any { it.asker == p && it.q == q }) return
                val target = GuessWhoData.ALL[secret[3 - p]]
                history.add(GwQA(p, q, GuessWhoData.QUESTIONS[q].test(target)))
                turn = 3 - p
                publish()
            }
            "guess" -> if (stage == "PLAY" && turn == p) {
                val c = a.optInt("c", -1)
                if (c !in ids) return
                guessBy = p; guessId = c
                winner = if (c == secret[3 - p]) p else 3 - p
                over = true; stage = "END"; why = if (winner == p) "right" else "wrong"
                publish()
            }
        }
    }

    override fun hostSnapshot(): JSONObject {
        val h = JSONArray()
        for (e in history) h.put(JSONArray().put(e.asker).put(e.q).put(if (e.yes) 1 else 0))
        return JSONObject()
            .put("st", stage).put("ids", JSONArray(ids)).put("pk", JSONArray().put(secret[1] >= 0).put(secret[2] >= 0))
            .put("tn", turn).put("h", h).put("af", autoFlip).put("gid", gameId)
            .put("ov", over).put("wn", winner).put("gb", guessBy).put("gi", guessId).put("why", why)
            .put("sc", if (over) JSONArray().put(secret[1]).put(secret[2]) else JSONArray().put(-1).put(-1))
    }

    override fun adopt(s: JSONObject) {
        val me = if (isHost) 1 else 2
        val ids = ArrayList<Int>()
        s.optJSONArray("ids")?.let { a -> for (i in 0 until minOf(a.length(), 24)) { val v = a.optInt(i, -1); if (v in 0 until GuessWhoData.ALL.size && v !in ids) ids.add(v) } }
        val pk = s.optJSONArray("pk")
        val myPicked = pk?.optBoolean(me - 1, false) ?: false
        val theirPicked = pk?.optBoolean(2 - me, false) ?: false
        val gid = s.optInt("gid", 0)
        if (gid != seenGame) { seenGame = gid; localSecret = -1 }
        val hist = ArrayList<GwQA>()
        s.optJSONArray("h")?.let { a ->
            for (i in 0 until minOf(a.length(), 40)) {
                val e = a.optJSONArray(i) ?: continue
                val q = e.optInt(1, -1)
                if (q !in GuessWhoData.QUESTIONS.indices) continue
                val asker = e.optInt(0, 1).coerceIn(1, 2)
                hist += GwQA(if (asker == me) 1 else 2, q, e.optInt(2, 0) == 1)
            }
        }
        val ov = s.optBoolean("ov", false)
        val wn = s.optInt("wn", 0).coerceIn(0, 2)
        val sc = s.optJSONArray("sc")
        val gb = s.optInt("gb", 0).coerceIn(0, 2)
        state = GuessWhoState(
            phase = if (ov) GPhase.OVER else GPhase.PLAYING, stage = s.optString("st", "PICK").take(5), ids = ids,
            mySecret = if (myPicked) localSecret else -1, myPicked = myPicked, theirPicked = theirPicked,
            myTurn = s.optInt("tn", 1) == me, history = hist, autoFlip = s.optBoolean("af", true),
            result = if (!ov) 0 else if (wn == me) 1 else 2,
            theirSecret = if (ov) (sc?.optInt(2 - me, -1) ?: -1) else -1,
            guessBy = if (gb == 0) 0 else if (gb == me) 1 else 2, guessId = s.optInt("gi", -1),
            why = s.optString("why", "").take(6), iAmHost = isHost,
        ).let { st -> if (ov && myPicked.not() && localSecret < 0) st.copy(mySecret = sc?.optInt(me - 1, -1) ?: -1) else if (ov) st.copy(mySecret = sc?.optInt(me - 1, -1) ?: localSecret) else st }
    }

    override fun reset() {
        state = GuessWhoState(iAmHost = isHost)
        localSecret = -1
        seenGame = -1
    }
}

// ====================================================================== the computer player

class GuessWhoBrain(private val e: GuessWhoEngine, skill: Skill, random: Random) : Brain(skill, random) {
    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING) return
        when (s.stage) {
            "PICK" -> if (!s.myPicked && s.ids.isNotEmpty() && ready(now, "gwpick", 700, 1800)) e.pick(s.ids[random.nextInt(s.ids.size)])
            "PLAY" -> if (s.myTurn && ready(now, "gwturn${s.history.size}", 1200, 2600)) {
                // who could it still be, given the answers I have had?
                var cands = s.ids.map { GuessWhoData.ALL[it] }
                for (qa in s.myQuestions) cands = cands.filter { GuessWhoData.QUESTIONS[qa.q].test(it) == qa.yes }
                val guessAt = when (skill) { Skill.EASY -> 1; Skill.NORMAL -> 1; Skill.HARD -> 2 }
                if (cands.size <= guessAt && cands.isNotEmpty() && !(skill == Skill.EASY && pct(20) && cands.size > 1)) {
                    e.guess(cands[random.nextInt(cands.size)].id)
                    return
                }
                val used = s.myQuestions.map { it.q }.toSet()
                val options = GuessWhoData.QUESTIONS.indices.filter { it !in used }
                if (options.isEmpty() || cands.isEmpty()) { e.guess(s.ids[random.nextInt(s.ids.size)]); return }
                val q = if (skill == Skill.EASY && pct(40)) options[random.nextInt(options.size)]
                else options.maxByOrNull { qi -> val y = cands.count { GuessWhoData.QUESTIONS[qi].test(it) }; minOf(y, cands.size - y) + random.nextFloat() * 0.01f }!!
                e.ask(q)
            }
        }
    }
}

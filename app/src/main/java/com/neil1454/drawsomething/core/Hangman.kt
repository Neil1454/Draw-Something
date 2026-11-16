package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

// ====================================================================================
// Hangman, for two. Two ways to play:
//   • Duel: take turns being the setter. The setter secretly types a word (and, if they like, a one-line hint) and the other player
//     guesses letters. Eight wrong guesses and the guesser is hanged. A solved word scores more the fewer mistakes were made;
//     a word that stumps the guesser scores for the setter.
//   • Shared: the app picks a random word and you take turns guessing letters on the same board. A right letter lets you guess
//     again, a wrong one costs you both a life and passes the turn. Whoever finishes the word scores; eight mistakes and
//     nobody does.
// The secret word never leaves the host's phone (or the setter's) until the round is over.
// ====================================================================================

object HangmanRules {
    const val MAX_MISSES = 8
    const val MIN_LEN = 3
    const val MAX_LEN = 14
    val ROUNDS = listOf(2, 4, 6)

    /** Points for solving a word with [misses] mistakes. */
    fun solvedPoints(misses: Int) = maxOf(20, 100 - 10 * misses)

    /** Points the setter earns when the guesser is hanged. */
    const val STUMPED_POINTS = 60

    /** A typed word turned into a clean secret word (letters only, upper case), or null if it isn't usable. */
    fun clean(raw: String): String? {
        val w = raw.uppercase().filter { it in 'A'..'Z' }
        return if (w.length in MIN_LEN..MAX_LEN && w.length == raw.trim().length) w else null
    }

    /** What the guesser sees: the guessed letters, and underscores for the rest. */
    fun mask(word: String, used: Set<Char>) = word.map { if (it in used) it else '_' }.joinToString("")

    fun solved(word: String, used: Set<Char>) = word.all { it in used }

    /** Words that could still fit what is showing (same length, matching letters, none of the wrong ones). */
    fun candidates(mask: String, used: Set<Char>, dict: List<String> = HangmanWords.ALL): List<String> {
        val wrong = used.filter { c -> c !in mask }.toSet()
        return dict.filter { w ->
            w.length == mask.length && w.indices.all { i -> if (mask[i] != '_') w[i] == mask[i] else w[i] !in used } && w.none { it in wrong }
        }
    }
}

object HangmanWords {
    /** Common words for the computer's own choices and for the shared game. */
    val ALL: List<String> = (
        "ABSENT ACTION ADVENTURE AIRPORT ANCHOR ANIMAL APPLE ARCTIC ARMCHAIR ARTIST AUTUMN BACON BADGER BALLOON BANANA BANKER BARREL BASKET BEACH BEDROOM " +
        "BICYCLE BISCUIT BLANKET BLOSSOM BOTTLE BRIDGE BROTHER BUBBLE BUCKET BUTTER BUTTON CABBAGE CACTUS CAMERA CANDLE CANYON CARPET CARROT CASTLE CATTLE " +
        "CAVERN CEREAL CHAIR CHEESE CHERRY CHIMNEY CINEMA CIRCUS CLIMATE CLOCK CLOUD COCONUT COFFEE COMPASS COOKIE COTTAGE COUNTRY COUSIN CRAYON CRICKET " +
        "CROCODILE CURTAIN DANCER DESERT DIAMOND DINOSAUR DOCTOR DOLPHIN DONKEY DRAGON DRAWER DUCKLING EAGLE ECLIPSE ELEPHANT ENGINE ENVELOPE EXPLORER " +
        "FACTORY FALCON FARMER FEATHER FESTIVAL FIDDLE FIREWORK FISHING FLAMINGO FLOWER FOREST FOUNTAIN FREEZER FRIDAY FRIEND FROZEN GALAXY GARDEN GARLIC " +
        "GIRAFFE GLACIER GLOBE GOBLIN GOLDFISH GRAPE GRAVITY GUITAR HAMMER HAMSTER HARBOUR HEDGEHOG HELMET HOLIDAY HONEY HORIZON HOSPITAL HOTEL HUNTER " +
        "ISLAND JACKET JELLY JEWEL JIGSAW JOURNEY JUNGLE KANGAROO KETCHUP KITCHEN KITTEN KNIGHT LADDER LANTERN LEMON LETTER LIBRARY LIGHTHOUSE LIZARD " +
        "LOBSTER MACHINE MAGNET MARKET MEADOW MELON MIRROR MONKEY MONSTER MOUNTAIN MUSEUM MUSHROOM NAPKIN NEPHEW NEEDLE NOODLE NOTEBOOK OCEAN ORANGE ORCHARD " +
        "OSTRICH OXYGEN PADDLE PAINTER PALACE PANCAKE PARADE PARROT PASSPORT PEBBLE PENCIL PENGUIN PEPPER PICNIC PILLOW PIRATE PLANET PLASTER POCKET POLICE " +
        "POTATO PRINCESS PUDDING PUMPKIN PUPPET PUZZLE PYRAMID QUARTER RABBIT RACKET RAINBOW RAISIN RESCUE RIBBON RIVER ROCKET SADDLE SAILOR SALAD SANDWICH " +
        "SATURN SAUSAGE SCARF SCHOOL SCISSORS SEAGULL SHADOW SHELL SHOVEL SILVER SKELETON SLIPPER SMOKE SNOWMAN SPIDER SPINACH SPONGE SPRING SQUIRREL STADIUM " +
        "STATION STORM STREAM SUMMER SUNRISE SUNSET SWEATER TABLET TEACHER TELESCOPE TENNIS THEATRE THUNDER TICKET TIGER TOASTER TOMATO TORNADO TOWEL TRACTOR " +
        "TRAFFIC TRAVEL TREASURE TRUMPET TUNNEL TURKEY TURTLE UMBRELLA UNICORN VALLEY VELVET VILLAGE VIOLIN VOLCANO WAFFLE WALLET WALRUS WEATHER WINDOW " +
        "WINTER WIZARD WONDER YOGURT ZEBRA ZIPPER ACORN BREEZE CHALK DAISY EMBER FLINT GRAVEL HAZEL IVORY JOLLY KOALA LLAMA MANGO NUTMEG OLIVE PEACH QUILT " +
        "ROBIN SALMON TULIP URCHIN VIOLET WHISKER YELLOW ZEPPELIN"
        ).split(' ').filter { it.length in 4..12 }.distinct()
}

data class HangmanState(
    val phase: GPhase = GPhase.LOBBY,
    /** 0 you set words in turn (Duel), 1 shared random words. */
    val mode: Int = 0,
    val round: Int = 1,
    val rounds: Int = 2,
    /** SET (the setter is choosing a word), GUESS, REVEAL (the word is shown) or END. */
    val stage: String = "SET",
    val iAmSetter: Boolean = false,
    /** Whose go it is to guess (always me or them). In Duel only the guesser ever has the turn. */
    val myTurn: Boolean = false,
    /** What the guesser sees, e.g. "_A_N_". Blank while the word is being set. */
    val mask: String = "",
    val hint: String = "",
    val used: String = "",
    val misses: Int = 0,
    /** My own secret word, if I am the setter (never sent to the other phone). */
    val myWord: String = "",
    /** The real word, once the round is over. */
    val word: String = "",
    /** How the round ended, once it has: 0 not yet, 1 solved, 2 hanged. And who scored (1 me, 2 them, 0 nobody). */
    val roundOutcome: Int = 0,
    val roundScorer: Int = 0,
    val roundPoints: Int = 0,
    val lastLetter: Char = ' ',
    val lastHit: Boolean = false,
    val moves: Int = 0,
    val myScore: Int = 0,
    val theirScore: Int = 0,
    val note: String = "",
    val result: Int = 0,
    val iAmHost: Boolean = true,
) {
    val shared get() = mode == 1
    val iAmGuesser get() = if (shared) true else !iAmSetter
}

class HangmanEngine(
    myName: String,
    theirName: String,
    isHost: Boolean,
    send: (GMsg) -> Unit,
    random: Random = Random.Default,
    clock: () -> Long = { System.currentTimeMillis() },
) : SyncEngine(GameKind.HANGMAN, myName, theirName, isHost, send, random, clock) {

    var state = HangmanState(iAmHost = isHost)
        private set
    override val stateAny: Any get() = state
    override val phase get() = state.phase

    private var localWord = ""
    private var seenGame = -1

    /** The setter picks the secret word (and an optional short hint). */
    fun setWord(word: String, hint: String = "") {
        val w = HangmanRules.clean(word) ?: return
        localWord = w
        act(JSONObject().put("a", "set").put("w", w).put("h", hint.trim().take(30)))
    }

    fun guessLetter(c: Char) = act(JSONObject().put("a", "g").put("c", c.toString()))
    fun guessWord(w: String) = act(JSONObject().put("a", "gw").put("w", w))

    // ---- the host's private game
    private var mode = 0
    private var rounds = 2
    private var round = 1
    private var stage = "SET"
    private var setter = 1
    private var guesser = 2
    private var turn = 2
    private var word = ""
    private var hint = ""
    private val used = LinkedHashSet<Char>()
    private var misses = 0
    private val scores = IntArray(3)
    private var roundOutcome = 0
    private var roundScorer = 0
    private var roundPoints = 0
    private var lastLetter = ' '
    private var lastHit = false
    private var moves = 0
    private var nextAt = 0L
    private var over = false
    private var result = 0
    private var gameId = 0
    private var note = ""

    override fun hostBegin(opts: JSONObject) {
        mode = opts.optInt("mode", 0).coerceIn(0, 1)
        rounds = HangmanRules.ROUNDS.getOrElse(opts.optInt("rounds", 0)) { 2 }
        round = 1; scores.fill(0); over = false; result = 0; gameId++; localWord = ""
        beginRound(first = true)
    }

    private fun beginRound(first: Boolean) {
        used.clear(); misses = 0; roundOutcome = 0; roundScorer = 0; roundPoints = 0; lastLetter = ' '; lastHit = false; moves = 0; nextAt = 0L; note = ""
        hint = ""; word = ""
        if (mode == 0) {
            setter = if (round % 2 == 1) 1 else 2
            guesser = 3 - setter; turn = guesser; stage = "SET"
        } else {
            setter = 0; guesser = 0
            word = HangmanWords.ALL[random.nextInt(HangmanWords.ALL.size)]
            turn = if ((round + gameId) % 2 == 0) 1 else 2
            stage = "GUESS"
        }
    }

    override fun hostHandle(fromHost: Boolean, a: JSONObject) {
        if (state.phase != GPhase.PLAYING || over) return
        val p = if (fromHost) 1 else 2
        when (a.getString("a")) {
            "set" -> if (stage == "SET" && mode == 0 && p == setter) {
                val w = HangmanRules.clean(a.optString("w", "")) ?: return
                word = w; hint = StakeWire.clean(a.optString("h", "")).take(30); stage = "GUESS"; publish()
            }
            "g" -> if (stage == "GUESS" && turn == p) {
                val c = a.optString("c", "").uppercase().firstOrNull() ?: return
                if (c !in 'A'..'Z' || c in used) return
                used.add(c); moves++; lastLetter = c; lastHit = c in word
                if (!lastHit) misses++
                afterGuess(p)
                publish()
            }
            "gw" -> if (stage == "GUESS" && turn == p) {
                val w = a.optString("w", "").uppercase().filter { it in 'A'..'Z' }
                if (w.length !in HangmanRules.MIN_LEN..HangmanRules.MAX_LEN) return
                moves++
                if (w == word) { word.forEach { used.add(it) }; lastLetter = ' '; lastHit = true }
                else { misses = minOf(HangmanRules.MAX_MISSES, misses + 2); lastLetter = ' '; lastHit = false }
                afterGuess(p)
                publish()
            }
        }
    }

    private fun afterGuess(p: Int) {
        if (HangmanRules.solved(word, used)) {
            roundOutcome = 1
            roundPoints = HangmanRules.solvedPoints(misses)
            roundScorer = if (mode == 0) guesser else p
            scores[roundScorer] += roundPoints
            endRound()
        } else if (misses >= HangmanRules.MAX_MISSES) {
            roundOutcome = 2
            if (mode == 0) { roundScorer = setter; roundPoints = HangmanRules.STUMPED_POINTS; scores[setter] += roundPoints } else { roundScorer = 0; roundPoints = 0 }
            endRound()
        } else if (!lastHit && mode == 1) turn = 3 - turn
    }

    private fun endRound() {
        stage = "REVEAL"
        if (round >= rounds) {
            over = true; stage = "END"
            result = if (scores[1] > scores[2]) 1 else if (scores[2] > scores[1]) 2 else 3
        } else nextAt = clock() + 4200L
    }

    override fun hostTick(now: Long) {
        if (!over && stage == "REVEAL" && nextAt > 0L && now >= nextAt) {
            round++
            beginRound(first = false)
            publish()
        }
    }

    override fun hostSnapshot(): JSONObject {
        val reveal = stage == "REVEAL" || stage == "END"
        return JSONObject()
            .put("md", mode).put("rd", round).put("rt", rounds).put("st", stage).put("sp", setter).put("tn", turn)
            .put("mk", if (word.isEmpty()) "" else HangmanRules.mask(word, used)).put("ln", word.length)
            .put("hn", hint).put("us", used.joinToString("")).put("ms", misses)
            .put("wd", if (reveal) word else "")
            .put("ro", roundOutcome).put("rs", roundScorer).put("rp", roundPoints)
            .put("ll", lastLetter.toString()).put("lh", lastHit).put("mv", moves)
            .put("s1", scores[1]).put("s2", scores[2]).put("gid", gameId).put("ov", over).put("res", result)
    }

    override fun adopt(s: JSONObject) {
        val me = if (isHost) 1 else 2
        val gid = s.optInt("gid", 0)
        if (gid != seenGame) { seenGame = gid; localWord = "" }
        val md = s.optInt("md", 0).coerceIn(0, 1)
        val rd = s.optInt("rd", 1).coerceIn(1, 8)
        val ov = s.optBoolean("ov", false)
        val stage = s.optString("st", "SET").take(6)
        val sp = s.optInt("sp", 0).coerceIn(0, 2)
        val mask = s.optString("mk", "").uppercase().filter { it in 'A'..'Z' || it == '_' }.take(HangmanRules.MAX_LEN)
        val used = s.optString("us", "").uppercase().filter { it in 'A'..'Z' }.take(26)
        val scorer = s.optInt("rs", 0).coerceIn(0, 2)
        fun who(v: Int) = when (v) { 0 -> 0; me -> 1; else -> 2 }
        // my own secret word is only kept for as long as it belongs to this round
        val myWord = if (md == 0 && sp == me && stage == "GUESS") localWord else ""
        val res = s.optInt("res", 0).coerceIn(0, 3)
        val ll = s.optString("ll", " ").firstOrNull() ?: ' '
        state = HangmanState(
            phase = if (ov) GPhase.OVER else GPhase.PLAYING, mode = md, round = rd, rounds = s.optInt("rt", 2).coerceIn(1, 8), stage = stage,
            iAmSetter = md == 0 && sp == me, myTurn = !ov && stage == "GUESS" && s.optInt("tn", 1) == me,
            mask = mask, hint = StakeWire.clean(s.optString("hn", "")).take(30), used = used, misses = s.optInt("ms", 0).coerceIn(0, HangmanRules.MAX_MISSES),
            myWord = myWord, word = s.optString("wd", "").uppercase().filter { it in 'A'..'Z' }.take(HangmanRules.MAX_LEN),
            roundOutcome = s.optInt("ro", 0).coerceIn(0, 2), roundScorer = who(scorer), roundPoints = s.optInt("rp", 0).coerceIn(0, 200),
            lastLetter = if (ll in 'A'..'Z') ll else ' ', lastHit = s.optBoolean("lh", false), moves = s.optInt("mv", 0).coerceIn(0, 100),
            myScore = (if (isHost) s.optInt("s1", 0) else s.optInt("s2", 0)).coerceIn(0, 2000),
            theirScore = (if (isHost) s.optInt("s2", 0) else s.optInt("s1", 0)).coerceIn(0, 2000),
            result = if (!ov) 0 else if (res == 3) 3 else if (res == me) 1 else 2, iAmHost = isHost,
        )
    }

    override fun reset() {
        state = HangmanState(iAmHost = isHost)
        localWord = ""; seenGame = -1
    }
}

// ====================================================================== the computer player

class HangmanBrain(private val e: HangmanEngine, skill: Skill, random: Random) : Brain(skill, random) {
    private val order = "ETAOINSHRDLUCMFWYPGBVKJXQZ"

    override fun think(now: Long) {
        val s = e.state
        if (s.phase != GPhase.PLAYING) return
        if (s.stage == "SET" && s.iAmSetter) {
            if (ready(now, "hmset${s.round}", 900, 2200)) e.setWord(HangmanWords.ALL[random.nextInt(HangmanWords.ALL.size)])
            return
        }
        if (s.stage != "GUESS" || !s.myTurn) return
        if (!ready(now, "hmg${s.round}-${s.moves}", 900, 2000)) return
        val used = s.used.toSet()
        val cands = HangmanRules.candidates(s.mask, used)
        // one word fits: say it (the careful player is surer than the careless one)
        if (cands.size == 1 && (skill != Skill.EASY || pct(50)) && s.misses < HangmanRules.MAX_MISSES - 1) { e.guessWord(cands[0]); return }
        val letter = when {
            skill == Skill.EASY && pct(45) -> order.filter { it !in used }.let { it[random.nextInt(minOf(it.length, 14))] }
            cands.isNotEmpty() && (skill == Skill.HARD || pct(75)) -> {
                val counts = HashMap<Char, Int>()
                for (w in cands) for (c in w.toSet()) if (c !in used) counts[c] = (counts[c] ?: 0) + 1
                counts.maxByOrNull { it.value * 100 + random.nextInt(50) }?.key
            }
            else -> null
        } ?: order.firstOrNull { it !in used } ?: return
        e.guessLetter(letter)
    }
}

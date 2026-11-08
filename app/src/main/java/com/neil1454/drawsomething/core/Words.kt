package com.neil1454.drawsomething.core

import kotlin.random.Random

enum class Difficulty(val label: String, val multiplier: Double) {
    EASY("Easy", 1.0),
    MEDIUM("Medium", 1.25),
    HARD("Hard", 1.5),
}

data class Word(val text: String, val category: String, val difficulty: Difficulty)

/** The built-in word list. Words are lowercase letters and spaces only. */
object Words {
    private fun group(d: Difficulty, category: String, vararg words: String) =
        words.map { Word(it, category, d) }

    val all: List<Word> = buildList {
        // ---- Easy ----
        addAll(group(Difficulty.EASY, "Nature", "tree", "sun", "moon", "star", "cloud", "flower", "rainbow", "mountain", "fire", "rain"))
        addAll(group(Difficulty.EASY, "Animal", "cat", "dog", "fish", "bird", "snake", "spider", "duck", "pig", "cow", "butterfly"))
        addAll(group(Difficulty.EASY, "Food", "apple", "banana", "pizza", "egg", "cake", "ice cream", "carrot", "cookie", "burger"))
        addAll(group(Difficulty.EASY, "Object", "house", "car", "key", "book", "clock", "hat", "ball", "umbrella", "chair", "bed", "door", "cup", "shoe", "balloon", "boat"))
        // ---- Medium ----
        addAll(group(Difficulty.MEDIUM, "Animal", "elephant", "giraffe", "penguin", "owl", "turtle", "dolphin", "rabbit", "horse", "frog", "crab", "bee", "shark"))
        addAll(group(Difficulty.MEDIUM, "Food", "sandwich", "popcorn", "watermelon", "pineapple", "cupcake", "spaghetti", "pancake"))
        addAll(group(Difficulty.MEDIUM, "Object", "bicycle", "guitar", "camera", "telephone", "scissors", "candle", "lamp", "glasses", "backpack", "kite", "ladder", "robot", "anchor", "crown"))
        addAll(group(Difficulty.MEDIUM, "Vehicle", "airplane", "train", "tractor", "rocket", "bus", "motorbike"))
        addAll(group(Difficulty.MEDIUM, "Place", "castle", "bridge", "lighthouse", "beach", "island", "igloo", "volcano", "farm"))
        // ---- Hard ----
        addAll(group(Difficulty.HARD, "Object", "skateboard", "telescope", "parachute", "microscope", "hourglass", "chandelier", "wheelbarrow", "snow globe", "scarecrow", "fireworks", "campfire"))
        addAll(group(Difficulty.HARD, "Animal", "octopus", "kangaroo", "chameleon", "jellyfish", "peacock", "hedgehog"))
        addAll(group(Difficulty.HARD, "Place", "pyramid", "waterfall", "windmill", "treehouse", "roller coaster"))
        addAll(group(Difficulty.HARD, "Vehicle", "submarine", "helicopter", "pirate ship", "hot air balloon"))
        addAll(group(Difficulty.HARD, "Character", "wizard", "dragon", "mermaid", "astronaut", "pirate", "ghost"))
        addAll(group(Difficulty.HARD, "Nature", "cactus", "sunflower"))
    }

    /**
     * Five choices for the drawer: two easy, two medium, one hard (shuffled).
     * Words in [exclude] (already used this game) are avoided while enough others remain.
     */
    fun pickOptions(random: Random, exclude: Set<String>): List<Word> {
        fun pool(d: Difficulty) = all.filter { it.difficulty == d && it.text !in exclude }
        val picked = mutableListOf<Word>()
        picked += pool(Difficulty.EASY).shuffled(random).take(2)
        picked += pool(Difficulty.MEDIUM).shuffled(random).take(2)
        picked += pool(Difficulty.HARD).shuffled(random).take(1)
        if (picked.size < 5) {
            // Ran low on unused words: top up from anything not already offered.
            picked += all.filter { w -> picked.none { it.text == w.text } }.shuffled(random).take(5 - picked.size)
        }
        return picked.shuffled(random)
    }

    /** "Surprise me": the game decides. Weighted 40% easy / 40% medium / 20% hard. */
    fun random(random: Random, exclude: Set<String>): Word {
        val roll = random.nextInt(100)
        val d = when {
            roll < 40 -> Difficulty.EASY
            roll < 80 -> Difficulty.MEDIUM
            else -> Difficulty.HARD
        }
        val pool = all.filter { it.difficulty == d && it.text !in exclude }
            .ifEmpty { all.filter { it.text !in exclude } }
            .ifEmpty { all }
        return pool.random(random)
    }

    /** "_ _ _" style mask shown to the guesser (letters hidden, spaces kept). */
    fun pattern(word: String): String = word.map { if (it == ' ') ' ' else '_' }.joinToString("")
}

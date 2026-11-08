package com.neil1454.drawsomething.core

import kotlin.random.Random

enum class Difficulty(val label: String, val multiplier: Double) {
    EASY("Easy", 1.0),
    MEDIUM("Medium", 1.25),
    HARD("Hard", 1.5),
    EXTREME("Extra hard", 2.0),
}

data class Word(val text: String, val category: String, val difficulty: Difficulty)

/** The built-in word list. Words are lowercase letters and spaces only. */
object Words {
    private fun group(d: Difficulty, category: String, vararg words: String) =
        words.map { Word(it, category, d) }

    val all: List<Word> = buildList {
        // ---------------------------------------------------------------- Easy
        addAll(group(Difficulty.EASY, "Nature", "tree", "sun", "moon", "star", "cloud", "flower", "rainbow", "mountain", "fire", "rain", "leaf", "snowflake", "wave", "grass", "lightning"))
        addAll(group(Difficulty.EASY, "Animal", "cat", "dog", "fish", "bird", "snake", "spider", "duck", "pig", "cow", "butterfly", "mouse", "chicken", "sheep", "ant", "worm", "snail"))
        addAll(group(Difficulty.EASY, "Food", "apple", "banana", "pizza", "egg", "cake", "ice cream", "carrot", "cookie", "burger", "strawberry", "cherry", "grapes", "lemon", "bread", "cheese", "lollipop", "donut", "corn"))
        addAll(group(Difficulty.EASY, "Object", "house", "car", "key", "book", "clock", "hat", "ball", "umbrella", "chair", "bed", "door", "cup", "shoe", "balloon", "boat", "table", "bottle", "bell", "heart", "flag", "sock", "spoon", "fork", "ring", "envelope", "gift", "bucket", "broom", "comb", "toothbrush"))

        // -------------------------------------------------------------- Medium
        addAll(group(Difficulty.MEDIUM, "Animal", "elephant", "giraffe", "penguin", "owl", "turtle", "dolphin", "rabbit", "horse", "frog", "crab", "bee", "shark", "monkey", "lion", "tiger", "bear", "zebra", "squirrel", "bat", "whale", "camel", "parrot", "swan", "deer", "fox", "seal", "goat", "crocodile"))
        addAll(group(Difficulty.MEDIUM, "Food", "sandwich", "popcorn", "watermelon", "pineapple", "cupcake", "spaghetti", "pancake", "hot dog", "sushi", "mushroom", "pumpkin", "broccoli", "coconut", "pretzel"))
        addAll(group(Difficulty.MEDIUM, "Object", "bicycle", "guitar", "camera", "telephone", "scissors", "candle", "lamp", "glasses", "backpack", "kite", "ladder", "robot", "anchor", "crown", "drum", "piano", "trumpet", "suitcase", "hammer", "paintbrush", "trophy", "magnet", "lantern", "compass", "mirror", "tent", "wheel", "snowman"))
        addAll(group(Difficulty.MEDIUM, "Vehicle", "airplane", "train", "tractor", "rocket", "bus", "motorbike", "ambulance", "fire truck", "taxi", "sailboat", "scooter"))
        addAll(group(Difficulty.MEDIUM, "Place", "castle", "bridge", "lighthouse", "beach", "island", "igloo", "volcano", "farm", "school", "church", "barn", "tower", "desert"))

        // ---------------------------------------------------------------- Hard
        addAll(group(Difficulty.HARD, "Object", "skateboard", "telescope", "parachute", "microscope", "hourglass", "chandelier", "wheelbarrow", "snow globe", "fireworks", "campfire", "binoculars", "typewriter", "accordion", "harp", "saxophone", "trampoline", "vacuum cleaner", "washing machine", "lawn mower", "light bulb", "thermometer", "stethoscope", "fishing rod", "bird cage", "treasure chest", "roller skates", "snorkel", "sleeping bag", "jigsaw puzzle", "calculator", "satellite dish"))
        addAll(group(Difficulty.HARD, "Animal", "octopus", "kangaroo", "chameleon", "jellyfish", "peacock", "hedgehog", "flamingo", "porcupine", "seahorse", "armadillo", "sloth", "rhinoceros", "hippopotamus", "walrus", "starfish", "scorpion", "woodpecker", "platypus"))
        addAll(group(Difficulty.HARD, "Place", "pyramid", "waterfall", "windmill", "treehouse", "roller coaster", "skyscraper", "drawbridge", "aquarium", "observatory", "ferris wheel"))
        addAll(group(Difficulty.HARD, "Vehicle", "submarine", "helicopter", "pirate ship", "hot air balloon", "cable car", "hovercraft", "steam train", "jet ski"))
        addAll(group(Difficulty.HARD, "Character", "wizard", "dragon", "mermaid", "astronaut", "pirate", "ghost", "vampire", "knight", "ninja", "unicorn", "cowboy", "genie", "fairy", "superhero", "scarecrow"))
        addAll(group(Difficulty.HARD, "Nature", "cactus", "sunflower", "tornado", "iceberg", "coral reef", "avalanche"))

        // ----------------------------------------------------------- Extra hard
        addAll(group(Difficulty.EXTREME, "Object", "spiral staircase", "suspension bridge", "grandfather clock", "hot water bottle", "kaleidoscope", "periscope", "gramophone", "sundial", "weather vane", "lava lamp", "totem pole", "bagpipes", "pogo stick", "slingshot", "cuckoo clock", "jack in the box", "rocking horse", "carousel", "wishing well", "treasure map", "wind turbine", "solar panel", "spider web", "paper airplane"))
        addAll(group(Difficulty.EXTREME, "Action", "tightrope walker", "ice skater", "bungee jumping", "scuba diver", "snowboarder", "mountain climber", "hide and seek", "tug of war", "snow angel", "bubble bath", "sleepwalking", "rollerblading", "magic trick", "fishing trip"))
        addAll(group(Difficulty.EXTREME, "Scene", "space station", "black hole", "solar eclipse", "shooting star", "sandcastle", "message in a bottle", "traffic jam", "birthday party", "picnic", "haunted house", "shipwreck", "desert island", "bowling alley"))
        addAll(group(Difficulty.EXTREME, "Concept", "gravity", "electricity", "daydream", "nightmare", "teamwork", "jet lag", "stage fright", "brain freeze", "goosebumps", "hiccups", "sneeze", "echo"))
    }

    private fun atLeast(min: Difficulty) = all.filter { it.difficulty.ordinal >= min.ordinal }

    /**
     * Five choices for the drawer, always with a spread of difficulty. [min] is the lowest level
     * the host allowed, so choosing "Hard" removes every easy and medium word. Words in
     * [exclude] (already used this game) are avoided while enough others remain.
     */
    fun pickOptions(random: Random, exclude: Set<String>, min: Difficulty = Difficulty.EASY): List<Word> {
        val e = Difficulty.EASY
        val m = Difficulty.MEDIUM
        val h = Difficulty.HARD
        val x = Difficulty.EXTREME
        val slots = when (min) {
            Difficulty.EASY -> listOf(e, e, m, m, if (random.nextInt(100) < 30) x else h)
            Difficulty.MEDIUM -> listOf(m, m, h, h, x)
            Difficulty.HARD -> listOf(h, h, h, x, x)
            Difficulty.EXTREME -> listOf(x, x, x, x, x)
        }
        val picked = mutableListOf<Word>()
        fun unused(w: Word) = w.text !in exclude && picked.none { it.text == w.text }
        for (d in slots) {
            val w = all.filter { it.difficulty == d && unused(it) }.randomOrNull(random)
                ?: atLeast(min).filter { unused(it) }.randomOrNull(random)
                ?: all.filter { w -> picked.none { it.text == w.text } }.randomOrNull(random)
            if (w != null) picked += w
        }
        return picked.shuffled(random)
    }

    /** "Surprise me": the game decides, within the allowed difficulty. */
    fun random(random: Random, exclude: Set<String>, min: Difficulty = Difficulty.EASY): Word {
        val roll = random.nextInt(100)
        val d = when (min) {
            Difficulty.EASY -> when {
                roll < 35 -> Difficulty.EASY
                roll < 70 -> Difficulty.MEDIUM
                roll < 90 -> Difficulty.HARD
                else -> Difficulty.EXTREME
            }
            Difficulty.MEDIUM -> when {
                roll < 45 -> Difficulty.MEDIUM
                roll < 80 -> Difficulty.HARD
                else -> Difficulty.EXTREME
            }
            Difficulty.HARD -> if (roll < 60) Difficulty.HARD else Difficulty.EXTREME
            Difficulty.EXTREME -> Difficulty.EXTREME
        }
        val pool = all.filter { it.difficulty == d && it.text !in exclude }
            .ifEmpty { atLeast(min).filter { it.text !in exclude } }
            .ifEmpty { atLeast(min) }
        return pool.random(random)
    }

    /** "_ _ _" style mask shown to the guesser (letters hidden, spaces kept). */
    fun pattern(word: String): String = word.map { if (it == ' ') ' ' else '_' }.joinToString("")
}

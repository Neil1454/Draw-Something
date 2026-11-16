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

/** How a person's face is drawn: colours and styles, all picked so that every face is easy to tell apart. */
class GwLook(
    /** 0 black, 1 brown, 2 blonde, 3 red, 4 grey. */
    val hair: Int,
    /** 0 short, 1 long, 2 curly, 3 bald, 4 tied up. */
    val style: Int,
    /** 0 brown, 1 blue, 2 green. */
    val eyes: Int,
    val skin: Int, val shirt: Int, val hatColour: Int,
    val flags: String,
) {
    private fun f(i: Int) = flags.getOrElse(i) { '0' } == '1'
    val glasses get() = f(0)
    val hat get() = f(1)
    val beard get() = f(2)
    val moustache get() = f(3)
    val bigNose get() = f(4)
    val rosy get() = f(5)
    val freckles get() = f(6)
    val brows get() = f(7)
    val smile get() = f(8)
    val earrings get() = f(9)
    val bowtie get() = f(10)
    val scarf get() = f(11)
}

/**
 * One card on the board: a person (drawn), an animal or an item (shown as a picture). [tags] say what is true about it,
 * and every question is just "does it have this tag?", so the answers can always be checked.
 */
class GwChar(val id: Int, val name: String, /** 0 person, 1 animal, 2 item. */ val kind: Int, val tags: Set<String>, val emoji: String = "", val look: GwLook? = null)

class GwQuestion(val key: String, val text: String, val emoji: String, val short: String, val group: String) {
    fun test(c: GwChar) = key in c.tags
}

/** The sets of cards the host can play with. */
enum class GwDeck(val label: String, val emoji: String) {
    PEOPLE("People", "🧑"), ANIMALS("Animals", "🐾"), WILD("Wild animals", "🦁"), FARM("Farm and pets", "🐄"),
    SEA("Birds and sea life", "🐧"), MIX("People and animals", "🔀"), ITEMS("Everyday items", "🎒"), EVERYTHING("Everything", "🌍"),
}

object GuessWhoData {
    private val HAIR = listOf("black", "brown", "blonde", "red", "grey")
    private val EYES = listOf("brown", "blue", "green")
    private val STYLE = listOf("short", "long", "curly", "bald", "tied")
    private val FLAGS = listOf("glasses", "hat", "beard", "moustache", "bignose", "rosy", "freckles", "brows", "smile", "earrings", "bowtie", "scarf")

    private fun person(id: Int, name: String, hair: Int, style: Int, eyes: Int, skin: Int, shirt: Int, hatColour: Int, flags: String): GwChar {
        val t = HashSet<String>()
        if (style != 3) t.add("h_${HAIR[hair]}")
        t.add("s_${STYLE[style]}"); t.add("e_${EYES[eyes]}")
        FLAGS.forEachIndexed { i, n -> if (flags[i] == '1') t.add(n) }
        return GwChar(id, name, 0, t, look = GwLook(hair, style, eyes, skin, shirt, hatColour, flags))
    }

    private fun animal(id: Int, emoji: String, name: String, tags: String) = GwChar(id, name, 1, tags.split(' ').filter { it.isNotEmpty() }.toSet(), emoji)
    private fun item(id: Int, emoji: String, name: String, tags: String) = GwChar(id, name, 2, tags.split(' ').filter { it.isNotEmpty() }.toSet(), emoji)

    private val PEOPLE: List<GwChar> = listOf(
        person(0, "Alex", 4, 1, 1, 4, 3, 3, "001000101000"),
        person(1, "Bea", 2, 1, 1, 0, 5, 0, "000011110000"),
        person(2, "Cal", 0, 0, 1, 0, 0, 0, "000010111001"),
        person(3, "Dee", 3, 1, 0, 4, 2, 0, "010001000100"),
        person(4, "Eli", 1, 2, 2, 3, 3, 0, "000110011001"),
        person(5, "Fay", 0, 2, 1, 1, 2, 2, "000101010100"),
        person(6, "Gus", 4, 1, 0, 1, 3, 1, "000001010001"),
        person(7, "Hal", 4, 3, 1, 2, 1, 1, "101001000001"),
        person(8, "Ivy", 4, 2, 2, 3, 4, 2, "000001001000"),
        person(9, "Jo", 1, 1, 0, 0, 2, 0, "000010000010"),
        person(10, "Kit", 2, 0, 2, 4, 5, 3, "010101100000"),
        person(11, "Lou", 3, 4, 2, 4, 1, 2, "011010000010"),
        person(12, "Max", 4, 0, 1, 4, 0, 2, "000101000011"),
        person(13, "Nia", 3, 1, 2, 1, 2, 2, "000011001000"),
        person(14, "Oz", 1, 4, 0, 0, 0, 1, "011011000001"),
        person(15, "Pip", 2, 2, 1, 1, 1, 1, "100001111000"),
        person(16, "Quinn", 1, 0, 0, 0, 5, 0, "001101011000"),
        person(17, "Rae", 1, 4, 1, 3, 4, 1, "000110100000"),
        person(18, "Sam", 0, 2, 2, 4, 3, 2, "001011100100"),
        person(19, "Tess", 0, 0, 1, 1, 1, 3, "010001000000"),
        person(20, "Uma", 2, 0, 2, 0, 5, 0, "001000001100"),
        person(21, "Vic", 0, 4, 0, 1, 3, 2, "000000011001"),
        person(22, "Wes", 2, 0, 1, 4, 1, 0, "010000100010"),
        person(23, "Zoe", 2, 1, 0, 2, 0, 1, "000100000100"),
        person(24, "Abe", 4, 2, 0, 1, 0, 3, "000010011001"),
        person(25, "Bo", 4, 1, 1, 2, 0, 1, "100011000100"),
        person(26, "Cleo", 2, 4, 2, 1, 0, 3, "000110000010"),
        person(27, "Dan", 3, 4, 0, 1, 4, 1, "001111000010"),
        person(28, "Eve", 1, 0, 0, 0, 3, 1, "110101000100"),
        person(29, "Finn", 3, 0, 2, 3, 5, 0, "000100001110"),
        person(30, "Gia", 3, 1, 2, 3, 3, 2, "011000000111"),
        person(31, "Hugo", 4, 4, 0, 1, 4, 2, "001010011100"),
        person(32, "Isla", 0, 3, 2, 1, 4, 0, "010110001000"),
        person(33, "Jack", 1, 0, 2, 3, 1, 2, "100000011101"),
        person(34, "Kai", 2, 2, 0, 3, 2, 3, "010101001000"),
        person(35, "Lena", 3, 4, 2, 2, 0, 2, "010001010011"),
        person(36, "Milo", 3, 0, 1, 1, 1, 2, "000011011000"),
        person(37, "Nell", 4, 3, 0, 0, 0, 2, "011001010000"),
        person(38, "Otto", 4, 2, 1, 3, 0, 2, "000011100001"),
        person(39, "Pam", 4, 1, 0, 4, 1, 3, "011100110000"),
        person(40, "Remy", 3, 2, 0, 2, 1, 1, "110000000111"),
        person(41, "Sid", 0, 3, 0, 4, 4, 1, "000001001001"),
        person(42, "Tara", 2, 0, 1, 2, 5, 0, "011100001001"),
        person(43, "Ugo", 2, 2, 1, 4, 2, 3, "001010010010"),
        person(44, "Vera", 2, 3, 0, 1, 0, 0, "100010100000"),
        person(45, "Walt", 3, 2, 2, 1, 4, 1, "010000110100"),
        person(46, "Yuki", 2, 2, 0, 3, 3, 1, "100111000100"),
        person(47, "Zane", 0, 0, 0, 2, 1, 3, "100000001100"),
    )

    private val ANIMALS: List<GwChar> = listOf(
        animal(48, "🐶", "Dog", "mammal pet furry legs4 floppyears"),
        animal(49, "🐱", "Cat", "mammal pet furry legs4 pointyears whiskers"),
        animal(50, "🐭", "Mouse", "mammal furry small whiskers wild"),
        animal(51, "🐹", "Hamster", "mammal pet furry small"),
        animal(52, "🐰", "Rabbit", "mammal pet furry small longears"),
        animal(53, "🐮", "Cow", "mammal farm large legs4 horns spots"),
        animal(54, "🐷", "Pig", "mammal farm pink legs4 snout"),
        animal(55, "🐑", "Sheep", "mammal farm furry legs4"),
        animal(56, "🐐", "Goat", "mammal farm legs4 horns"),
        animal(57, "🐴", "Horse", "mammal farm large legs4 mane"),
        animal(58, "🐔", "Chicken", "bird farm feathers beak"),
        animal(59, "🐓", "Rooster", "bird farm feathers beak crest"),
        animal(60, "🦃", "Turkey", "bird farm feathers beak large"),
        animal(61, "🦆", "Duck", "bird farm water feathers beak flies"),
        animal(62, "🐻", "Bear", "mammal wild furry brown large legs4 eatsmeat"),
        animal(63, "🐼", "Panda", "mammal wild furry blackwhite large legs4"),
        animal(64, "🐨", "Koala", "mammal wild furry grey"),
        animal(65, "🐯", "Tiger", "mammal wild furry stripes orange large legs4 eatsmeat teeth"),
        animal(66, "🦁", "Lion", "mammal wild furry mane large legs4 eatsmeat teeth"),
        animal(67, "🐺", "Wolf", "mammal wild furry grey legs4 pointyears eatsmeat teeth"),
        animal(68, "🦊", "Fox", "mammal wild furry orange legs4 pointyears eatsmeat"),
        animal(69, "🐗", "Boar", "mammal wild furry brown legs4 tusks snout"),
        animal(70, "🦓", "Zebra", "mammal wild stripes blackwhite large legs4 mane"),
        animal(71, "🦒", "Giraffe", "mammal wild spots large legs4 horns longneck"),
        animal(72, "🐘", "Elephant", "mammal wild grey large legs4 trunk"),
        animal(73, "🦏", "Rhino", "mammal wild grey large legs4 horns"),
        animal(74, "🐪", "Camel", "mammal wild large legs4 brown hump"),
        animal(75, "🦌", "Deer", "mammal wild brown legs4 horns"),
        animal(76, "🐵", "Monkey", "mammal wild furry brown"),
        animal(77, "🦇", "Bat", "mammal wild flies small wings nocturnal"),
        animal(78, "🐿", "Squirrel", "mammal wild furry small brown"),
        animal(79, "🦔", "Hedgehog", "mammal wild small spikes brown nocturnal"),
        animal(80, "🐧", "Penguin", "bird sea water feathers beak blackwhite"),
        animal(81, "🦅", "Eagle", "bird wild flies feathers beak eatsmeat large wings"),
        animal(82, "🦉", "Owl", "bird wild flies feathers beak nocturnal wings"),
        animal(83, "🐦", "Bird", "bird wild flies feathers beak small wings"),
        animal(84, "🐬", "Dolphin", "mammal sea water grey fins"),
        animal(85, "🐳", "Whale", "mammal sea water large fins"),
        animal(86, "🦈", "Shark", "fish sea water grey large fins teeth eatsmeat"),
        animal(87, "🐟", "Fish", "fish sea water fins"),
        animal(88, "🐠", "Tropical fish", "fish sea water fins colourful"),
        animal(89, "🐡", "Pufferfish", "fish sea water fins spikes"),
        animal(90, "🐙", "Octopus", "sea water tentacles eightarms"),
        animal(91, "🦑", "Squid", "sea water tentacles"),
        animal(92, "🦀", "Crab", "sea water claws shell red"),
        animal(93, "🐢", "Turtle", "reptile water shell green"),
        animal(94, "🐍", "Snake", "reptile scales green nolegs eatsmeat"),
        animal(95, "🐊", "Crocodile", "reptile water scales green large legs4 teeth eatsmeat"),
        animal(96, "🦎", "Lizard", "reptile scales green small legs4"),
        animal(97, "🐸", "Frog", "water green small"),
        animal(98, "🐝", "Bee", "bug flies small wings stripes"),
        animal(99, "🦋", "Butterfly", "bug flies small wings"),
        animal(100, "🐞", "Ladybird", "bug flies small wings spots red"),
        animal(101, "🐌", "Snail", "bug small shell"),
    )

    private val ITEMS: List<GwChar> = listOf(
        item(102, "🍎", "Apple", "food fruit red round handheld"),
        item(103, "🍌", "Banana", "food fruit yellow handheld curved"),
        item(104, "🍊", "Orange", "food fruit orange round handheld"),
        item(105, "🍇", "Grapes", "food fruit purple"),
        item(106, "🍓", "Strawberry", "food fruit red handheld"),
        item(107, "🍋", "Lemon", "food fruit yellow handheld sour"),
        item(108, "🥕", "Carrot", "food veg orange handheld"),
        item(109, "🌽", "Corn", "food veg yellow handheld"),
        item(110, "🍕", "Pizza", "food hot round"),
        item(111, "🍔", "Burger", "food hot handheld"),
        item(112, "🍟", "Chips", "food hot yellow"),
        item(113, "🍩", "Doughnut", "food sweet round handheld"),
        item(114, "🍪", "Biscuit", "food sweet round handheld brown"),
        item(115, "🎂", "Cake", "food sweet round"),
        item(116, "🍦", "Ice cream", "food sweet cold handheld"),
        item(117, "☕", "Coffee", "drink hot brown handheld"),
        item(118, "🥛", "Milk", "drink cold glass handheld"),
        item(119, "🚗", "Car", "vehicle wheels metal engine roads"),
        item(120, "🚲", "Bike", "vehicle wheels metal outdoors"),
        item(121, "✈️", "Plane", "vehicle metal flies engine wings"),
        item(122, "🚂", "Train", "vehicle wheels metal engine rails"),
        item(123, "🚢", "Ship", "vehicle metal waterborne engine"),
        item(124, "🚀", "Rocket", "vehicle metal flies engine space"),
        item(125, "⚽", "Football", "toy round ball outdoors handheld"),
        item(126, "🏀", "Basketball", "toy round ball orange handheld outdoors"),
        item(127, "🎸", "Guitar", "instrument music strings"),
        item(128, "🎹", "Piano", "instrument music keys"),
        item(129, "🥁", "Drum", "instrument music round"),
        item(130, "🎺", "Trumpet", "instrument music metal handheld"),
        item(131, "🔑", "Key", "tool metal handheld small"),
        item(132, "🔨", "Hammer", "tool metal handheld heavy"),
        item(133, "✂️", "Scissors", "tool metal handheld sharp"),
        item(134, "🔦", "Torch", "electric light handheld"),
        item(135, "💡", "Light bulb", "electric light glass handheld round"),
        item(136, "⏰", "Alarm clock", "electric handheld round"),
        item(137, "📷", "Camera", "electric handheld screen photo"),
        item(138, "📱", "Phone", "electric handheld screen"),
        item(139, "💻", "Laptop", "electric screen"),
        item(140, "🎧", "Headphones", "electric wearable music handheld"),
        item(141, "☂️", "Umbrella", "outdoors handheld"),
        item(142, "👓", "Glasses", "wearable glass handheld"),
        item(143, "🎩", "Top hat", "wearable headwear"),
        item(144, "👟", "Trainer", "wearable footwear"),
        item(145, "👑", "Crown", "wearable metal"),
        item(146, "🎁", "Present", "toy handheld"),
        item(147, "🎈", "Balloon", "toy round flies handheld"),
        item(148, "📚", "Books", "paper handheld"),
    )

    /** Every card, in id order (a card's id is its position here). */
    val ALL: List<GwChar> = PEOPLE + ANIMALS + ITEMS

    /** The cards a deck is made from. */
    fun pool(deck: GwDeck): List<GwChar> = when (deck) {
        GwDeck.PEOPLE -> PEOPLE
        GwDeck.ANIMALS -> ANIMALS
        GwDeck.WILD -> ANIMALS.filter { "wild" in it.tags && "bird" !in it.tags }
        GwDeck.FARM -> ANIMALS.filter { "farm" in it.tags || "pet" in it.tags }
        GwDeck.SEA -> ANIMALS.filter { "bird" in it.tags || "sea" in it.tags }
        GwDeck.MIX -> PEOPLE + ANIMALS
        GwDeck.ITEMS -> ITEMS
        GwDeck.EVERYTHING -> ALL
    }

    private fun q(key: String, text: String, emoji: String, short: String, group: String) = GwQuestion(key, text, emoji, short, group)

    val QUESTIONS: List<GwQuestion> = listOf(
        // people
        q("h_black", "Do they have black hair?", "⚫", "Black hair", "🎨 Hair colour"), q("h_brown", "Do they have brown hair?", "🟤", "Brown hair", "🎨 Hair colour"),
        q("h_blonde", "Do they have blonde hair?", "🟡", "Blonde hair", "🎨 Hair colour"), q("h_red", "Do they have red hair?", "🔴", "Red hair", "🎨 Hair colour"),
        q("h_grey", "Do they have grey hair?", "⚪", "Grey hair", "🎨 Hair colour"),
        q("s_short", "Do they have short hair?", "✂️", "Short hair", "💇 Hair style"), q("s_long", "Do they have long hair?", "💁", "Long hair", "💇 Hair style"),
        q("s_curly", "Do they have curly hair?", "🌀", "Curly hair", "💇 Hair style"), q("s_bald", "Are they bald?", "🥚", "Bald", "💇 Hair style"),
        q("s_tied", "Is their hair tied up?", "🎀", "Hair tied up", "💇 Hair style"),
        q("e_brown", "Do they have brown eyes?", "🟤", "Brown eyes", "👀 Eyes"), q("e_blue", "Do they have blue eyes?", "🔵", "Blue eyes", "👀 Eyes"),
        q("e_green", "Do they have green eyes?", "🟢", "Green eyes", "👀 Eyes"),
        q("glasses", "Do they wear glasses?", "👓", "Glasses", "😎 Face"), q("beard", "Do they have a beard?", "🧔", "Beard", "😎 Face"),
        q("moustache", "Do they have a moustache?", "👨", "Moustache", "😎 Face"), q("bignose", "Do they have a big nose?", "👃", "Big nose", "😎 Face"),
        q("rosy", "Do they have rosy cheeks?", "😊", "Rosy cheeks", "😎 Face"), q("freckles", "Do they have freckles?", "✨", "Freckles", "😎 Face"),
        q("brows", "Do they have bushy eyebrows?", "🤨", "Bushy eyebrows", "😎 Face"), q("smile", "Are they smiling with teeth?", "😁", "Big smile", "😎 Face"),
        q("hat", "Do they wear a hat?", "🎩", "Hat", "👔 Clothes"), q("earrings", "Do they wear earrings?", "💎", "Earrings", "👔 Clothes"),
        q("bowtie", "Do they wear a bow tie?", "🎀", "Bow tie", "👔 Clothes"), q("scarf", "Do they wear a scarf?", "🧣", "Scarf", "👔 Clothes"),
        // animals
        q("mammal", "Is it a mammal?", "🐾", "Mammal", "🐾 What it is"), q("bird", "Is it a bird?", "🐦", "Bird", "🐾 What it is"),
        q("fish", "Is it a fish?", "🐟", "Fish", "🐾 What it is"), q("reptile", "Is it a reptile?", "🦎", "Reptile", "🐾 What it is"),
        q("bug", "Is it an insect or bug?", "🐛", "Bug", "🐾 What it is"),
        q("pet", "Is it a common pet?", "🏠", "Pet", "🏡 Where it lives"), q("farm", "Would you find it on a farm?", "🚜", "Farm animal", "🏡 Where it lives"),
        q("wild", "Is it a wild animal?", "🌳", "Wild", "🏡 Where it lives"), q("sea", "Does it live in the sea?", "🌊", "Lives in the sea", "🏡 Where it lives"),
        q("water", "Does it spend time in water?", "💦", "Likes water", "🏡 Where it lives"), q("flies", "Can it fly?", "🪽", "Can fly", "🏃 What it does"),
        q("eatsmeat", "Does it eat meat?", "🥩", "Eats meat", "🏃 What it does"), q("nocturnal", "Does it come out at night?", "🌙", "Nocturnal", "🏃 What it does"),
        q("furry", "Is it furry?", "🧶", "Furry", "🧬 How it looks"), q("feathers", "Does it have feathers?", "🪶", "Feathers", "🧬 How it looks"),
        q("scales", "Does it have scales?", "🐉", "Scales", "🧬 How it looks"), q("horns", "Does it have horns or antlers?", "🦌", "Horns", "🧬 How it looks"),
        q("longears", "Does it have long ears?", "🐇", "Long ears", "🧬 How it looks"), q("floppyears", "Does it have floppy ears?", "🐕", "Floppy ears", "🧬 How it looks"),
        q("pointyears", "Does it have pointy ears?", "🔺", "Pointy ears", "🧬 How it looks"), q("trunk", "Does it have a trunk?", "🐘", "Trunk", "🧬 How it looks"),
        q("stripes", "Does it have stripes?", "🦓", "Stripes", "🧬 How it looks"), q("spots", "Does it have spots?", "🐆", "Spots", "🧬 How it looks"),
        q("beak", "Does it have a beak?", "🪺", "Beak", "🧬 How it looks"), q("mane", "Does it have a mane?", "🦁", "Mane", "🧬 How it looks"),
        q("shell", "Does it have a shell?", "🐚", "Shell", "🧬 How it looks"), q("fins", "Does it have fins?", "🐠", "Fins", "🧬 How it looks"),
        q("wings", "Does it have wings?", "🪽", "Wings", "🧬 How it looks"), q("teeth", "Does it have big sharp teeth?", "🦷", "Sharp teeth", "🧬 How it looks"),
        q("tentacles", "Does it have tentacles?", "🦑", "Tentacles", "🧬 How it looks"), q("spikes", "Does it have spikes?", "🌵", "Spikes", "🧬 How it looks"),
        q("legs4", "Does it walk on four legs?", "🐾", "Four legs", "🧬 How it looks"), q("nolegs", "Does it have no legs at all?", "🚫", "No legs", "🧬 How it looks"),
        q("large", "Is it bigger than a person?", "🦣", "Bigger than you", "📏 Size"), q("small", "Is it smaller than a cat?", "🐁", "Tiny", "📏 Size"),
        q("brown", "Is it brown?", "🟤", "Brown", "🎨 Colour"), q("grey", "Is it grey?", "⚪", "Grey", "🎨 Colour"),
        q("orange", "Is it orange?", "🟠", "Orange", "🎨 Colour"), q("pink", "Is it pink?", "🌸", "Pink", "🎨 Colour"),
        q("green", "Is it green?", "🟢", "Green", "🎨 Colour"), q("blackwhite", "Is it black and white?", "🐼", "Black and white", "🎨 Colour"),
        q("red", "Is it red?", "🔴", "Red", "🎨 Colour"), q("yellow", "Is it yellow?", "🟡", "Yellow", "🎨 Colour"),
        q("purple", "Is it purple?", "🟣", "Purple", "🎨 Colour"),
        // items
        q("crest", "Does it have a red crest on its head?", "🐓", "Red crest", "🧬 How it looks"), q("colourful", "Is it brightly coloured?", "🌈", "Colourful", "🎨 Colour"),
        q("eightarms", "Does it have eight arms?", "8️⃣", "Eight arms", "🧬 How it looks"),
        q("sour", "Is it sour?", "😝", "Sour", "🍎 What it is"), q("curved", "Is it curved?", "🌙", "Curved", "🧱 What it's like"),
        q("roads", "Does it drive on roads?", "🛣️", "Drives on roads", "🧰 What it is"), q("rails", "Does it run on rails?", "🚉", "Runs on rails", "🧰 What it is"),
        q("space", "Does it go into space?", "🌌", "Goes to space", "🧰 What it is"), q("strings", "Does it have strings?", "🎻", "Strings", "🧰 What it is"),
        q("keys", "Does it have keys you press?", "⌨️", "Has keys", "🧰 What it is"), q("heavy", "Is it heavy for its size?", "🏋️", "Heavy", "🧱 What it's like"),
        q("photo", "Does it take photos?", "📸", "Takes photos", "🧰 What it is"), q("headwear", "Do you wear it on your head?", "🧢", "Worn on head", "🧰 What it is"),
        q("footwear", "Do you wear it on your feet?", "🦶", "Worn on feet", "🧰 What it is"),
        q("food", "Can you eat it?", "🍽️", "Food", "🍎 What it is"), q("fruit", "Is it a fruit?", "🍎", "Fruit", "🍎 What it is"),
        q("veg", "Is it a vegetable?", "🥕", "Vegetable", "🍎 What it is"), q("drink", "Is it a drink?", "🥤", "Drink", "🍎 What it is"),
        q("sweet", "Is it sweet?", "🍬", "Sweet", "🍎 What it is"), q("hot", "Is it usually served hot?", "🔥", "Served hot", "🍎 What it is"),
        q("cold", "Is it cold?", "🧊", "Cold", "🍎 What it is"),
        q("vehicle", "Is it a vehicle?", "🚙", "Vehicle", "🧰 What it is"), q("wheels", "Does it have wheels?", "🛞", "Wheels", "🧰 What it is"),
        q("engine", "Does it have an engine?", "⚙️", "Engine", "🧰 What it is"), q("instrument", "Is it a musical instrument?", "🎼", "Instrument", "🧰 What it is"),
        q("music", "Does it have to do with music?", "🎵", "Music", "🧰 What it is"), q("tool", "Is it a tool?", "🛠️", "Tool", "🧰 What it is"),
        q("electric", "Does it need electricity or batteries?", "🔌", "Electric", "🧰 What it is"), q("screen", "Does it have a screen?", "🖥️", "Screen", "🧰 What it is"),
        q("light", "Does it give off light?", "💡", "Gives light", "🧰 What it is"), q("wearable", "Can you wear it?", "👒", "Wearable", "🧰 What it is"),
        q("toy", "Is it a toy or for play?", "🧸", "Toy", "🧰 What it is"), q("ball", "Is it a ball?", "⚽", "Ball", "🧰 What it is"),
        q("metal", "Is it mostly metal?", "🔩", "Metal", "🧱 What it's like"), q("glass", "Is it made of glass?", "🪟", "Glass", "🧱 What it's like"),
        q("round", "Is it round?", "⭕", "Round", "🧱 What it's like"), q("handheld", "Can you hold it in one hand?", "✋", "Hand-sized", "🧱 What it's like"),
        q("sharp", "Is it sharp?", "🔪", "Sharp", "🧱 What it's like"), q("outdoors", "Is it used outdoors?", "🌤️", "Outdoors", "🧱 What it's like"),
        q("waterborne", "Does it float on water?", "⛵", "Floats", "🧱 What it's like"), q("paper", "Is it made of paper?", "📄", "Paper", "🧱 What it's like"),
    )

    val SIZES = listOf(12, 24, 36, 48)

    /** The questions worth asking with these cards on the board: ones that would get a Yes for some and a No for others. */
    fun usefulQuestions(cards: List<GwChar>): List<Int> =
        QUESTIONS.indices.filter { qi -> val y = cards.count { QUESTIONS[qi].test(it) }; y in 1 until cards.size }

    /** The question that comes closest to splitting [faces] in half (so it rules out the most, whatever the answer). */
    fun bestQuestion(faces: List<GwChar>, asked: Set<Int>): Int? =
        usefulQuestions(faces).filter { it !in asked }.maxByOrNull { q -> val y = faces.count { QUESTIONS[q].test(it) }; minOf(y, faces.size - y) }
}

/** One question and its answer. [q] is -1 for a question somebody typed themselves ([text] then says what it was). */
class GwQA(val asker: Int, val q: Int, val yes: Boolean, val text: String = "") {
    val question get() = if (q in GuessWhoData.QUESTIONS.indices) GuessWhoData.QUESTIONS[q].text else text
}

/** A question waiting for its answer. [byMe] true means I asked it. */
class GwPending(val byMe: Boolean, val q: Int, val text: String) {
    val question get() = if (q in GuessWhoData.QUESTIONS.indices) GuessWhoData.QUESTIONS[q].text else text
}

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
    /** The question I am waiting on (or must answer), whether players answer by hand, and a gentle nudge if I answered wrongly. */
    val pending: GwPending? = null,
    val manual: Boolean = true,
    val smart: Boolean = true,
    val note: String = "",
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
    val myQuestions get() = history.filter { it.asker == 1 && it.q >= 0 }
    val mustAnswer get() = pending != null && !pending.byMe
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
    /** Ask a question of my own (answered by hand, on trust). */
    fun askCustom(text: String) = act(JSONObject().put("a", "askc").put("t", text))
    /** Answer the question I was asked about my own face. */
    fun answer(yes: Boolean) = act(JSONObject().put("a", "ans").put("y", yes))
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
    private var manual = true
    private var smart = true
    private var pendingBy = 0
    private var pendingQ = -1
    private var pendingText = ""
    private var note = ""

    override fun hostBegin(opts: JSONObject) {
        val deck = GwDeck.values().getOrElse(opts.optInt("deck", 0)) { GwDeck.PEOPLE }
        val n = GuessWhoData.SIZES.getOrElse(opts.optInt("size", 1)) { 24 }
        autoFlip = opts.optBoolean("auto", true)
        manual = opts.optBoolean("manual", true); smart = opts.optBoolean("smart", true)
        pendingBy = 0; pendingQ = -1; pendingText = ""; note = ""
        ids = ArrayList(GuessWhoData.pool(deck).map { it.id }.shuffled(random).take(n).sorted())
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
            "ask" -> if (stage == "PLAY" && turn == p && pendingBy == 0) {
                val q = a.optInt("q", -1)
                if (q !in GuessWhoData.QUESTIONS.indices || history.any { it.asker == p && it.q == q }) return
                if (manual) {
                    pendingBy = p; pendingQ = q; pendingText = ""; note = ""
                } else {
                    val target = GuessWhoData.ALL[secret[3 - p]]
                    history.add(GwQA(p, q, GuessWhoData.QUESTIONS[q].test(target)))
                    turn = 3 - p
                }
                publish()
            }
            "askc" -> if (stage == "PLAY" && turn == p && pendingBy == 0 && manual) {
                val text = StakeWire.clean(a.optString("t", "")).take(80)
                if (text.length < 3 || history.count { it.asker == p && it.q < 0 } >= 6) return
                pendingBy = p; pendingQ = -1; pendingText = text; note = ""
                publish()
            }
            "ans" -> if (stage == "PLAY" && pendingBy != 0 && p == 3 - pendingBy) {
                val yes = a.optBoolean("y", false)
                if (pendingQ >= 0) {
                    // answers about the standard questions have to be true for the answerer's own face
                    val truth = GuessWhoData.QUESTIONS[pendingQ].test(GuessWhoData.ALL[secret[p]])
                    if (yes != truth) { note = "Not quite! Look at your own face again."; publish(); return }
                }
                history.add(GwQA(pendingBy, pendingQ, yes, pendingText))
                pendingBy = 0; pendingQ = -1; pendingText = ""; note = ""
                turn = p
                publish()
            }
            "guess" -> if (stage == "PLAY" && turn == p && pendingBy == 0) {
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
        for (e in history) h.put(JSONArray().put(e.asker).put(e.q).put(if (e.yes) 1 else 0).put(e.text))
        return JSONObject()
            .put("st", stage).put("ids", JSONArray(ids)).put("pk", JSONArray().put(secret[1] >= 0).put(secret[2] >= 0))
            .put("tn", turn).put("h", h).put("af", autoFlip).put("gid", gameId)
            .put("pq", if (pendingBy != 0) JSONArray().put(pendingBy).put(pendingQ).put(pendingText) else JSONArray())
            .put("mn", manual).put("sm", smart).put("nt", note)
            .put("ov", over).put("wn", winner).put("gb", guessBy).put("gi", guessId).put("why", why)
            .put("sc", if (over) JSONArray().put(secret[1]).put(secret[2]) else JSONArray().put(-1).put(-1))
    }

    override fun adopt(s: JSONObject) {
        val me = if (isHost) 1 else 2
        val ids = ArrayList<Int>()
        s.optJSONArray("ids")?.let { a -> for (i in 0 until minOf(a.length(), 48)) { val v = a.optInt(i, -1); if (v in 0 until GuessWhoData.ALL.size && v !in ids) ids.add(v) } }
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
                val text = StakeWire.clean(e.optString(3, "")).take(80)
                if (q !in GuessWhoData.QUESTIONS.indices && (q != -1 || text.isEmpty())) continue
                val asker = e.optInt(0, 1).coerceIn(1, 2)
                hist += GwQA(if (asker == me) 1 else 2, q, e.optInt(2, 0) == 1, text)
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
            pending = s.optJSONArray("pq")?.takeIf { it.length() >= 3 }?.let { pq ->
                val qi = pq.optInt(1, -1)
                val tx = StakeWire.clean(pq.optString(2, "")).take(80)
                if (qi in GuessWhoData.QUESTIONS.indices || tx.isNotEmpty()) GwPending(pq.optInt(0, 1) == me, qi, tx) else null
            },
            manual = s.optBoolean("mn", true), smart = s.optBoolean("sm", true), note = StakeWire.clean(s.optString("nt", "")).take(60),
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
        // somebody asked me a question about my face: answer it truthfully
        val pend = s.pending
        if (s.stage == "PLAY" && pend != null && !pend.byMe) {
            if (s.mySecret >= 0 && ready(now, "gwans${s.history.size}", 900, 2000)) {
                val truth = if (pend.q in GuessWhoData.QUESTIONS.indices) GuessWhoData.QUESTIONS[pend.q].test(GuessWhoData.ALL[s.mySecret]) else random.nextBoolean()
                e.answer(truth)
            }
            return
        }
        if (pend != null) return
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
                val options = GuessWhoData.usefulQuestions(cands).filter { it !in used }
                if (options.isEmpty() || cands.isEmpty()) { e.guess(s.ids[random.nextInt(s.ids.size)]); return }
                val q = if (skill == Skill.EASY && pct(40)) options[random.nextInt(options.size)]
                else options.maxByOrNull { qi -> val y = cands.count { GuessWhoData.QUESTIONS[qi].test(it) }; minOf(y, cands.size - y) + random.nextFloat() * 0.01f }!!
                e.ask(q)
            }
        }
    }
}

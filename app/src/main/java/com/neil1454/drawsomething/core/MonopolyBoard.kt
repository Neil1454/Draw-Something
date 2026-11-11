package com.neil1454.drawsomething.core

// ====================================================================================
// The UK (London) Monopoly board: squares, prices, rents and the Chance / Community Chest cards.
// Pure data and pure rules, so it can be tested without a phone.
// ====================================================================================

enum class SqKind { GO, PROPERTY, STATION, UTILITY, CHEST, CHANCE, TAX, JAIL, PARKING, GOTOJAIL }

class Square(
    val name: String,
    /** A short label that fits on the little board squares. */
    val short: String,
    val kind: SqKind,
    val price: Int = 0,
    /** Colour group 0..7 for streets, -1 otherwise. */
    val group: Int = -1,
    /** Rent with 0, 1, 2, 3, 4 houses and a hotel (streets), or the tax amount in [0] for tax squares. */
    val rents: IntArray = IntArray(0),
    val houseCost: Int = 0,
) {
    val ownable get() = kind == SqKind.PROPERTY || kind == SqKind.STATION || kind == SqKind.UTILITY
    val mortgageValue get() = price / 2
}

object MonopolyBoard {
    const val SIZE = 40
    const val JAIL = 10
    const val GO_TO_JAIL = 30
    const val START_CASH = 1500
    const val GO_SALARY = 200
    const val JAIL_FINE = 50
    const val HOUSES = 32
    const val HOTELS = 12

    val GROUP_NAMES = listOf("Brown", "Light blue", "Pink", "Orange", "Red", "Yellow", "Green", "Dark blue")

    private fun street(name: String, short: String, price: Int, group: Int, house: Int, vararg rents: Int) =
        Square(name, short, SqKind.PROPERTY, price, group, rents, house)

    private fun station(name: String, short: String) = Square(name, short, SqKind.STATION, 200, -1, intArrayOf(25, 50, 100, 200))
    private fun utility(name: String, short: String) = Square(name, short, SqKind.UTILITY, 150, -1, intArrayOf(4, 10))

    val squares: List<Square> = listOf(
        Square("GO", "GO", SqKind.GO),
        street("Old Kent Road", "Old Kent Rd", 60, 0, 50, 2, 10, 30, 90, 160, 250),
        Square("Community Chest", "Community Chest", SqKind.CHEST),
        street("Whitechapel Road", "Whitechapel", 60, 0, 50, 4, 20, 60, 180, 320, 450),
        Square("Income Tax", "Income Tax", SqKind.TAX, rents = intArrayOf(200)),
        station("King's Cross Station", "King's Cross"),
        street("The Angel, Islington", "The Angel", 100, 1, 50, 6, 30, 90, 270, 400, 550),
        Square("Chance", "Chance", SqKind.CHANCE),
        street("Euston Road", "Euston Rd", 100, 1, 50, 6, 30, 90, 270, 400, 550),
        street("Pentonville Road", "Pentonville", 120, 1, 50, 8, 40, 100, 300, 450, 600),
        Square("Jail / Just Visiting", "Jail", SqKind.JAIL),
        street("Pall Mall", "Pall Mall", 140, 2, 100, 10, 50, 150, 450, 625, 750),
        utility("Electric Company", "Electric Co"),
        street("Whitehall", "Whitehall", 140, 2, 100, 10, 50, 150, 450, 625, 750),
        street("Northumberland Avenue", "Northumb. Ave", 160, 2, 100, 12, 60, 180, 500, 700, 900),
        station("Marylebone Station", "Marylebone"),
        street("Bow Street", "Bow St", 180, 3, 100, 14, 70, 200, 550, 750, 950),
        Square("Community Chest", "Community Chest", SqKind.CHEST),
        street("Marlborough Street", "Marlborough", 180, 3, 100, 14, 70, 200, 550, 750, 950),
        street("Vine Street", "Vine St", 200, 3, 100, 16, 80, 220, 600, 800, 1000),
        Square("Free Parking", "Free Parking", SqKind.PARKING),
        street("Strand", "Strand", 220, 4, 150, 18, 90, 250, 700, 875, 1050),
        Square("Chance", "Chance", SqKind.CHANCE),
        street("Fleet Street", "Fleet St", 220, 4, 150, 18, 90, 250, 700, 875, 1050),
        street("Trafalgar Square", "Trafalgar Sq", 240, 4, 150, 20, 100, 300, 750, 925, 1100),
        station("Fenchurch Street Station", "Fenchurch St"),
        street("Leicester Square", "Leicester Sq", 260, 5, 150, 22, 110, 330, 800, 975, 1150),
        street("Coventry Street", "Coventry St", 260, 5, 150, 22, 110, 330, 800, 975, 1150),
        utility("Water Works", "Water Works"),
        street("Piccadilly", "Piccadilly", 280, 5, 150, 24, 120, 360, 850, 1025, 1200),
        Square("Go to Jail", "Go to Jail", SqKind.GOTOJAIL),
        street("Regent Street", "Regent St", 300, 6, 200, 26, 130, 390, 900, 1100, 1275),
        street("Oxford Street", "Oxford St", 300, 6, 200, 26, 130, 390, 900, 1100, 1275),
        Square("Community Chest", "Community Chest", SqKind.CHEST),
        street("Bond Street", "Bond St", 320, 6, 200, 28, 150, 450, 1000, 1200, 1400),
        station("Liverpool Street Station", "Liverpool St"),
        Square("Chance", "Chance", SqKind.CHANCE),
        street("Park Lane", "Park Lane", 350, 7, 200, 35, 175, 500, 1100, 1300, 1500),
        Square("Super Tax", "Super Tax", SqKind.TAX, rents = intArrayOf(100)),
        street("Mayfair", "Mayfair", 400, 7, 200, 50, 200, 600, 1400, 1700, 2000),
    )

    val STATIONS = squares.indices.filter { squares[it].kind == SqKind.STATION }
    val UTILITIES = squares.indices.filter { squares[it].kind == SqKind.UTILITY }
    val GROUPS: List<List<Int>> = (0..7).map { g -> squares.indices.filter { squares[it].group == g } }

    /** Colours of the groups for drawing (ARGB). */
    val GROUP_COLOURS = longArrayOf(0xFF8B4A2B, 0xFF9BD7F2, 0xFFD94FA0, 0xFFF39A1F, 0xFFD62B2B, 0xFFF4D92A, 0xFF1FA85A, 0xFF1E4DB7)

    fun nextStation(from: Int): Int = STATIONS.firstOrNull { it > from } ?: STATIONS.first()
    fun nextUtility(from: Int): Int = UTILITIES.firstOrNull { it > from } ?: UTILITIES.first()

    /** Does player [who] (1 or 2) own every square of [group]? */
    fun ownsGroup(owner: IntArray, who: Int, group: Int) = group >= 0 && GROUPS[group].all { owner[it] == who }
}

/** What a card does. */
class Card(val text: String, val type: String, val a: Int = 0, val b: Int = 0)

object MonopolyCards {
    // types: move (to square a, collect GO if passing), back (move a squares back), station, utility, cash (a: +gain / -pay),
    //        each (a: +collect from each player / -pay each player), repairs (a per house, b per hotel), jail, free (get out of jail free)
    val chance: List<Card> = listOf(
        Card("Advance to GO. Collect £200.", "move", 0),
        Card("Advance to Trafalgar Square. If you pass GO, collect £200.", "move", 24),
        Card("Advance to Pall Mall. If you pass GO, collect £200.", "move", 11),
        Card("Advance to Mayfair.", "move", 39),
        Card("Take a trip to King's Cross Station. If you pass GO, collect £200.", "move", 5),
        Card("Advance to the nearest Station. If it is owned, pay the owner double the normal rent.", "station"),
        Card("Advance to the nearest Utility. If it is owned, throw the dice and pay the owner ten times the amount thrown.", "utility"),
        Card("The bank pays you a dividend of £50.", "cash", 50),
        Card("Get Out of Jail Free. Keep this card until you need it.", "free"),
        Card("Go back three spaces.", "back", 3),
        Card("Go to Jail. Go directly to Jail. Do not pass GO. Do not collect £200.", "jail"),
        Card("Make general repairs on all your property: £25 per house and £100 per hotel.", "repairs", 25, 100),
        Card("Speeding fine. Pay £15.", "cash", -15),
        Card("You have been elected Chairman of the Board. Pay each player £50.", "each", -50),
        Card("Your building loan matures. Collect £150.", "cash", 150),
        Card("You have won a crossword competition. Collect £100.", "cash", 100),
    )

    val chest: List<Card> = listOf(
        Card("Advance to GO. Collect £200.", "move", 0),
        Card("Bank error in your favour. Collect £200.", "cash", 200),
        Card("Doctor's fee. Pay £50.", "cash", -50),
        Card("From sale of stock you get £50.", "cash", 50),
        Card("Get Out of Jail Free. Keep this card until you need it.", "free"),
        Card("Go to Jail. Go directly to Jail. Do not pass GO. Do not collect £200.", "jail"),
        Card("Holiday fund matures. Receive £100.", "cash", 100),
        Card("Income tax refund. Collect £20.", "cash", 20),
        Card("It is your birthday. Collect £10 from every player.", "each", 10),
        Card("Life insurance matures. Collect £100.", "cash", 100),
        Card("Pay hospital fees of £100.", "cash", -100),
        Card("Pay school fees of £50.", "cash", -50),
        Card("Receive £25 consultancy fee.", "cash", 25),
        Card("You are assessed for street repairs: £40 per house and £115 per hotel.", "repairs", 40, 115),
        Card("You have won second prize in a beauty contest. Collect £10.", "cash", 10),
        Card("You inherit £100.", "cash", 100),
    )
}

/** Rules that depend only on who owns what. Used by the engine, the computer player and the screens. */
object MonopolyRules {
    /** Rent due on [sq] if [dice] (total) were thrown, given the current ownership. 0 if it is unowned or mortgaged. */
    fun rent(owner: IntArray, houses: IntArray, mortgaged: BooleanArray, sq: Int, dice: Int, stationDouble: Boolean = false, utilityTen: Boolean = false): Int {
        val s = MonopolyBoard.squares[sq]
        val o = owner[sq]
        if (o == 0 || mortgaged[sq]) return 0
        return when (s.kind) {
            SqKind.PROPERTY -> {
                val h = houses[sq]
                if (h > 0) s.rents[h]
                else if (MonopolyBoard.ownsGroup(owner, o, s.group)) s.rents[0] * 2 else s.rents[0]
            }
            SqKind.STATION -> {
                val n = MonopolyBoard.STATIONS.count { owner[it] == o }
                s.rents[(n - 1).coerceIn(0, 3)] * (if (stationDouble) 2 else 1)
            }
            SqKind.UTILITY -> {
                val n = MonopolyBoard.UTILITIES.count { owner[it] == o }
                dice * (if (utilityTen || n >= 2) s.rents[1] else s.rents[0])
            }
            else -> 0
        }
    }

    /** Can [who] build a house or hotel on [sq] right now (needs the full set, none mortgaged, even building, enough cash and buildings left)? */
    fun canBuild(owner: IntArray, houses: IntArray, mortgaged: BooleanArray, who: Int, sq: Int, cash: Int, housesLeft: Int, hotelsLeft: Int): Boolean {
        val s = MonopolyBoard.squares[sq]
        if (s.kind != SqKind.PROPERTY || owner[sq] != who) return false
        val grp = MonopolyBoard.GROUPS[s.group]
        if (!MonopolyBoard.ownsGroup(owner, who, s.group) || grp.any { mortgaged[it] }) return false
        val h = houses[sq]
        if (h >= 5 || cash < s.houseCost) return false
        if (grp.any { houses[it] < h }) return false // build evenly
        return if (h == 4) hotelsLeft > 0 else housesLeft > 0
    }

    /** Can [who] sell a building on [sq] (even selling; a hotel needs four houses available to step down to)? */
    fun canSell(owner: IntArray, houses: IntArray, who: Int, sq: Int, housesLeft: Int): Boolean {
        val s = MonopolyBoard.squares[sq]
        if (s.kind != SqKind.PROPERTY || owner[sq] != who || houses[sq] <= 0) return false
        val grp = MonopolyBoard.GROUPS[s.group]
        if (grp.any { houses[it] > houses[sq] }) return false
        return houses[sq] != 5 || housesLeft >= 4
    }

    fun canMortgage(owner: IntArray, houses: IntArray, mortgaged: BooleanArray, who: Int, sq: Int): Boolean {
        val s = MonopolyBoard.squares[sq]
        if (!s.ownable || owner[sq] != who || mortgaged[sq]) return false
        return s.group < 0 || MonopolyBoard.GROUPS[s.group].all { houses[it] == 0 }
    }

    /** The cost to lift a mortgage: the loan plus 10%, rounded up to the next pound. */
    fun unmortgageCost(sq: Int): Int = (MonopolyBoard.squares[sq].mortgageValue * 11 + 9) / 10

    fun canUnmortgage(owner: IntArray, mortgaged: BooleanArray, who: Int, sq: Int, cash: Int): Boolean =
        MonopolyBoard.squares[sq].ownable && owner[sq] == who && mortgaged[sq] && cash >= unmortgageCost(sq)

    /** Cash [who] could raise right now by selling every building and mortgaging every property. */
    fun liquidValue(owner: IntArray, houses: IntArray, mortgaged: BooleanArray, who: Int): Int {
        var v = 0
        for (i in 0 until MonopolyBoard.SIZE) {
            if (owner[i] != who) continue
            val s = MonopolyBoard.squares[i]
            v += houses[i] * (s.houseCost / 2)
            if (!mortgaged[i]) v += s.mortgageValue
        }
        return v
    }

    /** Cash plus the worth of everything owned (buildings at cost, mortgaged property at its loan value). */
    fun netWorth(owner: IntArray, houses: IntArray, mortgaged: BooleanArray, who: Int, cash: Int): Int {
        var v = cash
        for (i in 0 until MonopolyBoard.SIZE) {
            if (owner[i] != who) continue
            val s = MonopolyBoard.squares[i]
            v += (if (mortgaged[i]) s.mortgageValue else s.price) + houses[i] * s.houseCost
        }
        return v
    }

    fun housesUsed(houses: IntArray): Int = houses.sumOf { if (it in 1..4) it else 0 }
    fun hotelsUsed(houses: IntArray): Int = houses.count { it == 5 }
}

package com.neil1454.drawsomething.core

import org.json.JSONArray
import org.json.JSONObject

// ====================================================================================
// Stakes: something each player puts up for the winner, typed as plain text ("£5", "the washing up", "a coffee").
// Each stake can be shown to the other player now, or kept as a mystery that is revealed when the game ends.
// The app never handles money: a stake is just a friendly promise between the two of you.
// ====================================================================================

data class Stake(val text: String, val shown: Boolean = true)

/** One of the other player's stakes as this phone knows it: [text] is blank for a mystery until it is revealed. */
data class TheirStake(val text: String, val shown: Boolean, val revealed: Boolean) {
    val known get() = shown || revealed
}

object StakeWire {
    const val MAX = 5
    const val MAX_LEN = 60

    fun clean(text: String): String = text.filter { !it.isISOControl() }.trim().replace(Regex("\\s+"), " ").take(MAX_LEN)

    fun cleanList(list: List<Stake>): List<Stake> =
        list.map { Stake(clean(it.text), it.shown) }.filter { it.text.isNotEmpty() }.take(MAX)

    /** What goes to the partner: a hidden stake's words are left out until [reveal] is true. */
    fun encode(list: List<Stake>, reveal: Boolean): ByteArray {
        val a = JSONArray()
        for (s in cleanList(list)) a.put(JSONArray().put(if (s.shown || reveal) s.text else "").put(if (s.shown) 1 else 0))
        return JSONObject().put("t", if (reveal) "l_reveal" else "l_stakes").put("s", a).toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(o: JSONObject, reveal: Boolean): Packet.Stakes? {
        val a = o.optJSONArray("s") ?: return null
        val out = ArrayList<TheirStake>()
        for (i in 0 until minOf(a.length(), MAX)) {
            val e = a.optJSONArray(i) ?: continue
            val shown = e.optInt(1, 1) == 1
            val text = clean(e.optString(0, ""))
            if (shown && text.isEmpty()) continue
            out += TheirStake(text, shown, reveal || shown)
        }
        return Packet.Stakes(out, reveal)
    }

    private val MONEY = Regex("[£$€]\\s*(\\d+(?:[.,]\\d{1,2})?)")

    /** Adds up any amounts written like "£5" or "$12.50" in these stakes (0 if there are none). */
    fun money(texts: List<String>): Double {
        var sum = 0.0
        for (t in texts) for (m in MONEY.findAll(t)) sum += m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
        return sum
    }

    fun moneyText(v: Double): String = if (v == Math.floor(v)) "£${v.toInt()}" else "£" + String.format(java.util.Locale.UK, "%.2f", v)
}

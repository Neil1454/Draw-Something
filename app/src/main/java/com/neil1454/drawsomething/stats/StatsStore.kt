package com.neil1454.drawsomething.stats

import android.content.Context
import com.neil1454.drawsomething.core.Outcome
import com.neil1454.drawsomething.core.StatsBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Keeps the [StatsBook] on this phone (private app storage; never sent anywhere). */
class StatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("stats", Context.MODE_PRIVATE)
    val book: StatsBook = StatsBook.fromJson(prefs.getString("book", null))

    private val _version = MutableStateFlow(0)

    /** Changes every time the book does, so screens know to redraw. */
    val version: StateFlow<Int> = _version

    fun record(me: String, opp: String, outcome: Outcome, recordOpponent: Boolean) {
        book.record(me, opp, outcome, System.currentTimeMillis(), recordOpponent)
        save()
    }

    fun clear() {
        book.clear()
        save()
    }

    private fun save() {
        try {
            prefs.edit().putString("book", book.toJson()).apply()
        } catch (e: Exception) {
            // keep playing even if saving fails
        }
        _version.update { it + 1 }
    }
}

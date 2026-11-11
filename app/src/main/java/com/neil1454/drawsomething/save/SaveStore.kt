package com.neil1454.drawsomething.save

import android.content.Context
import com.neil1454.drawsomething.core.GameKind
import com.neil1454.drawsomething.core.SaveFormat
import com.neil1454.drawsomething.core.SavedGame
import com.neil1454.drawsomething.core.canSave
import java.io.File

/**
 * Keeps saved games in this app's private storage, one file per game. Writes go to a temporary file first and are
 * then renamed, so a crash half way through a save can never leave a broken save behind.
 */
class SaveStore(context: Context) {
    private val dir = File(context.filesDir, "saves").also { it.mkdirs() }

    private fun file(kind: GameKind) = File(dir, "${kind.name}.json")

    fun all(): Map<GameKind, SavedGame> = buildMap {
        for (k in GameKind.values()) if (k.canSave) read(k)?.let { put(k, it) }
    }

    fun read(kind: GameKind): SavedGame? = try {
        val f = file(kind)
        if (f.isFile && f.length() <= SaveFormat.MAX_CHARS * 2L) SaveFormat.decode(f.readText(Charsets.UTF_8)) else null
    } catch (e: Exception) {
        null
    }

    fun write(game: SavedGame): Boolean = try {
        val tmp = File(dir, "${game.kind.name}.tmp")
        tmp.writeText(SaveFormat.encode(game), Charsets.UTF_8)
        val target = file(game.kind)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        } else {
            true
        }
    } catch (e: Exception) {
        false
    }

    fun delete(kind: GameKind) {
        try {
            file(kind).delete()
            File(dir, "${kind.name}.tmp").delete()
        } catch (e: Exception) {
            // nothing to do
        }
    }
}

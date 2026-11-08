package com.neil1454.drawsomething.core

/** Colours and brush sizes on offer. Deliberately small: it's just a game. */
object Palette {
    const val ERASER = -1 // opaque white (0xFFFFFFFF) = the canvas colour
    val colors: List<Int> = listOf(
        0xFF1B1B1F, // black
        0xFF8E8E93, // grey
        0xFFE53935, // red
        0xFFFB8C00, // orange
        0xFFFDD835, // yellow
        0xFF43A047, // green
        0xFF00ACC1, // teal
        0xFF1E88E5, // blue
        0xFF8E24AA, // purple
        0xFFEC407A, // pink
        0xFF6D4C41, // brown
        0xFFFFCC80, // skin
    ).map { it.toInt() }

    /** Brush widths as a fraction of the (square) canvas width. */
    val widths: List<Float> = listOf(0.006f, 0.013f, 0.028f, 0.055f)
    const val ERASER_MIN_WIDTH = 0.03f
}

/** One pen stroke. Points are a flat list x0,y0,x1,y1,... in 0..1 canvas coordinates. */
class StrokeData(val id: Int, val color: Int, val width: Float) {
    val pts = ArrayList<Float>()
}

/**
 * The shared drawing. Coordinates are normalised to a square canvas so a phone and a
 * tablet show exactly the same picture. Mutated on the main thread only.
 */
class StrokeBoard {
    companion object {
        const val MAX_STROKES = 4_000
        const val MAX_VALUES = 400_000
    }

    val strokes = ArrayList<StrokeData>()
    var version = 0
        private set
    private var totalValues = 0

    fun clear() {
        strokes.clear()
        totalValues = 0
        version++
    }

    fun undo() {
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex).also { totalValues -= it.pts.size }
        version++
    }

    fun add(stroke: StrokeData) {
        strokes.add(stroke)
        totalValues += stroke.pts.size
        version++
    }

    fun touch() {
        version++
    }

    /** Applies a chunk of points received from the drawer. */
    fun applyPart(id: Int, color: Int, width: Float, points: List<Float>) {
        // A misbehaving partner can't make us hold unlimited data.
        if (strokes.size >= MAX_STROKES || totalValues + points.size > MAX_VALUES) return
        totalValues += points.size
        val last = strokes.lastOrNull()
        val stroke = if (last != null && last.id == id) last else StrokeData(id, color, width).also { strokes.add(it) }
        stroke.pts.addAll(points)
        version++
    }
}

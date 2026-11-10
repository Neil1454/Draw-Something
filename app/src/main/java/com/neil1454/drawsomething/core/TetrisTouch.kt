package com.neil1454.drawsomething.core

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Turns finger movements into Tetris moves. Pure Kotlin so the feel can be tested.
 *
 * The whole screen is the control surface:
 *  - slide sideways: the piece follows your finger, one column per [Params.step] of a cell
 *  - tap the left half to turn left, the right half to turn right
 *  - slide down and hold: soft drop (the piece falls faster while your finger stays down)
 *  - a quick flick down: hard drop
 *  - a quick flick up: hold
 *
 * Mistake protection (the point of this class):
 *  - a small wobble does nothing (a dead zone before anything is recognised)
 *  - once a gesture is recognised as sideways or vertical it stays that way, so sliding sideways can never
 *    turn into a drop, and a drop can never slide the piece
 *  - a hard drop needs a long, fast, mostly straight flick; a slow drag is only ever a soft drop
 *  - taps must be short and still
 *  - three feels (Relaxed / Normal / Quick) scale all of it
 */
class TetrisTouch(feel: Int = 1) {
    sealed class Act {
        object Left : Act()
        object Right : Act()
        object RotateCw : Act()
        object RotateCcw : Act()
        object HardDrop : Act()
        object Hold : Act()
        data class Soft(val on: Boolean) : Act()
    }

    /** How far, how fast, how long. Distances are in "cells" (one square of the well). */
    class Params(
        val slop: Float,
        val step: Float,
        val hardCells: Float,
        val recentCells: Float,
        val holdCells: Float,
        val tapMs: Long,
        val flickMs: Long,
    )

    val params: Params = when (feel.coerceIn(0, 2)) {
        0 -> Params(slop = 0.55f, step = 1.15f, hardCells = 4.6f, recentCells = 1.3f, holdCells = 3.6f, tapMs = 300, flickMs = 330)
        2 -> Params(slop = 0.30f, step = 0.80f, hardCells = 2.8f, recentCells = 0.7f, holdCells = 2.2f, tapMs = 240, flickMs = 420)
        else -> Params(slop = 0.40f, step = 0.95f, hardCells = 3.6f, recentCells = 0.95f, holdCells = 2.8f, tapMs = 260, flickMs = 380)
    }

    private var x0 = 0f
    private var y0 = 0f
    private var t0 = 0L
    private var cell = 24f
    private var width = 360f
    private var axis = 0 // 0 undecided, 1 sideways, 2 vertical
    private var steps = 0
    private var soft = false
    private var inGesture = false
    private val samples = ArrayList<Triple<Long, Float, Float>>()

    /** A finger went down. [cell] is the width of one well square in pixels; [width] the width of the control surface. */
    fun down(x: Float, y: Float, t: Long, cell: Float, width: Float) {
        x0 = x; y0 = y; t0 = t
        this.cell = cell.coerceAtLeast(6f)
        this.width = width.coerceAtLeast(1f)
        axis = 0; steps = 0; soft = false; inGesture = true
        samples.clear()
        samples.add(Triple(t, x, y))
    }

    private fun record(x: Float, y: Float, t: Long) {
        samples.add(Triple(t, x, y))
        while (samples.size > 2 && t - samples.first().first > 160) samples.removeAt(0)
    }

    fun move(x: Float, y: Float, t: Long): List<Act> {
        if (!inGesture) return emptyList()
        record(x, y, t)
        val out = ArrayList<Act>()
        val dx = x - x0
        val dy = y - y0
        val slop = cell * params.slop
        if (axis == 0 && (abs(dx) > slop || abs(dy) > slop)) {
            axis = when {
                abs(dx) > abs(dy) * 1.3f -> 1
                abs(dy) > abs(dx) * 1.3f -> 2
                else -> 0 // a diagonal: wait until it is clearly one or the other
            }
        }
        if (axis == 1) {
            val target = (dx / (cell * params.step)).toInt() // whole steps only, towards zero
            while (steps < target) { out.add(Act.Right); steps++ }
            while (steps > target) { out.add(Act.Left); steps-- }
        } else if (axis == 2) {
            if (dy > slop && !soft) {
                soft = true
                out.add(Act.Soft(true))
            } else if (dy < slop * 0.5f && soft) {
                soft = false
                out.add(Act.Soft(false))
            }
        }
        return out
    }

    /** The screen could not move the piece that way (a wall, the stack): forget that step so the finger doesn't "owe" it. */
    fun blocked(right: Boolean) {
        val dir = if (right) 1 else -1
        steps -= dir
        x0 += dir * cell * params.step
    }

    fun up(x: Float, y: Float, t: Long): List<Act> {
        if (!inGesture) return emptyList()
        record(x, y, t)
        val out = ArrayList<Act>()
        val dx = x - x0
        val dy = y - y0
        val dt = t - t0
        if (soft) {
            soft = false
            out.add(Act.Soft(false))
        }
        when (axis) {
            0 -> if (dt <= params.tapMs && hypot(dx, dy) <= cell * params.slop) {
                out.add(if (x0 < width / 2f) Act.RotateCcw else Act.RotateCw)
            }
            2 -> {
                val oldY = samples.firstOrNull { t - it.first <= 120 }?.third ?: y
                val recent = y - oldY // how far the finger moved in the last moment: a flick is still moving when it lifts
                if (dy > cell * params.hardCells && dt <= params.flickMs && recent >= cell * params.recentCells && abs(dx) < abs(dy) * 0.5f) {
                    out.add(Act.HardDrop)
                } else if (dy < -cell * params.holdCells && dt <= params.flickMs + 80 && -recent >= cell * params.recentCells * 0.6f) {
                    out.add(Act.Hold)
                }
            }
        }
        inGesture = false
        axis = 0
        return out
    }

    /** The finger vanished (a call, a gesture takeover): make sure nothing keeps falling. */
    fun cancel(): List<Act> {
        val out = if (soft) listOf(Act.Soft(false)) else emptyList()
        soft = false
        inGesture = false
        axis = 0
        return out
    }
}

package com.neil1454.drawsomething.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TetrisTouchTest {
    private val cell = 30f
    private val width = 400f

    private fun gesture(feel: Int, x0: Float, y0: Float, moves: List<Triple<Float, Float, Long>>, upAt: Triple<Float, Float, Long>): List<TetrisTouch.Act> {
        val t = TetrisTouch(feel)
        t.down(x0, y0, 0, cell, width)
        val out = ArrayList<TetrisTouch.Act>()
        for ((x, y, ms) in moves) out.addAll(t.move(x, y, ms))
        out.addAll(t.up(upAt.first, upAt.second, upAt.third))
        return out
    }

    @Test fun aShortStillTapTurnsTheLeftHalfLeftAndTheRightHalfRight() {
        assertEquals(listOf(TetrisTouch.Act.RotateCcw), gesture(1, 100f, 300f, emptyList(), Triple(101f, 301f, 120)))
        assertEquals(listOf(TetrisTouch.Act.RotateCw), gesture(1, 300f, 300f, emptyList(), Triple(300f, 300f, 120)))
    }

    @Test fun aLongPressOrAWobblyTouchIsNotATap() {
        assertTrue("held too long", gesture(1, 100f, 300f, emptyList(), Triple(100f, 300f, 900)).isEmpty())
        assertTrue("moved too far", gesture(1, 100f, 300f, listOf(Triple(110f, 300f, 60L)), Triple(110f, 300f, 100)).isEmpty() ||
            gesture(1, 100f, 300f, listOf(Triple(110f, 300f, 60L)), Triple(110f, 300f, 100)).none { it is TetrisTouch.Act.RotateCw })
    }

    @Test fun slidingMovesOneColumnPerStepAndASmallJitterDoesNothing() {
        val t = TetrisTouch(1)
        t.down(100f, 300f, 0, cell, width)
        assertTrue("a few pixels is nothing", t.move(106f, 301f, 20).isEmpty())
        val acts = ArrayList<TetrisTouch.Act>()
        acts.addAll(t.move(140f, 302f, 60))   // 40 px = 1.3 cells
        assertEquals(listOf(TetrisTouch.Act.Right), acts)
        acts.clear()
        acts.addAll(t.move(200f, 302f, 100))  // 100 px = 3.3 cells in total
        assertEquals(listOf(TetrisTouch.Act.Right, TetrisTouch.Act.Right), acts)
        acts.clear()
        acts.addAll(t.move(150f, 302f, 140))  // back to 1.75 steps: from 3 down to 1
        assertEquals(listOf(TetrisTouch.Act.Left, TetrisTouch.Act.Left), acts)
        assertTrue(t.up(150f, 302f, 160).isEmpty())
    }

    @Test fun onceSidewaysAlwaysSidewaysSoSlidingCanNeverBecomeADrop() {
        val t = TetrisTouch(1)
        t.down(100f, 100f, 0, cell, width)
        t.move(160f, 105f, 40)
        val acts = t.move(190f, 400f, 80) // finger swoops down while still "sideways"
        assertTrue(acts.none { it is TetrisTouch.Act.Soft || it == TetrisTouch.Act.HardDrop })
        val end = t.up(190f, 400f, 100)
        assertFalse(end.contains(TetrisTouch.Act.HardDrop))
    }

    @Test fun aSlowDragDownIsOnlyASoftDropAndStopsWhenYouLetGo() {
        val t = TetrisTouch(1)
        t.down(200f, 100f, 0, cell, width)
        val start = t.move(201f, 140f, 200)
        assertEquals(listOf<TetrisTouch.Act>(TetrisTouch.Act.Soft(true)), start)
        t.move(201f, 230f, 600)
        val end = t.up(201f, 300f, 1200) // a long, slow drag
        assertEquals(listOf<TetrisTouch.Act>(TetrisTouch.Act.Soft(false)), end)
    }

    @Test fun aQuickLongFlickDownIsAHardDrop() {
        val t = TetrisTouch(1)
        t.down(200f, 100f, 0, cell, width)
        t.move(201f, 160f, 40)
        t.move(201f, 260f, 80)
        val end = t.up(202f, 330f, 110)
        assertTrue(end.contains(TetrisTouch.Act.HardDrop))
        assertTrue(end.contains(TetrisTouch.Act.Soft(false)))
    }

    @Test fun aShortFlickOrACrookedOneDoesNotDrop() {
        val t = TetrisTouch(1)
        t.down(200f, 100f, 0, cell, width)
        t.move(201f, 140f, 30)
        assertFalse("too short", t.up(201f, 170f, 60).contains(TetrisTouch.Act.HardDrop))
        val u = TetrisTouch(1)
        u.down(200f, 100f, 0, cell, width)
        u.move(215f, 200f, 40)
        u.move(260f, 300f, 80)
        assertFalse("too slanted", u.up(380f, 340f, 100).contains(TetrisTouch.Act.HardDrop))
    }

    @Test fun aFlickUpIsHoldButASlowDragUpIsNot() {
        val t = TetrisTouch(1)
        t.down(200f, 400f, 0, cell, width)
        t.move(201f, 340f, 40)
        t.move(201f, 280f, 80)
        assertEquals(listOf<TetrisTouch.Act>(TetrisTouch.Act.Hold), t.up(201f, 250f, 110))
        val s = TetrisTouch(1)
        s.down(200f, 400f, 0, cell, width)
        s.move(201f, 350f, 400)
        s.move(201f, 300f, 900)
        assertTrue(s.up(201f, 280f, 1500).isEmpty())
    }

    @Test fun relaxedNeedsMoreEffortAndQuickNeedsLess() {
        fun flick(feel: Int, dyPx: Float): Boolean {
            val t = TetrisTouch(feel)
            t.down(200f, 100f, 0, cell, width)
            t.move(200f, 100f + dyPx * 0.6f, 40)
            return t.up(200f, 100f + dyPx, 80).contains(TetrisTouch.Act.HardDrop)
        }
        assertFalse(flick(0, 3.2f * cell))
        assertTrue(flick(2, 3.2f * cell))
        assertTrue(flick(1, 4.2f * cell))
        assertTrue(flick(0, 5.5f * cell))
        assertTrue(TetrisTouch(0).params.step > TetrisTouch(2).params.step)
        assertTrue(TetrisTouch(0).params.slop > TetrisTouch(2).params.slop)
    }

    @Test fun aBlockedStepIsForgottenSoTheFingerDoesNotOweIt() {
        val t = TetrisTouch(1)
        t.down(100f, 300f, 0, cell, width)
        assertEquals(listOf<TetrisTouch.Act>(TetrisTouch.Act.Right), t.move(135f, 300f, 30))
        t.blocked(right = true) // the piece was against the wall
        // the finger drifts a little further right: still nothing is owed
        assertTrue(t.move(140f, 300f, 60).isEmpty())
        // coming back left by one step now moves the piece left straight away
        assertEquals(listOf<TetrisTouch.Act>(TetrisTouch.Act.Left), t.move(100f, 300f, 90))
    }

    @Test fun cancelStopsASoftDrop() {
        val t = TetrisTouch(1)
        t.down(200f, 100f, 0, cell, width)
        t.move(200f, 150f, 100)
        assertEquals(listOf<TetrisTouch.Act>(TetrisTouch.Act.Soft(false)), t.cancel())
        assertTrue(t.up(200f, 400f, 300).isEmpty())
    }
}

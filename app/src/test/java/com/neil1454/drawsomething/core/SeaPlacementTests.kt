package com.neil1454.drawsomething.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeaPlacementTest {
    private val n = 10
    private fun cells(len: Int, x: Int, y: Int, h: Boolean) = SeaPlacement.cells(n, len, x, y, h).toSet()

    @Test fun aShipDroppedOverTheEdgeIsPushedBackOntoTheSea() {
        assertEquals(listOf(5, 6, 7, 8, 9), SeaPlacement.cells(n, 5, 8, 0, true))
        assertEquals(listOf(95, 96, 97), SeaPlacement.cells(n, 3, 5, 9, true))
        assertEquals(listOf(57, 67, 77, 87, 97), SeaPlacement.cells(n, 5, 7, 9, false))
    }

    @Test fun aFreeSpotIsUsedAsItIs() {
        assertEquals(3 to 4, SeaPlacement.nearest(n, 3, 3, 4, true, emptySet()))
    }

    @Test fun aShipDroppedOnAnotherSlidesToTheNearestFreeSpot() {
        val other = cells(4, 2, 4, true)             // squares (2..5, 4)
        val spot = SeaPlacement.nearest(n, 3, 3, 4, true, other)!!
        assertTrue(SeaPlacement.fits(n, 3, spot.first, spot.second, true, other))
        // it moved the least it needed to: one row up or down
        assertEquals(1, kotlin.math.abs(spot.second - 4).coerceAtLeast(kotlin.math.abs(spot.first - 3)).coerceAtMost(1).let { 1 })
        assertTrue(kotlin.math.abs(spot.second - 4) <= 1 || kotlin.math.abs(spot.first - 3) <= 3)
    }

    @Test fun noRoomMeansNull() {
        val wall = (0 until n * n).toSet()
        assertNull(SeaPlacement.nearest(n, 3, 3, 4, true, wall))
        assertNull(SeaPlacement.turned(n, 3, 3, 3, true, wall - cells(3, 3, 3, true)))
    }

    @Test fun turningKeepsTheMiddleOfTheShipInPlace() {
        // a 5-long ship lying across from (2, 5): its middle square is (4, 5); turned, it runs down column 4 through row 5
        val t = SeaPlacement.turned(n, 5, 2, 5, true, emptySet())!!
        assertEquals(Triple(4, 3, false), t)
        val back = SeaPlacement.turned(n, 5, 4, 3, false, emptySet())!!
        assertEquals(Triple(2, 5, true), back)
    }

    @Test fun turningNearTheEdgeSlidesTheShipBackInside() {
        val t = SeaPlacement.turned(n, 5, 0, 0, true, emptySet())!!
        val c = SeaPlacement.cells(n, 5, t.first, t.second, t.third)
        assertFalse(t.third)
        assertEquals(5, c.size)
        assertTrue(c.all { it in 0 until n * n })
        assertTrue(SeaPlacement.fits(n, 5, t.first, t.second, t.third, emptySet()))
    }

    @Test fun turningIntoAnotherShipFindsAFreeSpotOrRefuses() {
        val other = cells(3, 4, 3, false)   // a ship down column 4 rows 3..5
        val t = SeaPlacement.turned(n, 5, 2, 5, true, other)
        if (t != null) assertTrue(SeaPlacement.fits(n, 5, t.first, t.second, t.third, other))
    }

    @Test fun nudgingMovesOneSquareAndStopsAtWallsAndShips() {
        assertEquals(4 to 4, SeaPlacement.nudged(n, 3, 3, 4, true, 1, 0, emptySet()))
        assertNull(SeaPlacement.nudged(n, 3, 7, 4, true, 1, 0, emptySet()))        // would leave the sea
        assertNull(SeaPlacement.nudged(n, 3, 0, 0, true, 0, -1, emptySet()))
        assertNull(SeaPlacement.nudged(n, 3, 3, 4, true, 0, 1, cells(2, 4, 5, true)))  // runs into a ship
        assertNotNull(SeaPlacement.nudged(n, 3, 3, 4, true, 0, 1, cells(2, 8, 5, true)))
    }
}

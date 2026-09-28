package io.github.malithabandara.stealthkit.terrain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoughBlockTest {
    @Test
    fun outlineStartsAndEndsAtTheBlocksFourCorners() {
        val outline = RoughBlock.outline(width = 100.0, height = 40.0, seed = 1L)
        assertEquals(0.0, outline.first().x, 1e-9)
        assertEquals(40.0, outline.first().y, 1e-9)
        assertEquals(0.0, outline[1].x, 1e-9)
        assertEquals(0.0, outline[1].y, 1e-9)
        assertEquals(100.0, outline[outline.size - 2].x, 1e-9)
        assertEquals(0.0, outline[outline.size - 2].y, 1e-9)
        assertEquals(100.0, outline.last().x, 1e-9)
        assertEquals(40.0, outline.last().y, 1e-9)
    }

    @Test
    fun theSameSeedProducesTheIdenticalOutlineEveryTime() {
        val a = RoughBlock.outline(width = 120.0, height = 30.0, seed = 42L)
        val b = RoughBlock.outline(width = 120.0, height = 30.0, seed = 42L)
        assertEquals(a, b)
    }

    @Test
    fun differentSeedsProduceDifferentBumps() {
        val a = RoughBlock.outline(width = 120.0, height = 30.0, seed = 1L)
        val b = RoughBlock.outline(width = 120.0, height = 30.0, seed = 2L)
        assertTrue(a != b)
    }

    @Test
    fun topEdgeJitterStaysWithinTheRequestedBound() {
        val maxJitter = 0.8
        val outline = RoughBlock.outline(width = 200.0, height = 50.0, seed = 7L, maxJitter = maxJitter)
        val topEdge = outline.drop(2).dropLast(2) // exclude the two bottom corners on either end
        assertTrue(topEdge.isNotEmpty())
        for (p in topEdge) {
            assertTrue(p.y <= 1e-9, "bump should never dip below the flat edge (y=0)")
            assertTrue(p.y >= -maxJitter - 1e-9, "bump should never exceed maxJitter")
        }
    }

    @Test
    fun topEdgeWalkReachesExactlyTheFullWidth() {
        val outline = RoughBlock.outline(width = 97.0, height = 20.0, seed = 3L)
        val topEdge = outline.drop(2).dropLast(2)
        assertEquals(97.0, topEdge.last().x, 1e-9)
    }

    @Test
    fun aBlockNarrowerThanOneSegmentStillProducesAValidOutline() {
        val outline = RoughBlock.outline(width = 2.0, height = 10.0, seed = 5L, minSegmentLength = 5.0, maxSegmentLength = 12.0)
        assertEquals(2.0, outline[outline.size - 2].x, 1e-9)
    }
}

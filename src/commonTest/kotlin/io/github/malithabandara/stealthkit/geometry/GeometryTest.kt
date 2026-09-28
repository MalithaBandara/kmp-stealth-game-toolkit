package io.github.malithabandara.stealthkit.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeometryTest {
    @Test
    fun vec2dBasicOps() {
        val a = Vec2d(3.0, 4.0)
        assertEquals(5.0, a.length(), 1e-9)
        assertEquals(Vec2d(1.0, 1.0), Vec2d(0.0, 0.0).let { it + Vec2d(1.0, 1.0) })
        assertEquals(0.0, Vec2d.ZERO.length(), 1e-9)
    }

    @Test
    fun rectIntersectsDetectsOverlapAndSeparation() {
        val a = Rect(0.0, 0.0, 10.0, 10.0)
        val overlapping = Rect(5.0, 5.0, 10.0, 10.0)
        val separate = Rect(20.0, 20.0, 10.0, 10.0)
        assertTrue(a.intersects(overlapping))
        assertFalse(a.intersects(separate))
    }

    @Test
    fun hasLineOfSightIsBlockedByAnOccluderBetweenTheEndpointsOnly() {
        val occluder = Rect(40.0, 0.0, 20.0, 100.0)
        // A wall directly between origin and target blocks sight.
        assertFalse(GeometryUtils.hasLineOfSight(Vec2d(0.0, 50.0), Vec2d(100.0, 50.0), listOf(occluder)))
        // The same wall does not block a shorter ray that stops before reaching it.
        assertTrue(GeometryUtils.hasLineOfSight(Vec2d(0.0, 50.0), Vec2d(30.0, 50.0), listOf(occluder)))
        // Nor a ray entirely past it in the other direction.
        assertTrue(GeometryUtils.hasLineOfSight(Vec2d(70.0, 50.0), Vec2d(100.0, 50.0), listOf(occluder)))
    }

    @Test
    fun castRayStopsAtTheNearestOccluderFace() {
        val occluder = Rect(40.0, 0.0, 20.0, 100.0)
        val hit = GeometryUtils.castRay(Vec2d(0.0, 50.0), 0.0, 200.0, listOf(occluder))
        assertEquals(40.0, hit.x, 1e-6)
    }

    @Test
    fun castRayReachesFullRangeWithNoOccluders() {
        val hit = GeometryUtils.castRay(Vec2d(0.0, 0.0), 0.0, 100.0, emptyList())
        assertEquals(100.0, hit.x, 1e-6)
    }
}

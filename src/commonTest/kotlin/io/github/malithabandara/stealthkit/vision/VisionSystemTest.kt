package io.github.malithabandara.stealthkit.vision

import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.geometry.Vec2d
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisionSystemTest {
    @Test
    fun spottedWhenTargetIsInRangeAndFovWithNoOccluders() {
        val distance = VisionSystem.getPlayerSpottedDistance(
            eye = Vec2d(0.0, 0.0),
            facingAngle = 0.0,
            visionRange = 200.0,
            visionFov = PI / 2.0,
            targetPoints = listOf(Vec2d(100.0, 0.0)),
            occluders = emptyList()
        )
        assertNotNull(distance)
    }

    @Test
    fun notSpottedWhenOutsideRange() {
        val distance = VisionSystem.getPlayerSpottedDistance(
            eye = Vec2d(0.0, 0.0),
            facingAngle = 0.0,
            visionRange = 50.0,
            visionFov = PI / 2.0,
            targetPoints = listOf(Vec2d(100.0, 0.0)),
            occluders = emptyList()
        )
        assertNull(distance)
    }

    @Test
    fun notSpottedWhenOutsideFov() {
        val distance = VisionSystem.getPlayerSpottedDistance(
            eye = Vec2d(0.0, 0.0),
            facingAngle = 0.0,
            visionRange = 200.0,
            visionFov = PI / 8.0, // narrow cone straight ahead
            targetPoints = listOf(Vec2d(0.0, 100.0)), // directly below, outside the cone
            occluders = emptyList()
        )
        assertNull(distance)
    }

    @Test
    fun notSpottedWhenAnOccluderBlocksLineOfSight() {
        val wall = Rect(40.0, -50.0, 10.0, 100.0)
        val distance = VisionSystem.getPlayerSpottedDistance(
            eye = Vec2d(0.0, 0.0),
            facingAngle = 0.0,
            visionRange = 200.0,
            visionFov = PI / 2.0,
            targetPoints = listOf(Vec2d(100.0, 0.0)),
            occluders = listOf(wall)
        )
        assertNull(distance)
    }

    @Test
    fun spottedIfAnyOneTargetPointIsExposedEvenIfOthersAreOccluded() {
        // A low wall (y in -10..10) hides a point level with it (y=0) but a ray to a point well
        // above it (y=-100) clears over the top of the wall entirely.
        val wall = Rect(40.0, -10.0, 10.0, 20.0)
        val distance = VisionSystem.getPlayerSpottedDistance(
            eye = Vec2d(0.0, 0.0),
            facingAngle = 0.0,
            visionRange = 300.0,
            visionFov = PI, // wide enough to see both points
            targetPoints = listOf(Vec2d(100.0, 0.0), Vec2d(100.0, -100.0)),
            occluders = listOf(wall)
        )
        assertNotNull(distance)
    }

    @Test
    fun visionPolygonStartsAtTheOriginAndEveryVertexIsWithinRange() {
        val polygon = VisionSystem.computeVisionPolygon(
            origin = Vec2d(0.0, 0.0),
            facingAngle = 0.0,
            range = 100.0,
            fov = PI / 2.0,
            occluders = emptyList(),
            sampleCount = 8
        )
        assertTrue(polygon.first() == Vec2d(0.0, 0.0))
        for (p in polygon) {
            assertTrue(p.length() <= 100.0 + 1e-6)
        }
    }
}

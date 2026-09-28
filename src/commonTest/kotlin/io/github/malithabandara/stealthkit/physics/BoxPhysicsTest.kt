package io.github.malithabandara.stealthkit.physics

import io.github.malithabandara.stealthkit.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoxPhysicsTest {
    @Test
    fun aFallingBoxComesToRestOnAStaticFloorAndDoesNotSinkThroughIt() {
        val box = RigidBox(width = 20.0, height = 20.0, cx = 0.0, cy = -200.0)
        val floor = BoxObstacle(rect = Rect(-500.0, 0.0, 1000.0, 50.0))
        val supports = HashSet<BoxObstacle>()

        var settled = false
        var t = 0.0
        while (t < 5.0 && !settled) {
            BoxPhysics.step(box, dt = 1.0 / 60.0, obstacles = listOf(floor), supports = supports)
            settled = BoxPhysics.settleIfResting(
                box, dt = 1.0 / 60.0,
                supported = supports.isNotEmpty(),
                supportTop = if (supports.isNotEmpty()) floor.rect.top else null
            )
            t += 1.0 / 60.0
        }

        assertTrue(settled, "box should have come to rest within 5 seconds")
        assertTrue(box.isAsleep)
        // Resting on top of the floor, not sunk into it.
        val bottom = box.aabb().bottom
        assertEquals(floor.rect.top, bottom, 0.5)
    }

    @Test
    fun stepReportsNoContactWhenNothingIsNearby() {
        val box = RigidBox(width = 20.0, height = 20.0, cx = 0.0, cy = 0.0)
        val touched = BoxPhysics.step(box, dt = 1.0 / 60.0, obstacles = emptyList())
        assertTrue(!touched)
    }

    @Test
    fun aSleepingBoxIsNoLongerAdvancedByStep() {
        val box = RigidBox(width = 20.0, height = 20.0, cx = 0.0, cy = 0.0)
        box.isAsleep = true
        val before = box.cy
        BoxPhysics.step(box, dt = 1.0, obstacles = emptyList())
        assertEquals(before, box.cy, 1e-9)
    }

    @Test
    fun impulsesOnAMovableObstacleAreReportedToTheCaller() {
        // Box starts already half-overlapping the obstacle's top face, guaranteeing contact on
        // the very first substep rather than depending on how far gravity moves it in one frame.
        val box = RigidBox(width = 20.0, height = 20.0, cx = 0.0, cy = 0.0)
        val cart = Any()
        val movable = BoxObstacle(rect = Rect(-500.0, 0.0, 1000.0, 50.0), invMass = 1.0, owner = cart)
        var impulseSeen = false
        BoxPhysics.step(box, dt = 1.0 / 60.0, obstacles = listOf(movable)) { owner, _ ->
            if (owner === cart) impulseSeen = true
        }
        assertTrue(impulseSeen)
    }
}

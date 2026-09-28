package io.github.malithabandara.stealthkit.platforms

import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.physics.BoxObstacle
import io.github.malithabandara.stealthkit.physics.BoxPhysics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HookCrateTest {
    private fun hangingBounds(hookX: Double, hookY: Double, w: Double, h: Double, ropeLength: Double) =
        Rect(hookX - w / 2.0, hookY + ropeLength, w, h)

    @Test
    fun aNonPhysicalCrateFallsStraightDownAtAConstantAccelerationAndStopsAtTheGround() {
        val bounds = Rect(0.0, 0.0, 20.0, 20.0)
        val crate = HookCrate(id = "c1", hook = Rect(0.0, -10.0, 4.0, 4.0), bounds = bounds)
        crate.detach()
        assertTrue(crate.isDetached)

        var t = 0.0
        while (t < 5.0 && !crate.isLanded) {
            crate.update(dt = 1.0 / 60.0, gravity = 800.0, groundY = 300.0)
            t += 1.0 / 60.0
        }
        assertTrue(crate.isLanded)
        assertEquals(300.0, crate.bounds.bottom, 1e-6)
    }

    @Test
    fun aNonPhysicalCrateThatIsNotDetachedDoesNotFall() {
        val bounds = Rect(0.0, 0.0, 20.0, 20.0)
        val crate = HookCrate(id = "c1", hook = Rect(0.0, -10.0, 4.0, 4.0), bounds = bounds)
        crate.update(dt = 1.0, gravity = 800.0, groundY = 300.0)
        assertEquals(0.0, crate.bounds.y, 1e-9)
    }

    @Test
    fun aSweepingRigCarriesAHangingCrateBackAndForth() {
        val w = 20.0
        val h = 20.0
        val hook = Rect(-w / 2.0, -10.0, 4.0, 4.0)
        val crate = HookCrate(
            id = "c1", hook = hook, bounds = hangingBounds(0.0, -10.0, w, h, ropeLength = 30.0),
            ropeLength = 30.0, sweepX = 100.0, sweepPeriodSeconds = 4.0
        )
        val startX = crate.bounds.x
        crate.advanceSweep(dt = 2.0) // half period -> far end of the sweep
        assertTrue(crate.bounds.x > startX + 90.0) // should have moved ~100 units right
    }

    @Test
    fun aPhysicalCrateSwingsAsAPendulumWhenTheRigAccelerates() {
        val w = 20.0
        val h = 20.0
        val crate = HookCrate(
            id = "c1", hook = Rect(-w / 2.0, -10.0, 4.0, 4.0),
            bounds = hangingBounds(0.0, -10.0, w, h, ropeLength = 40.0),
            ropeLength = 40.0, sweepX = 80.0, sweepPeriodSeconds = 3.0, physical = true
        )
        assertEquals(0.0, crate.swingAngle, 1e-9)
        repeat(120) { crate.advanceSweep(dt = 1.0 / 60.0) }
        // The rig setting off should have pulled the pendulum out of dead-vertical.
        assertTrue(kotlin.math.abs(crate.swingAngle) > 0.001)
    }

    @Test
    fun detachingAPhysicalCrateHandsOffToARigidBodyCarryingItsSwingVelocity() {
        val w = 20.0
        val h = 20.0
        val crate = HookCrate(
            id = "c1", hook = Rect(-w / 2.0, -10.0, 4.0, 4.0),
            bounds = hangingBounds(0.0, -10.0, w, h, ropeLength = 40.0),
            ropeLength = 40.0, physical = true
        )
        assertNull(crate.body)
        // Give it some swing rate directly (as advanceSweep would have, over time).
        repeat(30) { crate.advanceSweep(dt = 1.0 / 60.0) } // settles, still ~0 without a sweep
        crate.detach()
        assertNotNull(crate.body)
        assertTrue(crate.isDetached)
    }

    @Test
    fun aDetachedPhysicalCrateTumblesAndLandsViaBoxPhysics() {
        val w = 20.0
        val h = 20.0
        val crate = HookCrate(
            id = "c1", hook = Rect(-w / 2.0, -200.0, 4.0, 4.0),
            bounds = hangingBounds(0.0, -200.0, w, h, ropeLength = 10.0),
            ropeLength = 10.0, physical = true
        )
        crate.detach()
        val body = crate.body!!
        val floor = BoxObstacle(rect = Rect(-500.0, 0.0, 1000.0, 50.0))

        var settled = false
        var t = 0.0
        while (t < 5.0 && !settled) {
            BoxPhysics.step(body, dt = 1.0 / 60.0, obstacles = listOf(floor))
            crate.syncBodyBounds()
            settled = BoxPhysics.settleIfResting(body, dt = 1.0 / 60.0, supported = crate.bounds.bottom >= floor.rect.top - 1.0, supportTop = floor.rect.top)
            t += 1.0 / 60.0
        }
        assertTrue(crate.bounds.bottom <= floor.rect.top + 1.0)
    }

    @Test
    fun resetReturnsAPhysicalCrateToItsHangingState() {
        val w = 20.0
        val h = 20.0
        val crate = HookCrate(
            id = "c1", hook = Rect(-w / 2.0, -10.0, 4.0, 4.0),
            bounds = hangingBounds(0.0, -10.0, w, h, ropeLength = 40.0),
            ropeLength = 40.0, physical = true
        )
        crate.detach()
        assertTrue(crate.isDetached)
        crate.reset()
        assertTrue(!crate.isDetached)
        assertNull(crate.body)
        assertEquals(0.0, crate.swingAngle, 1e-9)
    }
}

package io.github.malithabandara.stealthkit.follow

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmoothFollowTest {
    @Test
    fun snapToTeleportsAndClearsVelocity() {
        val f = SmoothFollow()
        f.snapTo(50.0)
        assertEquals(50.0, f.position, 1e-9)
        assertEquals(0.0, f.velocity, 1e-9)
    }

    @Test
    fun graduallyApproachesAStationaryTargetWithoutOvershooting() {
        val f = SmoothFollow(smoothTime = 0.1)
        f.snapTo(0.0)
        var previousError = 100.0
        repeat(120) {
            f.update(target = 100.0, dt = 1.0 / 60.0)
            val error = abs(100.0 - f.position)
            assertTrue(error <= previousError + 1e-6, "error should never increase for a stationary target")
            previousError = error
        }
        assertTrue(abs(100.0 - f.position) < 1.0)
    }

    @Test
    fun settlesToTrackingTheTargetsOwnVelocityAtASteadyLag() {
        val f = SmoothFollow(smoothTime = 0.12)
        f.snapTo(0.0)
        var targetX = 0.0
        val targetVelocity = 130.0
        val dt = 1.0 / 60.0

        // Run well past the settling transient (several multiples of smoothTime).
        repeat(120) {
            targetX += targetVelocity * dt
            f.update(target = targetX, dt = dt)
        }
        // Once settled, the follow's own velocity should match the target's, and stay essentially
        // constant frame to frame - the whole point of a second-order follow over a plain lerp.
        assertTrue(abs(f.velocity - targetVelocity) < 1.0)
        val settledVelocity = f.velocity
        repeat(30) {
            targetX += targetVelocity * dt
            f.update(target = targetX, dt = dt)
            assertTrue(abs(f.velocity - settledVelocity) < 1.0)
        }
    }

    @Test
    fun snapIfFartherThanTreatsALargeJumpAsATeleportNotAPan() {
        val f = SmoothFollow()
        f.snapTo(0.0)
        f.update(target = 10_000.0, dt = 1.0 / 60.0, snapIfFartherThan = 500.0)
        assertEquals(10_000.0, f.position, 1e-9)
        assertEquals(0.0, f.velocity, 1e-9)
    }

    @Test
    fun ignoresNonFiniteInputsInsteadOfCorruptingState() {
        val f = SmoothFollow()
        f.snapTo(42.0)
        f.update(target = Double.NaN, dt = 1.0 / 60.0)
        assertEquals(42.0, f.position, 1e-9)
        f.update(target = 43.0, dt = -1.0)
        assertEquals(42.0, f.position, 1e-9)
    }
}

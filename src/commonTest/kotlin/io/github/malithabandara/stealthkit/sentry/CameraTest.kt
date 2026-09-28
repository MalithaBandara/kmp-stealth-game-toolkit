package io.github.malithabandara.stealthkit.sentry

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraTest {
    @Test
    fun sweepsBetweenMinAndMaxAndReverses() {
        val cam = Camera.createSweeping(x = 0.0, y = 0.0, sweepSpeed = 1.0, sweepAngleDelta = 0.5)
        val min = cam.minAngle
        val max = cam.maxAngle
        var sawMax = false
        repeat(500) {
            cam.update(dt = 0.05)
            assertTrue(cam.currentAngle in min - 1e-6..max + 1e-6)
            if (cam.currentAngle >= max - 1e-6) sawMax = true
        }
        assertTrue(sawMax)
    }

    @Test
    fun holdsAtTheSweepEndForThePauseDurationInsteadOfImmediatelyReversingMotion() {
        val cam = Camera.createSweeping(x = 0.0, y = 0.0, sweepSpeed = 10.0, sweepAngleDelta = 0.1)
            .copy(sweepPauseDuration = 1.0)
        // One big step slams it into the max end and starts the dwell.
        cam.update(dt = 1.0)
        assertEquals(cam.maxAngle, cam.currentAngle, 1e-6)
        // Still within the dwell: the angle must not have started moving back yet.
        cam.update(dt = 0.5)
        assertEquals(cam.maxAngle, cam.currentAngle, 1e-6)
        // Once the dwell fully elapses, motion resumes (now heading back toward minAngle). The
        // exact frame the timer reaches zero still just consumes the remainder and returns, so a
        // further update is needed to see the angle actually move.
        cam.update(dt = 1.0) // consumes the last of the dwell
        cam.update(dt = 0.01) // now actually rotating again
        assertTrue(cam.currentAngle < cam.maxAngle)
    }

    @Test
    fun detectionPausesRotationUntilVisualIsLost() {
        val cam = Camera.createSweeping(x = 0.0, y = 0.0, sweepSpeed = 1.0, sweepAngleDelta = 0.5)
        cam.onPlayerSpotted()
        val angleWhenSpotted = cam.currentAngle
        repeat(10) { cam.update(dt = 0.1) }
        assertEquals(angleWhenSpotted, cam.currentAngle, 1e-9)

        cam.onVisualLost()
        // Still in its post-detection pause window, not yet resumed.
        cam.update(dt = 0.1)
        assertEquals(angleWhenSpotted, cam.currentAngle, 1e-9)
    }

    @Test
    fun degenerateSweepHoldsAtMinAngle() {
        val cam = Camera(x = 0.0, y = 0.0, minAngle = PI / 2.0, maxAngle = PI / 2.0, currentAngle = PI / 2.0)
        cam.update(dt = 1.0)
        assertEquals(PI / 2.0, cam.currentAngle, 1e-9)
    }
}

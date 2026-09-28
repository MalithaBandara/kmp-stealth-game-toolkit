package io.github.malithabandara.stealthkit.hazards

import io.github.malithabandara.stealthkit.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaserTest {
    @Test
    fun cyclesBetweenActiveAndInactiveOnItsPeriod() {
        val laser = Laser(id = "l1", topX = 0.0, topY = 0.0, activeDuration = 2.0, inactiveDuration = 1.0)
        laser.update(totalElapsedSeconds = 0.0)
        assertTrue(laser.isActive)
        laser.update(totalElapsedSeconds = 1.9)
        assertTrue(laser.isActive)
        laser.update(totalElapsedSeconds = 2.1)
        assertFalse(laser.isActive)
        laser.update(totalElapsedSeconds = 3.0) // wraps back into the active window
        assertTrue(laser.isActive)
    }

    @Test
    fun disableIsPermanentAcrossFurtherUpdates() {
        val laser = Laser(id = "l1", topX = 0.0, topY = 0.0, mechanismId = "switch1")
        laser.disable()
        assertTrue(laser.isDisabled)
        assertFalse(laser.isActive)
        laser.update(totalElapsedSeconds = 100.0) // would otherwise be well inside an active window
        assertFalse(laser.isActive)
        laser.reset()
        assertFalse(laser.isDisabled)
    }

    @Test
    fun isAlwaysActiveIgnoresTheCycleEntirely() {
        val laser = Laser(id = "l1", topX = 0.0, topY = 0.0, isAlwaysActive = true, inactiveDuration = 100.0)
        laser.update(totalElapsedSeconds = 50.0)
        assertTrue(laser.isActive)
    }

    @Test
    fun rejectsATiltPastFortyFiveDegrees() {
        assertFailsWith<IllegalArgumentException> {
            Laser(id = "bad", topX = 0.0, topY = 0.0, bottomX = 200.0, bottomY = 100.0)
        }
    }

    @Test
    fun intersectsPlayerAccountsForBeamThicknessAndOnlyWhenActive() {
        val laser = Laser(id = "l1", topX = 50.0, topY = 0.0, bottomX = 50.0, bottomY = 100.0, beamThickness = 4.0, isAlwaysActive = true)
        laser.update(0.0)
        val touchingBeam = Rect(48.0, 40.0, 4.0, 10.0)
        assertTrue(laser.intersectsPlayer(touchingBeam))

        val farAway = Rect(200.0, 40.0, 4.0, 10.0)
        assertFalse(laser.intersectsPlayer(farAway))
    }

    @Test
    fun timingHelpersAgreeWithTheActiveWindow() {
        val laser = Laser(id = "l1", topX = 0.0, topY = 0.0, activeDuration = 2.0, inactiveDuration = 1.0)
        assertEquals(2.0, laser.timeUntilInactive(0.0), 1e-9)
        assertEquals(0.0, laser.remainingInactiveTime(0.0), 1e-9)
        assertEquals(1.0, laser.remainingInactiveTime(2.0), 1e-9)
    }
}

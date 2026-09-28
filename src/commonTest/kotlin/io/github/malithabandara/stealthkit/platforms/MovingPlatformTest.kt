package io.github.malithabandara.stealthkit.platforms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MovingPlatformTest {
    @Test
    fun oscillatesBetweenMinAndMaxOnItsPeriod() {
        val p = MovingPlatform(id = "p1", y = 0.0, width = 40.0, height = 10.0, minX = 0.0, maxX = 100.0, periodSeconds = 4.0)
        p.update(dt = 0.0, totalElapsedSeconds = 0.0)
        assertEquals(0.0, p.x, 1e-6) // t=0 -> rest position
        p.update(dt = 0.0, totalElapsedSeconds = 2.0) // half period -> far end
        assertEquals(100.0, p.x, 1e-6)
        p.update(dt = 0.0, totalElapsedSeconds = 4.0) // full period -> back at rest
        assertEquals(0.0, p.x, 1e-6)
    }

    @Test
    fun gatedPlatformStaysParkedUntilActivated() {
        val p = MovingPlatform(
            id = "p1", y = 0.0, width = 40.0, height = 10.0, minX = 0.0, maxX = 100.0,
            periodSeconds = 4.0, startsInactive = true
        )
        val disp = p.update(dt = 1.0, totalElapsedSeconds = 1.0)
        assertEquals(0.0, disp.dx, 1e-9)
        assertEquals(0.0, p.x, 1e-9)

        p.activate()
        // Runs its own local clock from t=0 the moment it's activated.
        p.update(dt = 2.0, totalElapsedSeconds = 999.0)
        assertEquals(100.0, p.x, 1e-6)
    }

    @Test
    fun oneShotPlaysExactlyOnePeriodThenReArms() {
        val p = MovingPlatform(
            id = "p1", y = 0.0, width = 40.0, height = 10.0, minX = 0.0, maxX = 100.0,
            periodSeconds = 2.0, startsInactive = true, oneShot = true
        )
        p.activate()
        p.update(dt = 2.0, totalElapsedSeconds = 0.0) // exactly one full period
        assertEquals(0.0, p.x, 1e-6) // settled back at rest
        assertTrue(!p.isActive) // re-armed, needs another activate() to move again

        // A stray update before re-activation is a no-op.
        val disp = p.update(dt = 1.0, totalElapsedSeconds = 0.0)
        assertEquals(0.0, disp.dx, 1e-9)
    }

    @Test
    fun resetReturnsToTheInitialParkedState() {
        val p = MovingPlatform(id = "p1", y = 0.0, width = 40.0, height = 10.0, minX = 0.0, maxX = 100.0, periodSeconds = 4.0)
        p.update(dt = 0.0, totalElapsedSeconds = 2.0)
        assertEquals(100.0, p.x, 1e-6)
        p.reset()
        assertEquals(p.initialX, p.x, 1e-9)
    }
}

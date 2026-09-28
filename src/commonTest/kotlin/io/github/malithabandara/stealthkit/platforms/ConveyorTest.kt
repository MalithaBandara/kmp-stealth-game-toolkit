package io.github.malithabandara.stealthkit.platforms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConveyorTest {
    @Test
    fun crateDriftsByExactlyTheGivenDelta() {
        val crate = ConveyorCrate(initialX = 0.0, initialY = 0.0, width = 20.0, height = 20.0)
        crate.update(dx = 5.0)
        assertEquals(5.0, crate.x, 1e-9)
        crate.update(dx = 5.0)
        assertEquals(10.0, crate.x, 1e-9)
    }

    @Test
    fun loopingCrateWrapsAroundInsteadOfDriftingOffForever() {
        val crate = ConveyorCrate(
            initialX = 90.0, initialY = 0.0, width = 20.0, height = 20.0,
            loopMinX = 0.0, loopMaxX = 100.0, shouldLoop = true
        )
        crate.update(dx = 20.0) // pushes it past loopMaxX
        assertTrue(crate.x < 90.0) // wrapped back around, not sitting past 100
    }

    @Test
    fun patrollingCrateBouncesBetweenItsOwnBounds() {
        val crate = ConveyorCrate(
            initialX = 0.0, initialY = 0.0, width = 20.0, height = 20.0,
            isPatrol = true, patrolMinX = 0.0, patrolMaxX = 40.0
        )
        // Push past patrolMaxX in one step - it should clamp there and reverse.
        crate.update(dx = 100.0)
        assertEquals(20.0, crate.x, 1e-9) // patrolMaxX - width
        assertTrue(crate.speedMultiplier < 0.0)
    }

    @Test
    fun verticalBobFollowsACosineCycleBetweenMinAndMaxY() {
        val crate = ConveyorCrate(
            initialX = 0.0, initialY = 0.0, width = 20.0, height = 20.0,
            minY = 0.0, maxY = 100.0, verticalPeriodSeconds = 4.0
        )
        crate.update(dx = 0.0, totalElapsedSeconds = 0.0) // phase 0 -> maxY
        assertEquals(100.0, crate.y, 1e-6)
        crate.update(dx = 0.0, totalElapsedSeconds = 2.0) // half period -> minY
        assertEquals(0.0, crate.y, 1e-6)
    }

    @Test
    fun resetReturnsToTheInitialState() {
        val crate = ConveyorCrate(initialX = 0.0, initialY = 0.0, width = 20.0, height = 20.0)
        crate.update(dx = 50.0)
        crate.reset()
        assertEquals(0.0, crate.x, 1e-9)
    }
}

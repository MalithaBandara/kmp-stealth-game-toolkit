package io.github.malithabandara.stealthkit.actors

import io.github.malithabandara.stealthkit.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuardTest {
    private fun guard() = Guard(x = 0.0, y = 0.0, patrolMinX = 0.0, patrolMaxX = 100.0, speed = 50.0)

    @Test
    fun patrolWalksForwardAndTurnsAroundAtTheFarEnd() {
        val g = guard()
        repeat(200) { g.update(dt = 0.1) }
        assertEquals(GuardState.PATROL, g.state)
        assertTrue(g.x <= g.patrolMaxX + 1e-6)
        assertTrue(g.x >= g.patrolMinX - 1e-6)
    }

    @Test
    fun patrolTurnsAroundAtAnObstacleBeforeReachingTheRouteEnd() {
        val g = guard()
        val obstacle = Rect(30.0, -100.0, 20.0, 200.0)
        repeat(50) { g.update(dt = 0.1, obstacles = listOf(obstacle)) }
        // Never allowed to cross into the obstacle.
        assertTrue(g.x + g.width <= obstacle.left + 1e-6)
    }

    @Test
    fun noiseSendsTheGuardToInvestigateThenBackToPatrol() {
        val g = guard()
        g.onNoiseHeard(noiseX = 40.0)
        assertEquals(GuardState.INVESTIGATING, g.state)
        assertTrue(g.investigatedFromNoise)

        // Investigation times out and returns to patrol.
        repeat(60) { g.update(dt = 0.1) }
        assertEquals(GuardState.PATROL, g.state)
        assertTrue(!g.investigatedFromNoise)
    }

    @Test
    fun visualLossStartsAnInvestigationNotFlaggedAsFromNoise() {
        val g = guard()
        g.onVisualLost(lastSeenX = 80.0)
        assertEquals(GuardState.INVESTIGATING, g.state)
        assertTrue(!g.investigatedFromNoise)
        assertEquals(80.0, g.targetInvestigateX, 1e-9)
    }

    @Test
    fun patrolPauseHoldsAtThePostBeforeTurning() {
        val g = Guard(
            x = 95.0, y = 0.0, patrolMinX = 0.0, patrolMaxX = 100.0,
            speed = 50.0, facing = 1.0, patrolPauseDuration = 1.0
        )
        g.update(dt = 0.2) // reaches patrolMaxX and starts the dwell
        assertEquals(100.0, g.x, 1e-6)
        assertEquals(1.0, g.facing, 1e-9) // still facing the way it arrived, not yet turned
        repeat(15) { g.update(dt = 0.1) } // dwell elapses
        assertEquals(-1.0, g.facing, 1e-9)
    }
}

package io.github.malithabandara.stealthkit.progress

import io.github.malithabandara.stealthkit.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckpointProgressTest {
    private fun body(x: Double, y: Double = 370.0) = Rect(x, y, 22.0, 44.0)

    @Test
    fun manualCheckpointsAreSecuredInOrderAndNeverBackwards() {
        val cps = listOf(Checkpoint(x = 500.0, y = 370.0, id = "a"), Checkpoint(x = 1000.0, y = 370.0, id = "b"))
        val p = CheckpointProgress(cps, startX = 40.0, startY = 370.0)
        assertFalse(p.update(body(250.0), isSafe = true))
        assertTrue(p.update(body(495.0), isSafe = true))
        assertEquals(0, p.currentManualCheckpointIndex)
        assertEquals(500.0, p.lastCheckpointX, 1e-9)
        assertTrue(p.update(body(995.0), isSafe = true))
        assertFalse(p.update(body(495.0), isSafe = true))
        assertEquals(1000.0, p.lastCheckpointX, 1e-9)
    }

    @Test
    fun onlySecuredWhileSafe() {
        val p = CheckpointProgress(listOf(Checkpoint(x = 500.0, y = 370.0)), startX = 40.0, startY = 370.0)
        assertFalse(p.update(body(495.0), isSafe = false))
        assertFalse(p.hasAdvancedCheckpoint)
    }

    @Test
    fun anExplicitTriggerZoneReplacesTheDefaultBox() {
        val cp = Checkpoint(x = 500.0, y = 370.0, triggerZone = Rect(300.0, 0.0, 10.0, 500.0))
        val p = CheckpointProgress(listOf(cp), startX = 40.0, startY = 370.0)
        assertFalse(p.update(body(495.0), isSafe = true))
        assertTrue(p.update(body(295.0), isSafe = true))
        assertEquals(500.0, p.lastCheckpointX, 1e-9) // still respawns at the checkpoint itself
    }

    @Test
    fun withoutManualCheckpointsItCheckpointsEveryFewHundredUnits() {
        val p = CheckpointProgress(startX = 40.0, startY = 370.0)
        assertFalse(p.update(body(280.0), isSafe = true))
        assertTrue(p.update(body(300.0), isSafe = true))
        assertEquals(300.0, p.lastCheckpointX, 1e-9)
        assertFalse(p.update(body(540.0), isSafe = true))
        assertTrue(p.update(body(560.0), isSafe = true))
    }

    @Test
    fun resetGoesBackToTheStart() {
        val p = CheckpointProgress(startX = 40.0, startY = 370.0)
        p.update(body(400.0), isSafe = true)
        p.reset()
        assertEquals(40.0, p.lastCheckpointX, 1e-9)
        assertEquals(-1, p.currentManualCheckpointIndex)
        assertFalse(p.hasAdvancedCheckpoint)
    }
}

package io.github.malithabandara.stealthkit.mechanisms

import io.github.malithabandara.stealthkit.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeverTest {
    private val lever = Lever(id = "l", x = 100.0, y = 388.0) // centre x 111, base at 400

    private fun playerAt(centerX: Double, feetY: Double) = Rect(centerX - 11.0, feetY - 44.0, 22.0, 44.0)

    @Test
    fun inRangeWhenCloseAcrossWithFeetNearItsBase() {
        assertTrue(lever.isPlayerInRange(playerAt(140.0, 400.0)))
        assertTrue(lever.isPlayerInRange(playerAt(111.0, 430.0)))
    }

    @Test
    fun outOfRangeWhenTooFarAcrossOrAtTheWrongHeight() {
        assertFalse(lever.isPlayerInRange(playerAt(160.0, 400.0)))
        assertFalse(lever.isPlayerInRange(playerAt(111.0, 300.0)))
    }

    @Test
    fun resetUnthrowsIt() {
        val l = lever.copy(isActivated = true)
        l.reset()
        assertFalse(l.isActivated)
    }
}

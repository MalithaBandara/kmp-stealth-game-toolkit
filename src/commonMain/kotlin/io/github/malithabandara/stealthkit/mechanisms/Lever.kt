package io.github.malithabandara.stealthkit.mechanisms

import io.github.malithabandara.stealthkit.geometry.Rect
import kotlin.math.abs

/**
 * An interactive in-world lever that activates mechanisms when the player uses the interact button nearby.
 *
 * [targetMechanismId] names what it drives; the caller does the wiring when it's thrown - typically
 * disabling every [io.github.malithabandara.stealthkit.hazards.Laser] whose `mechanismId` matches,
 * activating a gated [io.github.malithabandara.stealthkit.platforms.MovingPlatform] with that id, and
 * detaching a [io.github.malithabandara.stealthkit.platforms.HookCrate] with that id.
 */
data class Lever(
    val id: String,
    val x: Double,
    val y: Double,
    val width: Double = 22.0,
    val height: Double = 12.0,
    var isActivated: Boolean = false,
    val targetMechanismId: String? = null,
    val interactRadius: Double = 40.0
) {
    val bounds: Rect get() = Rect(x, y, width, height)
    val centerX: Double get() = x + width / 2.0
    val centerY: Double get() = y + height / 2.0

    /** Whether a player occupying [playerBounds] can reach it: close enough across, feet near its base. */
    fun isPlayerInRange(playerBounds: Rect): Boolean {
        val dx = abs(playerBounds.centerX - centerX)
        val feetY = playerBounds.y + playerBounds.height
        val leverBottomY = y + height
        val dy = abs(feetY - leverBottomY)
        return dx <= interactRadius && dy <= 32.0
    }

    /** Un-throws it - a level restart. */
    fun reset() {
        isActivated = false
    }
}

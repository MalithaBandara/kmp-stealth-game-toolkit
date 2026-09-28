package io.github.malithabandara.stealthkit.actors

import io.github.malithabandara.stealthkit.geometry.GeometryUtils
import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.geometry.Vec2d

/** How loud the player is being, as a hearing radius. The game uses SILENT while crouched or still, NORMAL while walking. */
enum class NoiseLevel(val radius: Double) {
    SILENT(0.0),
    LOW(100.0),
    NORMAL(180.0),
    HIGH(280.0)
}

/**
 * Movement noise detection, as in Infiltrate's GameWorld: a guard hears the player if he is
 * within [noiseRadius] of them and nothing solid is in between - blocked by occluders, the same
 * as vision line-of-sight. Each guard that hears turns to investigate [playerX].
 */
object Noise {
    /** Returns the guards that heard. A radius of 0 (silent, or stealth boots) alerts nobody. */
    fun alertGuards(
        playerCenter: Vec2d,
        playerX: Double,
        noiseRadius: Double,
        guards: List<Guard>,
        occluders: List<Rect>
    ): List<Guard> {
        if (noiseRadius <= 0.0) return emptyList()
        val heard = ArrayList<Guard>()
        for (g in guards) {
            val distToGuard = playerCenter.distanceTo(g.center)
            if (distToGuard <= noiseRadius &&
                GeometryUtils.hasLineOfSight(playerCenter, g.center, occluders)
            ) {
                g.onNoiseHeard(playerX)
                heard += g
            }
        }
        return heard
    }
}

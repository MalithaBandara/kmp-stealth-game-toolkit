package io.github.malithabandara.stealthkit.platforms

import io.github.malithabandara.stealthkit.geometry.Rect
import kotlin.math.PI
import kotlin.math.cos

/**
 * A conveyor belt.
 *
 * [bounds]: Collision and platform rectangle.
 * [speed]: Surface movement speed (positive = moves rightward, negative = moves leftward).
 */
data class ConveyorDef(
    val bounds: Rect,
    val speed: Double = 80.0
)

/**
 * Declarative definition of a crate resting on and transported by a conveyor belt.
 */
data class ConveyorCrateDef(
    val initialX: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val loopMinX: Double = -width,
    val loopMaxX: Double = 5000.0,
    val shouldLoop: Boolean = false,
    val isHanging: Boolean = false,
    val isVariant1: Boolean = false,
    val speedMultiplier: Double = if (isHanging) DEFAULT_HANGING_SPEED_MULTIPLIER else 1.0,
    val isPatrol: Boolean = false,
    val patrolMinX: Double = initialX,
    val patrolMaxX: Double = initialX,
    val minY: Double = y,
    val maxY: Double = y,
    val verticalPeriodSeconds: Double = 0.0,
    val verticalPhaseOffsetSeconds: Double = 0.0
) {
    companion object {
        const val DEFAULT_HANGING_SPEED_MULTIPLIER = 1.0
    }
}

/**
 * Runtime state of a crate moving dynamically with a conveyor belt.
 */
class ConveyorCrate(
    val initialX: Double,
    initialY: Double,
    val width: Double,
    val height: Double,
    val loopMinX: Double = -width,
    val loopMaxX: Double = 5000.0,
    val shouldLoop: Boolean = false,
    val isHanging: Boolean = false,
    val isVariant1: Boolean = false,
    speedMultiplier: Double = if (isHanging) DEFAULT_HANGING_SPEED_MULTIPLIER else 1.0,
    val isPatrol: Boolean = false,
    val patrolMinX: Double = initialX,
    val patrolMaxX: Double = initialX,
    val minY: Double = initialY,
    val maxY: Double = initialY,
    val verticalPeriodSeconds: Double = 0.0,
    val verticalPhaseOffsetSeconds: Double = 0.0
) {
    companion object {
        const val DEFAULT_HANGING_SPEED_MULTIPLIER = ConveyorCrateDef.DEFAULT_HANGING_SPEED_MULTIPLIER
    }

    val initialY: Double = initialY
    var y: Double = initialY
        private set

    val initialSpeedMultiplier: Double = speedMultiplier
    var speedMultiplier: Double = speedMultiplier
        private set

    var x: Double = initialX
        private set

    val bounds: Rect get() = Rect(x, y, width, height)

    val top: Double get() = y
    val bottom: Double get() = y + height
    val left: Double get() = x
    val right: Double get() = x + width

    var isMovingDown: Boolean = false
        private set
    var vy: Double = 0.0
        private set

    /**
     * Moves the crate by [dx] - the belt's own travel this frame times [speedMultiplier] (a patrolling
     * crate flips the multiplier's sign at each bound) - and applies the vertical bob for level time
     * [totalElapsedSeconds].
     */
    fun update(dx: Double, totalElapsedSeconds: Double = 0.0) {
        x += dx
        if (isPatrol) {
            if (dx > 0.0 && x + width >= patrolMaxX) {
                x = patrolMaxX - width
                speedMultiplier = -speedMultiplier
            } else if (dx < 0.0 && x <= patrolMinX) {
                x = patrolMinX
                speedMultiplier = -speedMultiplier
            }
        } else if (shouldLoop && dx < 0.0 && x + width < loopMinX) {
            val loopSpan = loopMaxX - loopMinX
            x += loopSpan
        } else if (shouldLoop && dx > 0.0 && x > loopMaxX) {
            val loopSpan = loopMaxX - loopMinX
            x -= loopSpan
        }

        val oldY = y
        if (minY != maxY && verticalPeriodSeconds > 0.0) {
            val phase = ((totalElapsedSeconds + verticalPhaseOffsetSeconds) % verticalPeriodSeconds) / verticalPeriodSeconds
            val normalized = if (phase < 0.0) phase + 1.0 else phase
            val t = 0.5 + 0.5 * cos(2.0 * PI * normalized)
            y = minY + (maxY - minY) * t
        }
        val deltaY = y - oldY
        isMovingDown = deltaY > 1e-4
        vy = deltaY
    }

    /** Back to where it started - a level restart. */
    fun reset() {
        x = initialX
        y = initialY
        speedMultiplier = initialSpeedMultiplier
        isMovingDown = false
        vy = 0.0
    }
}

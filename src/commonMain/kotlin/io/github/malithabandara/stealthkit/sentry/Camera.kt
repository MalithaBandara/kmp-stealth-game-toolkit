package io.github.malithabandara.stealthkit.sentry

import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.geometry.Vec2d
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A sweeping security camera: swings between two angles on a timer, optionally pausing at each
 * end, and pauses its sweep entirely while it has the player in view.
 */
data class Camera(
    var x: Double,
    var y: Double,
    val width: Double = 20.0,
    val height: Double = 20.0,
    var minAngle: Double = (90.0 - 30.0) * (PI / 180.0),
    var maxAngle: Double = (90.0 + 30.0) * (PI / 180.0),
    var currentAngle: Double = (90.0 - 30.0) * (PI / 180.0),
    var sweepSpeed: Double = 0.7, // radians per second
    var visionRange: Double = 240.0,
    var visionFov: Double = 45.0 * (PI / 180.0), // 45 degrees in radians
    var sweepDirection: Double = 1.0,
    /** Seconds spent holding at each end of the sweep before reversing. 0 reverses instantly. */
    val sweepPauseDuration: Double = 0.0,
    /** Seconds the camera stops rotating once it has spotted the player. */
    var detectionPauseDuration: Double = 2.5
) {
    val bounds: Rect get() = Rect(x, y, width, height)
    val center: Vec2d get() = Vec2d(x + width / 2.0, y + height / 2.0)

    /**
     * The mount's ball-joint pivot, in world space - fixed relative to (x, y) regardless of
     * [currentAngle], since only the body beyond the joint rotates. [NECK_LENGTH] is measured
     * straight down from the attach point.
     */
    val pivotPosition: Vec2d get() = Vec2d(x + width / 2.0, y + NECK_LENGTH)

    /**
     * The lens tip - [LENS_LENGTH] out from the joint along the CURRENT facing direction, not a
     * fixed offset, since the body is a rigid piece rotating around the joint. Vision starts here,
     * not at the joint or the mount, so a renderer that draws the lens at this point matches
     * exactly what the vision cone sees.
     */
    val eyePosition: Vec2d get() {
        val pivot = pivotPosition
        return Vec2d(pivot.x + cos(currentAngle) * LENS_LENGTH, pivot.y + sin(currentAngle) * LENS_LENGTH)
    }
    val facingAngle: Double get() = currentAngle

    /** Time left holding at a sweep end before reversing; 0 while actively sweeping. */
    var sweepPauseTimer: Double = 0.0
        private set

    /** Time left holding paused due to player detection; 0 while sweeping normally. */
    var detectionPauseTimer: Double = 0.0
        private set

    /** True if camera currently has eyes on player. */
    var isDetectingPlayer: Boolean = false
        private set

    val isPausedFromDetection: Boolean get() = isDetectingPlayer || detectionPauseTimer > 0.0

    /** Call every frame the camera sees the player: it stops sweeping, and stays stopped for [detectionPauseDuration] after. */
    fun onPlayerSpotted() {
        isDetectingPlayer = true
        if (detectionPauseDuration > 0.0) {
            detectionPauseTimer = detectionPauseDuration
        }
    }

    /** Call when it stops seeing the player. The post-detection pause then runs out before it sweeps again. */
    fun onVisualLost() {
        isDetectingPlayer = false
    }

    /** Forgets any detection and resumes sweeping at once. */
    fun resetDetectionPause() {
        isDetectingPlayer = false
        detectionPauseTimer = 0.0
    }

    /** Advances the sweep one frame - unless it is watching the player, or pausing after that or at a sweep end. */
    fun update(dt: Double) {
        if (minAngle >= maxAngle || sweepSpeed <= 0.0) {
            currentAngle = minAngle
            return
        }

        if (isDetectingPlayer) {
            return
        }

        if (detectionPauseTimer > 0.0) {
            detectionPauseTimer = (detectionPauseTimer - dt).coerceAtLeast(0.0)
            return
        }

        if (sweepPauseTimer > 0.0) {
            sweepPauseTimer = (sweepPauseTimer - dt).coerceAtLeast(0.0)
            return
        }

        currentAngle += sweepDirection * sweepSpeed * dt

        if (sweepDirection > 0.0 && currentAngle >= maxAngle) {
            currentAngle = maxAngle
            sweepDirection = -1.0
            if (sweepPauseDuration > 0.0) sweepPauseTimer = sweepPauseDuration
        } else if (sweepDirection < 0.0 && currentAngle <= minAngle) {
            currentAngle = minAngle
            sweepDirection = 1.0
            if (sweepPauseDuration > 0.0) sweepPauseTimer = sweepPauseDuration
        }
    }

    /** Back to the start of its sweep with no detection - a level restart. */
    fun reset() {
        currentAngle = minAngle
        sweepDirection = 1.0
        sweepPauseTimer = 0.0
        detectionPauseTimer = 0.0
        isDetectingPlayer = false
    }

    companion object {
        /** See [pivotPosition]. */
        const val NECK_LENGTH = 9.5

        /** See [eyePosition]. */
        const val LENS_LENGTH = 20.0

        /**
         * A camera sweeping [sweepAngleDelta] either side of [centerAngle] (radians; PI/2 looks straight down),
         * starting at the low end. ([x], [y]) is the mount.
         */
        fun createSweeping(
            x: Double,
            y: Double,
            centerAngle: Double = PI / 2.0,
            sweepAngleDelta: Double = 30.0 * (PI / 180.0),
            sweepSpeed: Double = 0.7,
            visionRange: Double = 240.0,
            visionFov: Double = 45.0 * (PI / 180.0),
            width: Double = 20.0,
            height: Double = 20.0,
            detectionPauseDuration: Double = 2.5
        ): Camera {
            val min = centerAngle - sweepAngleDelta
            val max = centerAngle + sweepAngleDelta
            return Camera(
                x = x,
                y = y,
                width = width,
                height = height,
                minAngle = min,
                maxAngle = max,
                currentAngle = min,
                sweepSpeed = sweepSpeed,
                visionRange = visionRange,
                visionFov = visionFov,
                sweepDirection = 1.0,
                detectionPauseDuration = detectionPauseDuration
            )
        }
    }
}

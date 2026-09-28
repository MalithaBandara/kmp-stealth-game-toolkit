package io.github.malithabandara.stealthkit.hazards

import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.geometry.Segment2d
import io.github.malithabandara.stealthkit.geometry.Vec2d
import kotlin.math.*

/**
 * Declarative definition of a laser hazard.
 *
 * Lasers originate from the top (e.g. a ceiling emitter, default [topY] = 150.0) and aim at the
 * bottom (e.g. a floor receiver, default [bottomY] = 414.0). Tilt angle from vertical strictly
 * satisfies: abs(tiltAngleDegrees) <= 45.0.
 *
 * [id]: Unique identifier for the laser.
 * [topX], [topY]: Origin point of the laser beam.
 * [bottomX], [bottomY]: Target point of the beam.
 * [beamThickness]: Visual and collision thickness of the laser beam.
 * [activeDuration]: Duration (in seconds) that the laser beam remains energized and dangerous.
 * [inactiveDuration]: Duration (in seconds) that the laser beam de-energizes and is safe to traverse.
 * [phaseOffsetSeconds]: Offset in seconds into the active/inactive cycle.
 * [isAlwaysActive]: If true, laser is continuously energized and never deactivates.
 */
data class LaserDef(
    val id: String,
    val topX: Double,
    val topY: Double = 150.0,
    val bottomX: Double = topX,
    val bottomY: Double = 414.0,
    val beamThickness: Double = 6.0,
    val activeDuration: Double = 2.0,
    val inactiveDuration: Double = 1.5,
    val phaseOffsetSeconds: Double = 0.0,
    val isAlwaysActive: Boolean = false,
    /**
     * Scales the drawn emitter/receiver housings at either end of the beam (1.0 is the reference
     * size). Purely visual - the beam's own collision is [beamThickness] and does not move.
     */
    val emitterScale: Double = 1.0,
    /**
     * Ties this beam to a switch: a caller-owned trigger whose id matches [mechanismId] can kill
     * every laser carrying it, permanently, by calling [Laser.disable]. Null (the default) means
     * nothing can turn it off and the only way past is its own on/off cycle.
     */
    val mechanismId: String? = null
) {
    /**
     * Tilt angle measured from vertical (0 degrees is straight down along +y axis).
     * Forward tilt (towards +x) is positive; backward tilt (-x) is negative.
     */
    val tiltAngleRadians: Double
        get() = atan2(bottomX - topX, bottomY - topY)

    val tiltAngleDegrees: Double
        get() = tiltAngleRadians * 180.0 / PI

    init {
        require(abs(tiltAngleDegrees) <= 45.0 + 1e-4) {
            "LaserDef '$id' tilt angle (${tiltAngleDegrees}°) exceeds maximum allowed 45° from vertical."
        }
    }

    val bounds: Rect
        get() {
            val minX = min(topX, bottomX) - beamThickness / 2.0
            val maxX = max(topX, bottomX) + beamThickness / 2.0
            val minY = min(topY, bottomY)
            val maxY = max(topY, bottomY)
            return Rect(minX, minY, maxX - minX, maxY - minY)
        }

    val x: Double get() = bounds.x
    val y: Double get() = bounds.y
    val width: Double get() = bounds.width
    val height: Double get() = bounds.height

    /**
     * Backwards-compatible secondary constructor for axis-aligned bounding rectangles.
     */
    constructor(
        id: String,
        x: Double,
        y: Double,
        width: Double,
        height: Double = 6.0,
        activeDuration: Double = 2.0,
        inactiveDuration: Double = 1.5,
        phaseOffsetSeconds: Double = 0.0,
        isAlwaysActive: Boolean = false
    ) : this(
        id = id,
        topX = x + width / 2.0,
        topY = y,
        bottomX = x + width / 2.0,
        bottomY = y + height,
        beamThickness = min(width, height).coerceAtLeast(4.0),
        activeDuration = activeDuration,
        inactiveDuration = inactiveDuration,
        phaseOffsetSeconds = phaseOffsetSeconds,
        isAlwaysActive = isAlwaysActive
    )
}

/**
 * Runtime simulator and collision state for a periodic laser hazard.
 */
class Laser(
    val id: String,
    val topX: Double,
    val topY: Double = 150.0,
    val bottomX: Double = topX,
    val bottomY: Double = 414.0,
    val beamThickness: Double = 6.0,
    val activeDuration: Double = 2.0,
    val inactiveDuration: Double = 1.5,
    val phaseOffsetSeconds: Double = 0.0,
    val isAlwaysActive: Boolean = false,
    /** See [LaserDef.emitterScale] - the drawn size of the housings, nothing to do with collision. */
    val emitterScale: Double = 1.0,
    /** See [LaserDef.mechanismId] - the switch, if any, that can kill this beam for good. */
    val mechanismId: String? = null
) {
    var isActive: Boolean = true
        private set

    /**
     * Cut by its switch and staying cut. Unlike the active/inactive cycle this is one-way for the
     * rest of the run (until [reset]), because it is a lever being thrown, not a timing window.
     */
    var isDisabled: Boolean = false
        private set

    /** Throws the switch. No-op on a laser that has no [mechanismId]. */
    fun disable() {
        isDisabled = true
        isActive = false
    }

    /**
     * Tilt angle measured from vertical (0 degrees is straight down along +y axis).
     * Forward tilt (towards +x) is positive; backward tilt (-x) is negative.
     */
    val tiltAngleRadians: Double
        get() = atan2(bottomX - topX, bottomY - topY)

    val tiltAngleDegrees: Double
        get() = tiltAngleRadians * 180.0 / PI

    init {
        require(abs(tiltAngleDegrees) <= 45.0 + 1e-4) {
            "Laser '$id' tilt angle (${tiltAngleDegrees}°) exceeds maximum allowed 45° from vertical."
        }
    }

    val beamSegment: Segment2d
        get() = Segment2d(Vec2d(topX, topY), Vec2d(bottomX, bottomY))

    val bounds: Rect
        get() {
            val minX = min(topX, bottomX) - beamThickness / 2.0
            val maxX = max(topX, bottomX) + beamThickness / 2.0
            val minY = min(topY, bottomY)
            val maxY = max(topY, bottomY)
            return Rect(minX, minY, maxX - minX, maxY - minY)
        }

    val x: Double get() = bounds.x
    val y: Double get() = bounds.y
    val width: Double get() = bounds.width
    val height: Double get() = bounds.height

    val top: Double get() = bounds.top
    val bottom: Double get() = bounds.bottom
    val left: Double get() = bounds.left
    val right: Double get() = bounds.right

    /**
     * Backwards-compatible secondary constructor for axis-aligned bounding rectangles.
     */
    constructor(
        id: String,
        x: Double,
        y: Double,
        width: Double,
        height: Double = 6.0,
        activeDuration: Double = 2.0,
        inactiveDuration: Double = 1.5,
        phaseOffsetSeconds: Double = 0.0,
        isAlwaysActive: Boolean = false
    ) : this(
        id = id,
        topX = x + width / 2.0,
        topY = y,
        bottomX = x + width / 2.0,
        bottomY = y + height,
        beamThickness = min(width, height).coerceAtLeast(4.0),
        activeDuration = activeDuration,
        inactiveDuration = inactiveDuration,
        phaseOffsetSeconds = phaseOffsetSeconds,
        isAlwaysActive = isAlwaysActive
    )

    /**
     * Accurate collision detection against a target bounding box, accounting for beam thickness.
     */
    fun intersectsPlayer(playerBounds: Rect): Boolean {
        if (!isActive) return false
        val halfThick = beamThickness / 2.0
        val expandedPlayer = Rect(
            x = playerBounds.x - halfThick,
            y = playerBounds.y - halfThick,
            width = playerBounds.width + beamThickness,
            height = playerBounds.height + beamThickness
        )
        return expandedPlayer.intersectsSegment(beamSegment)
    }

    /**
     * Updates laser energized state based on [totalElapsedSeconds].
     */
    fun update(totalElapsedSeconds: Double) {
        if (isDisabled) {
            isActive = false
            return
        }
        if (isAlwaysActive) {
            isActive = true
            return
        }
        val cycle = activeDuration + inactiveDuration
        if (cycle <= 0.0) {
            isActive = true
            return
        }
        val phase = ((totalElapsedSeconds + phaseOffsetSeconds) % cycle)
        val normalizedPhase = if (phase < 0.0) phase + cycle else phase
        isActive = normalizedPhase < activeDuration
    }

    fun remainingInactiveTime(totalElapsedSeconds: Double): Double {
        if (isAlwaysActive) return 0.0
        val cycle = activeDuration + inactiveDuration
        if (cycle <= 0.0) return 0.0
        val phase = ((totalElapsedSeconds + phaseOffsetSeconds) % cycle)
        val normalizedPhase = if (phase < 0.0) phase + cycle else phase
        return if (normalizedPhase >= activeDuration) cycle - normalizedPhase else 0.0
    }

    fun timeUntilInactive(totalElapsedSeconds: Double): Double {
        if (isAlwaysActive) return Double.POSITIVE_INFINITY
        val cycle = activeDuration + inactiveDuration
        if (cycle <= 0.0) return Double.POSITIVE_INFINITY
        val phase = ((totalElapsedSeconds + phaseOffsetSeconds) % cycle)
        val normalizedPhase = if (phase < 0.0) phase + cycle else phase
        return if (normalizedPhase < activeDuration) activeDuration - normalizedPhase else 0.0
    }

    fun reset() {
        isActive = true
        isDisabled = false
    }
}

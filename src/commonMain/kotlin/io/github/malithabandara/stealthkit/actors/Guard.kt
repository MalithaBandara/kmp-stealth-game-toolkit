package io.github.malithabandara.stealthkit.actors

import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.geometry.Vec2d
import kotlin.math.PI
import kotlin.math.abs

/** What a [Guard] is doing: walking his route, or stopped to look at something. */
enum class GuardState {
    PATROL,
    INVESTIGATING
}

/**
 * A patrolling sentry: walks a fixed route, turns around at obstacles or route ends, and can be
 * pulled into investigating a noise or a lost line of sight before returning to patrol.
 */
data class Guard(
    var x: Double,
    var y: Double,
    val width: Double = 26.0,
    val height: Double = 48.0,
    val patrolMinX: Double,
    val patrolMaxX: Double,
    var speed: Double = 70.0,
    var facing: Double = 1.0,
    var visionRange: Double = 260.0,
    var visionFov: Double = 60.0 * (PI / 180.0), // 60 degrees in radians
    var investigateDuration: Double = 2.5,
    var investigatePauseDuration: Double = 2.0,
    /**
     * How long the guard stands at each end of his patrol before turning back. 0 turns on the
     * spot the frame he arrives. With a pause he holds his arriving facing for the whole dwell -
     * useful for a post that should visibly watch one end of its route for a while.
     */
    val patrolPauseDuration: Double = 0.0,
    /**
     * Keeps him rooted at his spawn point - not patrolling, not even the initial walk off it -
     * until the caller flips something (e.g. the player performs some action) and calls
     * [startInvestigating] or otherwise moves him off PATROL. Useful for guaranteeing a guard is
     * standing at a specific post for a scripted early-game beat instead of leaving it to patrol
     * timing.
     */
    val holdUntilPlayerCrouches: Boolean = false,
    /**
     * Tilts the cone down from dead level, in radians - 0 keeps a perfectly horizontal
     * facingAngle. A guard perched up on something, meant to watch the ground below rather than
     * the far horizon, wants this positive.
     */
    var visionTilt: Double = 0.0
) {
    var state: GuardState = GuardState.PATROL
        private set

    var patrolFacing: Double = facing
        private set

    var targetInvestigateX: Double = x
        private set

    var isAtInvestigateTarget: Boolean = true
        private set

    var investigateTimer: Double = 0.0
        private set

    var investigatePauseTimer: Double = 0.0
        private set

    var investigatedFromNoise: Boolean = false
        private set

    /** Time left standing at a patrol post before turning back; 0 while walking. */
    var patrolPauseTimer: Double = 0.0
        private set

    /** The facing to take when the current post dwell ends. */
    private var facingAfterPause: Double = facing

    /**
     * True on a frame the guard actually moved along his route - useful for keying a walk
     * animation. False while dwelling at a post, investigating, blocked, or at speed 0.
     */
    var isWalking: Boolean = false
        private set

    val bounds: Rect get() = Rect(x, y, width, height)
    val center: Vec2d get() = Vec2d(x + width / 2.0, y + height / 2.0)

    // y grows downward, so a positive angle rotates toward the ground: tilting down from level
    // means moving *toward* PI/2 from either horizontal, i.e. adding it facing right and
    // subtracting it facing left.
    val facingAngle: Double
        get() = if (facing >= 0.0) visionTilt else PI - visionTilt

    /**
     * Where the vision cone starts: an eye/torch/lens held out at arm's length, not the body
     * centre. Positioned as a fixed fraction of [height] ahead of the centre column and above the
     * feet, so it matches wherever a renderer draws the equivalent detail on the sprite. Detection
     * and any drawn beam should both start here, so what's lit on screen matches what can see the
     * player.
     */
    val eyePosition: Vec2d
        get() = Vec2d(
            x + width / 2.0 + (if (facing >= 0.0) 1.0 else -1.0) * TORCH_AHEAD_PER_HEIGHT * height,
            y + height - TORCH_ABOVE_FEET_PER_HEIGHT * height
        )

    companion object {
        /** Eye/torch lens ahead of the body's centre column, as a fraction of the standing height. */
        const val TORCH_AHEAD_PER_HEIGHT = 0.29
        /** Eye/torch lens above the feet, as a fraction of the standing height. */
        const val TORCH_ABOVE_FEET_PER_HEIGHT = 0.56
    }

    /**
     * Stops and turns to look toward [targetX] for [investigateDuration] - e.g. after a sighting.
     * Investigating is look-only: the guard never leaves his spot. [moveTowards] is not used.
     */
    fun startInvestigating(targetX: Double, moveTowards: Boolean = false) {
        if (state == GuardState.PATROL) {
            patrolFacing = facing
        }
        state = GuardState.INVESTIGATING
        investigatedFromNoise = false
        targetInvestigateX = targetX
        isAtInvestigateTarget = true
        investigateTimer = 0.0
        investigatePauseTimer = 0.0
        if (targetInvestigateX > center.x) {
            facing = 1.0
        } else if (targetInvestigateX < center.x) {
            facing = -1.0
        }
    }

    /** Like [startInvestigating], but flagged [investigatedFromNoise] - call it when he hears the player. */
    fun onNoiseHeard(noiseX: Double) {
        if (state == GuardState.PATROL) {
            patrolFacing = facing
        }
        state = GuardState.INVESTIGATING
        investigatedFromNoise = true
        targetInvestigateX = noiseX
        isAtInvestigateTarget = true
        investigateTimer = 0.0
        investigatePauseTimer = 0.0
        if (noiseX > center.x) {
            facing = 1.0
        } else if (noiseX < center.x) {
            facing = -1.0
        }
    }

    /** The player slipped out of sight: look toward where they were last seen. */
    fun onVisualLost(lastSeenX: Double) {
        startInvestigating(lastSeenX, moveTowards = false)
    }

    /** Still seeing the player mid-investigation: re-aim at [playerX] and restart the investigation clock. */
    fun onPlayerSpottedWhileInvestigating(playerX: Double) {
        targetInvestigateX = playerX
        isAtInvestigateTarget = true
        investigateTimer = 0.0
        investigatedFromNoise = false
        if (targetInvestigateX > center.x) {
            facing = 1.0
        } else if (targetInvestigateX < center.x) {
            facing = -1.0
        }
    }

    /** Drops any investigation and resumes the patrol, facing back along the route. */
    fun returnToPatrol() {
        state = GuardState.PATROL
        investigatedFromNoise = false
        isAtInvestigateTarget = true
        investigateTimer = 0.0
        investigatePauseTimer = 0.0
        patrolPauseTimer = 0.0

        // Resume original route
        when {
            x <= patrolMinX -> {
                facing = 1.0
                patrolFacing = 1.0
            }
            x >= patrolMaxX -> {
                facing = -1.0
                patrolFacing = -1.0
            }
            else -> {
                facing = patrolFacing
            }
        }
    }

    /** Advances one frame: walks the route (turning at its ends or at any of [obstacles]), or counts down an investigation. */
    fun update(dt: Double, obstacles: List<Rect> = emptyList()) {
        isWalking = false
        when (state) {
            GuardState.PATROL -> updatePatrol(dt, obstacles)
            GuardState.INVESTIGATING -> updateInvestigating(dt)
        }
    }

    private fun updatePatrol(dt: Double, obstacles: List<Rect>) {
        if (patrolPauseTimer > 0.0) {
            patrolPauseTimer -= dt
            if (patrolPauseTimer <= 0.0) {
                patrolPauseTimer = 0.0
                facing = facingAfterPause
                patrolFacing = facing
            }
            return
        }

        val before = x
        val targetX = x + facing * speed * dt
        val blocker = obstacles.firstOrNull { it.intersects(Rect(targetX, y, width, height)) }

        x = if (blocker != null) {
            val clampedX = if (facing > 0.0) blocker.left - width else blocker.right
            facing = -facing
            clampedX
        } else {
            targetX
        }
        patrolFacing = facing

        if (facing > 0.0 && x >= patrolMaxX) {
            x = patrolMaxX
            turnAtPost(-1.0)
        } else if (facing < 0.0 && x <= patrolMinX) {
            x = patrolMinX
            turnAtPost(1.0)
        }
        isWalking = x != before
    }

    /** Reached the end of the route: turn now, or stand the dwell out first and turn after. */
    private fun turnAtPost(newFacing: Double) {
        if (patrolPauseDuration > 0.0) {
            patrolPauseTimer = patrolPauseDuration
            facingAfterPause = newFacing
        } else {
            facing = newFacing
            patrolFacing = newFacing
        }
    }

    private fun updateInvestigating(dt: Double) {
        investigateTimer += dt

        if (investigateTimer >= investigateDuration) {
            returnToPatrol()
        }
    }
}

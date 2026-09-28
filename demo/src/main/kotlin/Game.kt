import io.github.malithabandara.stealthkit.actors.Guard
import io.github.malithabandara.stealthkit.actors.GuardState
import io.github.malithabandara.stealthkit.actors.Noise
import io.github.malithabandara.stealthkit.actors.NoiseLevel
import io.github.malithabandara.stealthkit.follow.SmoothFollow
import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.geometry.Vec2d
import io.github.malithabandara.stealthkit.hazards.Laser
import io.github.malithabandara.stealthkit.layout.ScreenLayout
import io.github.malithabandara.stealthkit.layout.VirtualViewport
import io.github.malithabandara.stealthkit.mechanisms.Lever
import io.github.malithabandara.stealthkit.physics.BoxObstacle
import io.github.malithabandara.stealthkit.physics.BoxPhysics
import io.github.malithabandara.stealthkit.platforms.ConveyorCrate
import io.github.malithabandara.stealthkit.platforms.ConveyorDef
import io.github.malithabandara.stealthkit.platforms.HookCrate
import io.github.malithabandara.stealthkit.platforms.MovingPlatform
import io.github.malithabandara.stealthkit.platforms.PlatformDisplacement
import io.github.malithabandara.stealthkit.powerups.ActivePowerups
import io.github.malithabandara.stealthkit.powerups.PowerupType
import io.github.malithabandara.stealthkit.progress.Checkpoint
import io.github.malithabandara.stealthkit.progress.CheckpointProgress
import io.github.malithabandara.stealthkit.sentry.Camera
import io.github.malithabandara.stealthkit.terrain.RoughBlock
import io.github.malithabandara.stealthkit.vision.VisionSystem
import kotlin.math.PI
import kotlin.math.min

// World units are the library's: y grows downward, the level is authored against ScreenLayout's
// 1040x480 design canvas, and the main floor sits at GROUND_Y.

const val GROUND_Y = 414.0
const val LEVEL_END = 3300.0
const val CEILING_Y = 140.0

const val GRAVITY = 1800.0
const val JUMP_SPEED = 640.0
const val RUN_SPEED = 210.0
const val CROUCH_SPEED = 95.0
const val MAX_FALL_SPEED = 1200.0
const val STEP_UP = 16.0
const val PLAYER_W = 22.0
const val STAND_H = 44.0
const val CROUCH_H = 26.0

/** After a respawn, how long lasers can't hurt you and nobody can see you - the game's 3 s. */
const val RESPAWN_GRACE = 3.0
/** After the laser shield soaks a hit - the game's 1.2 s. */
const val SHIELD_GRACE = 1.2

fun deg(d: Double) = d * PI / 180.0

/** The gadgets on the demo's quick-slot, in key order. Checkpoints isn't fired - it's spent when you're caught. */
val QUICK_SLOT = listOf(
    PowerupType.SMOKE_SCREEN,
    PowerupType.LASER_SHIELD,
    PowerupType.INVISIBILITY,
    PowerupType.NOISE_SUPPRESSION,
    PowerupType.REMOTE_TRIGGER,
)

/** The game's own store descriptions (SMOKE_SCREEN's reworded to what it actually does). */
fun gadgetDescription(type: PowerupType): String = when (type) {
    PowerupType.SMOKE_SCREEN -> "Cameras stop sweeping and can't see you for 10 seconds."
    PowerupType.LASER_SHIELD -> "Protects from 1 laser contact."
    PowerupType.INVISIBILITY -> "Become invisible to guards and cameras for 10 seconds."
    PowerupType.NOISE_SUPPRESSION -> "Silent movement for entire mission."
    PowerupType.CHECKPOINTS -> "Respawn at activated checkpoints after being caught or restarting."
    PowerupType.REMOTE_TRIGGER -> "Triggers closest mechanism without needing to find its switch."
}

/** One frame of player intent. Held actions are levels; the rest fire on the frame they're pressed. */
data class Controls(
    val left: Boolean = false,
    val right: Boolean = false,
    val crouch: Boolean = false,
    val jump: Boolean = false,
    val interact: Boolean = false,
    /** A gadget fired from the quick-slot this frame. */
    val gadget: PowerupType? = null,
    val dismiss: Boolean = false,
    val restart: Boolean = false,
)

class Player(var x: Double, var y: Double) {
    var vy = 0.0
    var height = STAND_H
    var facing = 1.0
    var grounded = false
    var isWalking = false
    /** What the player is standing on, so moving surfaces can carry them next frame. */
    var standingOn: Any? = null

    val bounds: Rect get() = Rect(x, y, PLAYER_W, height)
    val center: Vec2d get() = Vec2d(x + PLAYER_W / 2.0, y + height / 2.0)
    val crouching: Boolean get() = height < STAND_H

    /** As in the game: silent while crouched or still, NORMAL while walking. */
    val noiseLevel: NoiseLevel get() = if (isWalking && !crouching) NoiseLevel.NORMAL else NoiseLevel.SILENT

    /** Changes height while keeping the feet planted. */
    fun resize(newHeight: Double) {
        y += height - newHeight
        height = newHeight
    }

    /** Head, both sides and feet - so a body half behind cover still counts once enough shows. */
    fun visibilityPoints(): List<Vec2d> {
        val b = bounds
        return listOf(
            Vec2d(b.centerX, b.top + 2.0),
            Vec2d(b.left + 2.0, b.centerY),
            Vec2d(b.right - 2.0, b.centerY),
            Vec2d(b.centerX, b.bottom - 2.0),
        )
    }
}

/** A stretch of the level with advice on getting past it - with and without gadgets. */
data class SectionHint(val fromX: Double, val toX: Double, val skill: String, val gadget: PowerupType?, val gadgetUse: String)

/**
 * The level. Everything that moves, sees, hurts, or tracks progress is a library type; this class
 * wires them together the way Infiltrate's GameWorld does, and owns the player's movement.
 */
class Game(
    /** The gadgets the player "bought" before the mission - one of each by default. */
    initialStock: Map<PowerupType, Int> = PowerupType.entries.associateWith { 1 },
) {
    // --- static geometry ---
    val groundBlocks = listOf(
        Rect(0.0, GROUND_Y, 900.0, 66.0),
        Rect(1100.0, GROUND_Y, 1500.0, 66.0),
    )
    val ledge = Rect(2600.0, 290.0, LEVEL_END - 2600.0, 190.0)
    val lowCover = Rect(440.0, 384.0, 40.0, 30.0)
    val tallCover = Rect(760.0, 370.0, 30.0, 44.0)
    val catwalk = Rect(490.0, 300.0, 310.0, 10.0)
    val pillar = Rect(1380.0, 330.0, 40.0, 84.0)
    val duct = Rect(2780.0, 190.0, 340.0, 12.0)
    val exitDoor = Rect(3200.0, 230.0, 40.0, 60.0)
    private val walls = listOf(Rect(-20.0, -400.0, 20.0, 900.0), Rect(LEVEL_END, -400.0, 20.0, 900.0))

    /** Drawing outlines only; collision stays on the plain rects (see RoughBlock's docs). */
    val roughOutlines: List<Pair<Rect, List<Vec2d>>> = (groundBlocks + ledge).map { block ->
        block to RoughBlock.outline(block.width, block.height, seed = block.x.toLong(), maxJitter = 2.5)
    }

    // --- platforms ---
    val lift = MovingPlatform(
        id = "lift", y = 384.0, width = 80.0, height = 14.0,
        minX = 905.0, maxX = 1020.0, periodSeconds = 4.5,
    )
    val belt = ConveyorDef(Rect(2000.0, 400.0, 300.0, 14.0), speed = 60.0)
    val beltCrates = List(3) { i ->
        ConveyorCrate(
            initialX = 2010.0 + i * 100.0, initialY = 370.0, width = 30.0, height = 30.0,
            loopMinX = belt.bounds.left, loopMaxX = belt.bounds.right, shouldLoop = true,
        )
    }
    val hookCrate = HookCrate(
        id = "load",
        hook = Rect(2440.0, 150.0, 20.0, 10.0),
        bounds = Rect(2425.0, 270.0, 50.0, 50.0),
        ropeLength = 110.0,
        sweepX = 90.0,
        sweepPeriodSeconds = 6.0,
        physical = true,
    )
    val hookRailLeft = 2430.0
    val hookRailRight = 2470.0 + hookCrate.sweepX

    // --- mechanisms ---
    val levers = listOf(
        Lever(id = "gate-lever", x = 1779.0, y = 402.0, targetMechanismId = "gate"),
        Lever(id = "hook-lever", x = 2359.0, y = 402.0, targetMechanismId = hookCrate.id),
    )

    // --- watchers ---
    val guards = listOf(
        Guard(
            x = 600.0, y = GROUND_Y - 48.0, patrolMinX = 490.0, patrolMaxX = 730.0,
            facing = -1.0, visionRange = 220.0, patrolPauseDuration = 0.8, investigateDuration = 3.0,
        ),
        Guard(
            x = 2950.0, y = ledge.top - 48.0, patrolMinX = 2800.0, patrolMaxX = 3080.0,
            speed = 60.0, facing = -1.0, visionRange = 200.0, patrolPauseDuration = 1.5, investigateDuration = 4.0,
        ),
    )
    val cameras = listOf(
        Camera.createSweeping(
            x = 1290.0, y = 180.0, sweepAngleDelta = deg(45.0), sweepSpeed = 0.6,
            visionRange = 225.0, visionFov = deg(36.0),
        ),
        Camera.createSweeping(
            x = 2130.0, y = 180.0, sweepAngleDelta = deg(25.0), sweepSpeed = 0.8,
            visionRange = 215.0, visionFov = deg(36.0),
        ),
    )

    // --- hazards ---
    val lasers = listOf(
        Laser(
            id = "sweep", topX = 1640.0, topY = 250.0, bottomX = 1690.0, bottomY = GROUND_Y,
            beamThickness = 5.0, activeDuration = 1.8, inactiveDuration = 1.4,
        ),
        Laser(
            id = "gate", topX = 1900.0, topY = 250.0, bottomY = GROUND_Y,
            isAlwaysActive = true, emitterScale = 1.3, mechanismId = "gate",
        ),
    )

    // --- gadgets ---
    val activePowerups = ActivePowerups()
    /** The player's stock of each gadget - the game keeps this in the profile, spent from the quick-slot. */
    private val stock: MutableMap<PowerupType, Int> = initialStock.toMutableMap()
    fun stockOf(type: PowerupType): Int = stock[type] ?: 0

    // --- checkpoints ---
    private val startX = 40.0 - PLAYER_W / 2
    private val startY = GROUND_Y - STAND_H
    val checkpoints = CheckpointProgress(
        manualCheckpoints = listOf(
            checkpointAt("lift", 1120.0, GROUND_Y),
            checkpointAt("gate", 1950.0, GROUND_Y),
            checkpointAt("ledge", 2640.0, ledge.top),
        ),
        startX = startX,
        startY = startY,
    )
    val currentCheckpointId: String
        get() = checkpoints.manualCheckpoints.getOrNull(checkpoints.currentManualCheckpointIndex)?.id ?: "start"

    // --- advice ---
    val sectionHints = listOf(
        SectionHint(300.0, 830.0, "Crouch near guards - walking is noisy. Climb the low crate to the catwalk and cross over him.",
            PowerupType.INVISIBILITY, "walk straight past him"),
        SectionHint(1100.0, 1500.0, "Cross while the camera looks the other way.",
            PowerupType.SMOKE_SCREEN, "freeze and blind every camera"),
        SectionHint(1500.0, 1720.0, "Wait for the laser to switch off.",
            PowerupType.LASER_SHIELD, "walk through it once"),
        SectionHint(1720.0, 1900.0, "Throw the switch (E) to kill the laser gate.",
            PowerupType.REMOTE_TRIGGER, "throw it from anywhere"),
        SectionHint(1930.0, 2320.0, "Ride the belt while the camera looks away.",
            PowerupType.SMOKE_SCREEN, "blind the camera"),
        SectionHint(2320.0, 2600.0, "Pull the lever to drop the crate - stand clear - push it to the wall and climb.",
            PowerupType.REMOTE_TRIGGER, "drop the crate from anywhere"),
        SectionHint(2600.0, 3300.0, "Cross the duct above the guard, then crouch out behind him.",
            PowerupType.NOISE_SUPPRESSION, "walk silently for the rest of the level"),
    )

    val player = Player(startX, startY)
    var time = 0.0
        private set
    var suspicion = 0.0
        private set
    var timesCaught = 0
        private set
    var won = false
        private set
    var restartRequested = false
        private set
    /** The start-of-mission briefing on how to use the gadgets; dismissed by any key. */
    var showBriefing = true
        private set
    var message: String? = null
        private set
    private var messageTimer = 0.0
    var hint: String? = null
        private set
    /** The last thing that caught the player - handy for tests and for the HUD. */
    var lastCaughtReason: String? = null
        private set
    /** Set for a moment after the remote trigger fires, so the renderer can draw the signal. */
    var remoteSignal: Pair<Vec2d, Vec2d>? = null
        private set
    private var remoteSignalTimer = 0.0
    private var laserGraceTimer = 0.0

    /** Which watchers currently have eyes on the player - also drives cone colours. */
    val guardSees = BooleanArray(guards.size)
    val cameraSees = BooleanArray(cameras.size)
    private var lastSeenX = 0.0

    // --- view ---
    var viewport = VirtualViewport(ScreenLayout.DESIGN_WIDTH, ScreenLayout.DESIGN_HEIGHT)
    private val follow = SmoothFollow(smoothTime = 0.25).apply { snapTo(0.0) }
    var cameraX = 0.0
        private set

    private val boxObstacles = (groundBlocks + ledge + lowCover + tallCover + pillar + belt.bounds + walls)
        .map { BoxObstacle(it) }

    val landedCrate: Rect?
        get() = hookCrate.body?.takeIf { it.isAsleep }?.let { hookCrate.bounds }

    private fun solids(): List<Rect> =
        groundBlocks + ledge + lowCover + tallCover + catwalk + pillar + duct + belt.bounds + walls +
            listOfNotNull(landedCrate)

    /** Everything that blocks sight and sound this frame, moving pieces included. */
    fun occluders(): List<Rect> =
        groundBlocks + ledge + lowCover + tallCover + catwalk + pillar + duct + belt.bounds +
            lift.bounds + beltCrates.map { it.bounds } + hookCrate.bounds

    /** The advice for where the player is standing. */
    val currentSection: SectionHint?
        get() = sectionHints.firstOrNull { player.bounds.centerX in it.fromX..it.toX }

    fun step(dt: Double, controls: Controls) {
        if (showBriefing) {
            if (controls.dismiss) showBriefing = false
            return
        }
        if (won) {
            if (controls.restart) restartRequested = true
            return
        }
        time += dt
        if (messageTimer > 0.0) {
            messageTimer -= dt
            if (messageTimer <= 0.0) message = null
        }
        if (remoteSignalTimer > 0.0) {
            remoteSignalTimer -= dt
            if (remoteSignalTimer <= 0.0) remoteSignal = null
        }
        laserGraceTimer = (laserGraceTimer - dt).coerceAtLeast(0.0)

        // World first, so the player rides this frame's platform motion.
        lasers.forEach { it.update(time) }
        val liftMove = lift.update(dt, time)
        val beltDx = belt.speed * dt
        beltCrates.forEach { it.update(beltDx * it.speedMultiplier, time) }
        updateHookCrate(dt)
        // Update cameras - paused while the Camera Jammer is active (as in the game)
        if (!activePowerups.isSmokeScreenActive) cameras.forEach { it.update(dt) }
        guards.forEachIndexed { i, g -> if (!guardSees[i]) g.update(dt, obstacles = listOf(lowCover, tallCover)) }
        activePowerups.update(dt)

        updatePlayer(dt, controls, liftMove, beltDx)
        handleActions(controls)
        if (won) return
        checkHazards()
        // Movement noise - Stealth Boots keep movement completely silent (as in the game)
        val effectiveNoiseRadius = if (activePowerups.isNoiseSuppressed) 0.0 else player.noiseLevel.radius
        Noise.alertGuards(player.center, player.x, effectiveNoiseRadius, guards, occluders())
        updateDetection(dt)

        val target = (player.bounds.centerX - viewport.width * 0.4)
            .coerceIn(0.0, (LEVEL_END - viewport.width).coerceAtLeast(0.0))
        cameraX = follow.update(target, dt, snapIfFartherThan = 600.0)
    }

    private fun updateHookCrate(dt: Double) {
        hookCrate.advanceSweep(dt)
        val body = hookCrate.body ?: return
        if (body.isAsleep) return
        val supports = HashSet<BoxObstacle>()
        BoxPhysics.step(body, dt, boxObstacles, supports)
        BoxPhysics.settleIfResting(body, dt, supports.isNotEmpty(), supports.minOfOrNull { it.rect.top })
        hookCrate.syncBodyBounds()
    }

    private fun updatePlayer(dt: Double, c: Controls, liftMove: PlatformDisplacement, beltDx: Double) {
        val p = player

        // Carried by whatever we stood on last frame.
        when (val on = p.standingOn) {
            lift -> { p.x += liftMove.dx; p.y += liftMove.dy }
            belt.bounds -> p.x += beltDx
            is ConveyorCrate -> p.x += beltDx * on.speedMultiplier
        }

        p.resize(if (c.crouch && p.grounded) CROUCH_H else STAND_H)

        val dir = (if (c.right) 1 else 0) - (if (c.left) 1 else 0)
        if (dir != 0) p.facing = dir.toDouble()
        val vx = dir * (if (p.crouching) CROUCH_SPEED else RUN_SPEED)
        p.isWalking = dir != 0 && p.grounded

        if (p.grounded && c.jump) {
            p.vy = -JUMP_SPEED
            p.grounded = false
        }
        p.vy = min(p.vy + GRAVITY * dt, MAX_FALL_SPEED)

        val solids = solids().toMutableList()
        val wasGrounded = p.grounded

        // Horizontal pass: step up small lips (the belt), shove the landed crate, otherwise push
        // out sideways.
        p.x += vx * dt
        val crate = landedCrate
        for ((i, s) in solids.withIndex()) {
            val b = p.bounds
            if (!b.intersects(s)) continue
            val lip = b.bottom - s.top
            val stepped = Rect(b.x, s.top - p.height, b.width, b.height)
            if (wasGrounded && lip in 0.0..STEP_UP && solids.none { it !== s && it.intersects(stepped) }) {
                p.y = s.top - p.height
            } else if (s == crate && wasGrounded) {
                val pushingRight = b.centerX < s.centerX
                val moved = shoveCrate(s, if (pushingRight) b.right - s.left else b.left - s.right, solids)
                p.x = if (pushingRight) moved.left - PLAYER_W else moved.right
                solids[i] = moved
            } else {
                p.x = if (b.centerX < s.centerX) s.left - PLAYER_W else s.right
            }
        }

        // Vertical pass: land on or bump into solids.
        val prevBottom = p.y + p.height
        p.y += p.vy * dt
        p.grounded = false
        p.standingOn = null
        for (s in solids) {
            if (!p.bounds.intersects(s)) continue
            if (p.vy >= 0.0) {
                p.y = s.top - p.height
                p.grounded = true
                p.standingOn = s
            } else {
                p.y = s.bottom
            }
            p.vy = 0.0
        }

        // One-way surfaces (only from above): the lift and the belt crates.
        if (p.vy >= 0.0) {
            val oneWay: List<Pair<Any, Rect>> = listOf<Pair<Any, Rect>>(lift to lift.bounds) +
                beltCrates.map { it to it.bounds }
            for ((owner, r) in oneWay) {
                val b = p.bounds
                val overlapsX = b.right > r.left && b.left < r.right
                if (overlapsX && prevBottom <= r.top + 2.0 && b.bottom >= r.top) {
                    p.y = r.top - p.height
                    p.vy = 0.0
                    p.grounded = true
                    p.standingOn = owner
                }
            }
        }

        if (p.y > 560.0) caught("You fell.")
    }

    /**
     * Slides the settled hook crate by up to [dx] along the floor, stopping flush against anything
     * solid in the way. Returns where it ended up.
     */
    private fun shoveCrate(crate: Rect, dx: Double, solids: List<Rect>): Rect {
        var allowed = dx
        for (o in solids) {
            if (o == crate || o.top >= crate.bottom || o.bottom <= crate.top) continue
            if (dx > 0 && o.left >= crate.right - 1e-6) allowed = minOf(allowed, o.left - crate.right)
            if (dx < 0 && o.right <= crate.left + 1e-6) allowed = maxOf(allowed, o.right - crate.left)
        }
        val body = hookCrate.body ?: return crate
        body.cx += allowed
        hookCrate.syncBodyBounds()
        return hookCrate.bounds
    }

    // --- gadgets, the way the game does it ------------------------------------------------------

    /** Activates a lever exactly as walking up and pressing interact would - shared by the normal
     *  in-range interact path and REMOTE_TRIGGER's remote one. (GameWorld.triggerLever) */
    private fun triggerLever(lever: Lever) {
        lever.isActivated = true
        if (lever.targetMechanismId != null) {
            if (hookCrate.id == lever.targetMechanismId) hookCrate.detach()
            for (laser in lasers) {
                if (laser.mechanismId == lever.targetMechanismId) laser.disable()
            }
        }
    }

    /** Whether REMOTE_TRIGGER has anything to do right now. (GameWorld.hasRemoteTriggerTarget) */
    fun hasRemoteTriggerTarget(): Boolean = levers.any { !it.isActivated }

    /** (GameWorld.activatePowerup) */
    fun activatePowerup(type: PowerupType): Boolean {
        if (won) return false
        // Already running - refuse rather than reset its clock/charges.
        if (activePowerups.isActive(type)) return false
        if (type == PowerupType.REMOTE_TRIGGER) {
            // One-shot: remotely throws the nearest lever the player hasn't already reached.
            val target = levers.filter { !it.isActivated }
                .minByOrNull { player.center.distanceTo(Vec2d(it.centerX, it.centerY)) }
                ?: return false
            triggerLever(target)
            remoteSignal = player.center to Vec2d(target.centerX, target.centerY)
            remoteSignalTimer = 0.6
            return true
        }
        activePowerups.activate(type)
        if (type == PowerupType.SMOKE_SCREEN) {
            for (c in cameras) c.resetDetectionPause()
        }
        return true
    }

    /** The quick-slot: refuse before spending, then spend and fire. (GameplayScene.tryActivatePowerup) */
    fun tryActivatePowerup(type: PowerupType): Boolean {
        if (won) return false
        // Refuse before spending the item - checking only inside activatePowerup would still burn
        // one for an activation that silently does nothing.
        if (activePowerups.isActive(type)) return false
        if (type == PowerupType.REMOTE_TRIGGER && !hasRemoteTriggerTarget()) return false
        if (stockOf(type) <= 0) return false
        stock[type] = stockOf(type) - 1
        activatePowerup(type)
        flash("${type.displayName}: ${gadgetDescription(type)}")
        return true
    }

    private fun handleActions(c: Controls) {
        val b = player.bounds
        val reachable = levers.firstOrNull { !it.isActivated && it.isPlayerInRange(b) }
        hint = when (reachable?.id) {
            "gate-lever" -> "E  throw the switch (kills the laser gate)"
            "hook-lever" -> "E  release the hook - stand clear, then climb the crate"
            else -> null
        }
        if (c.interact && reachable != null) triggerLever(reachable)

        c.gadget?.let(::tryActivatePowerup)

        // Safe checkpoint recording: only when grounded, unseen and with no alert building.
        val inVision = guardSees.any { it } || cameraSees.any { it }
        if (checkpoints.update(b, isSafe = player.grounded && !inVision && suspicion == 0.0)) {
            flash("Checkpoint secured.")
        }

        if (b.intersects(exitDoor)) {
            won = true
            message = null
        }
    }

    private fun checkHazards() {
        val b = player.bounds
        if (laserGraceTimer <= 0.0) {
            for (laser in lasers) {
                if (!activePowerups.isInvisibilityActive && laser.intersectsPlayer(b)) {
                    if (activePowerups.isLaserShieldActive) {
                        activePowerups.consumeLaserShield()
                        laserGraceTimer = SHIELD_GRACE
                        flash("Shield absorbed the laser.")
                        break
                    }
                    caught("Tripped a laser.")
                    return
                }
            }
        }
        val body = hookCrate.body
        if (body != null && !body.isAsleep && body.vy > 150.0 && hookCrate.bounds.intersects(b)) {
            caught("Flattened by a falling crate.")
        }
    }

    private fun updateDetection(dt: Double) {
        val points = player.visibilityPoints()
        val occluders = occluders()
        val px = player.bounds.centerX
        var fill = 0.0

        // Invisibility: player cannot be spotted by any guard or camera
        val invisible = activePowerups.isInvisibilityActive
        guards.forEachIndexed { i, guard ->
            val dist = if (invisible) null else VisionSystem.getPlayerSpottedDistance(guard, points, occluders)
            if (dist != null) {
                if (guard.state == GuardState.PATROL) guard.startInvestigating(px)
                else guard.onPlayerSpottedWhileInvestigating(px)
                lastSeenX = px
                fill = maxOf(fill, 0.5 + 1.5 * (1.0 - dist / guard.visionRange))
            } else if (guardSees[i]) {
                guard.onVisualLost(lastSeenX)
            }
            guardSees[i] = dist != null
        }

        // Cameras vision checks - skipped while the Camera Jammer is active
        cameras.forEachIndexed { i, camera ->
            val blind = invisible || activePowerups.isSmokeScreenActive
            val dist = if (blind) null else VisionSystem.getPlayerSpottedDistance(camera, points, occluders)
            if (dist != null) {
                camera.onPlayerSpotted()
                fill = maxOf(fill, 0.6 + 1.2 * (1.0 - dist / camera.visionRange))
            } else if (cameraSees[i]) {
                camera.onVisualLost()
            }
            cameraSees[i] = dist != null
        }

        suspicion = if (fill > 0.0) suspicion + fill * dt else (suspicion - 0.5 * dt).coerceAtLeast(0.0)
        if (suspicion >= 1.0) caught("Spotted!")
    }

    private fun flash(text: String) {
        message = text
        messageTimer = 2.5
    }

    /**
     * Caught. With Checkpoints active you go back to the last checkpoint with a moment of grace
     * (GameWorld.respawnAtCheckpoint); without, the level starts over. Being caught spends a
     * Checkpoints from stock if there is one - the game's RESPAWN button.
     */
    private fun caught(reason: String) {
        timesCaught++
        lastCaughtReason = reason
        if (!activePowerups.isCheckpointsActive && stockOf(PowerupType.CHECKPOINTS) > 0) {
            stock[PowerupType.CHECKPOINTS] = stockOf(PowerupType.CHECKPOINTS) - 1
            activePowerups.activate(PowerupType.CHECKPOINTS)
        }
        val toCheckpoint = activePowerups.isCheckpointsActive
        if (!toCheckpoint) {
            checkpoints.reset()
            activePowerups.reset()
        }
        flash(if (toCheckpoint) "$reason  Back to the last checkpoint." else "$reason  Starting over.")

        suspicion = 0.0
        player.height = STAND_H
        player.x = checkpoints.lastCheckpointX
        player.y = checkpoints.lastCheckpointY
        player.vy = 0.0
        player.grounded = false
        player.standingOn = null

        for (g in guards) g.returnToPatrol()
        for (cam in cameras) cam.reset()
        lift.reset()
        for (crate in beltCrates) crate.reset()
        for (laser in lasers) laser.reset()
        for (lever in levers) lever.reset()
        hookCrate.reset()
        guardSees.fill(false)
        cameraSees.fill(false)
        if (toCheckpoint) {
            activePowerups.invisibilityTimer = RESPAWN_GRACE
            laserGraceTimer = RESPAWN_GRACE
        }
    }

    private companion object {
        /** A checkpoint whose player spawns centred on [centerX], with a tall trigger strip a jump can't clear. */
        fun checkpointAt(id: String, centerX: Double, floorY: Double) = Checkpoint(
            x = centerX - PLAYER_W / 2,
            y = floorY - STAND_H,
            triggerZone = Rect(centerX - 40.0, floorY - 200.0, 80.0, 200.0),
            id = id,
        )
    }
}

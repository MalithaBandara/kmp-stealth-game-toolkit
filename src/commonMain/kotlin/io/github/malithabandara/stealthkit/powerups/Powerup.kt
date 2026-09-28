package io.github.malithabandara.stealthkit.powerups

/**
 * The gadgets from Infiltrate: Shadow Heist's store. A timed gadget ([duration] > 0) runs down on
 * its own once fired; a level-duration one ([isLevelDuration]) stays on for the rest of the level
 * (the shield: until it has soaked up its one hit). [REMOTE_TRIGGER] has no duration at all - it is
 * a one-shot, and firing it IS its whole effect (see its doc).
 *
 * [ActivePowerups] tracks what's running; applying each effect is the caller's job - each entry
 * says what to check.
 */
enum class PowerupType(
    val id: String,
    val displayName: String,
    val shortName: String,
    val duration: Double,
    val defaultCost: Int
) {
    /** Cameras stop sweeping and can't see for 10 s: skip camera updates and camera detection while [ActivePowerups.isSmokeScreenActive]. */
    SMOKE_SCREEN(
        id = "smoke_screen",
        displayName = "CAMERA JAMMER",
        shortName = "JAMMER",
        duration = 10.0,
        defaultCost = 150
    ),

    /** Soaks up one laser (or other hazard) contact: call [ActivePowerups.consumeLaserShield] on a hit. */
    LASER_SHIELD(
        id = "laser_shield",
        displayName = "GUARD SHIELD",
        shortName = "SHIELD",
        duration = -1.0, // Level-duration until consumed by 1 hit
        defaultCost = 500
    ),

    /** Unseen by guards and cameras, and lasers pass through, for 10 s: check [ActivePowerups.isInvisibilityActive]. */
    INVISIBILITY(
        id = "invisibility",
        displayName = "INVISIBILITY CLOAK",
        shortName = "INVIS",
        duration = 10.0,
        defaultCost = 350
    ),

    /** Silent movement for the whole level: skip footstep noise while [ActivePowerups.isNoiseSuppressed]. */
    NOISE_SUPPRESSION(
        id = "noise_suppression",
        displayName = "STEALTH BOOTS",
        shortName = "STEALTH",
        duration = -1.0, // Level-duration
        defaultCost = 400
    ),

    /**
     * Respawn at the last checkpoint reached after being caught or restarting, instead of starting
     * the level over: check [ActivePowerups.isCheckpointsActive]. The game spends it from the
     * caught screen's respawn button rather than a quick-slot.
     */
    CHECKPOINTS(
        id = "checkpoints",
        displayName = "CHECKPOINTS",
        shortName = "CHECKPOINT",
        duration = -1.0, // Mission-wide
        defaultCost = 750
    ),

    /**
     * One-shot, no timer or charge of its own: throws the nearest lever the player hasn't reached
     * yet, exactly as if they'd walked up and pressed interact on it, without needing to be in
     * range. Check there's an unthrown lever before spending one.
     */
    REMOTE_TRIGGER(
        id = "remote_trigger",
        displayName = "REMOTE TRIGGER",
        shortName = "TRIGGER",
        duration = 0.0,
        defaultCost = 600
    );

    val isLevelDuration: Boolean get() = duration <= 0.0

    companion object {
        val STEALTH_BOOTS: PowerupType get() = NOISE_SUPPRESSION

        /** Resolves a gadget from its id, name, or any of the store's aliases (case-insensitive); null if none match. */
        fun fromId(id: String): PowerupType? {
            return when (id.lowercase().trim()) {
                "camera_jammer", "jammer", "smoke_screen", "smoke_bomb", "camera_disable", "smoke" -> SMOKE_SCREEN
                "guard_shield", "laser_shield", "laser_guard", "shield", "guard" -> LASER_SHIELD
                "invisibility", "invisibility_cloak", "invis" -> INVISIBILITY
                "noise_suppression", "stealth_boots", "silence", "boots", "stealth" -> NOISE_SUPPRESSION
                "checkpoints", "checkpoint", "tactical_checkpoint" -> CHECKPOINTS
                "remote_trigger", "trigger", "remote" -> REMOTE_TRIGGER
                else -> entries.firstOrNull {
                    it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true)
                }
            }
        }
    }
}

/**
 * The gadgets running during a level. [activate] starts one; [update] runs the timers down.
 *
 * The game refuses to fire a gadget that is already running (rather than resetting its clock), and
 * checks that before spending one from the player's stock - do the same with [isActive].
 */
data class ActivePowerups(
    var smokeScreenTimer: Double = 0.0,
    var laserShieldCharges: Int = 0,
    var invisibilityTimer: Double = 0.0,
    var isNoiseSuppressed: Boolean = false,
    var isCheckpointsActive: Boolean = false
) {
    val isSmokeScreenActive: Boolean get() = smokeScreenTimer > 0.0
    val isLaserShieldActive: Boolean get() = laserShieldCharges > 0
    val isInvisibilityActive: Boolean get() = invisibilityTimer > 0.0

    val anyActive: Boolean
        get() = isSmokeScreenActive || isLaserShieldActive || isInvisibilityActive || isNoiseSuppressed || isCheckpointsActive

    /** Starts [type]: a timed gadget gets its full [PowerupType.duration], the shield one charge, a level-duration gadget switches on. Does nothing for [PowerupType.REMOTE_TRIGGER]. */
    fun activate(type: PowerupType) {
        when (type) {
            PowerupType.SMOKE_SCREEN -> smokeScreenTimer = type.duration
            PowerupType.LASER_SHIELD -> laserShieldCharges = 1
            PowerupType.INVISIBILITY -> invisibilityTimer = type.duration
            PowerupType.NOISE_SUPPRESSION -> isNoiseSuppressed = true
            PowerupType.CHECKPOINTS -> isCheckpointsActive = true
            PowerupType.REMOTE_TRIGGER -> Unit
        }
    }

    /** Spends the shield on a hazard hit. True if it absorbed the hit. */
    fun consumeLaserShield(): Boolean {
        if (laserShieldCharges > 0) {
            laserShieldCharges--
            return true
        }
        return false
    }

    /** Runs the timed gadgets down by [dt]. */
    fun update(dt: Double) {
        if (smokeScreenTimer > 0.0) {
            smokeScreenTimer = (smokeScreenTimer - dt).coerceAtLeast(0.0)
        }
        if (invisibilityTimer > 0.0) {
            invisibilityTimer = (invisibilityTimer - dt).coerceAtLeast(0.0)
        }
    }

    /** Whether [type] is running. Always false for [PowerupType.REMOTE_TRIGGER], which has no state. */
    fun isActive(type: PowerupType): Boolean = when (type) {
        PowerupType.SMOKE_SCREEN -> isSmokeScreenActive
        PowerupType.LASER_SHIELD -> isLaserShieldActive
        PowerupType.INVISIBILITY -> isInvisibilityActive
        PowerupType.NOISE_SUPPRESSION -> isNoiseSuppressed
        PowerupType.CHECKPOINTS -> isCheckpointsActive
        PowerupType.REMOTE_TRIGGER -> false
    }

    /** Seconds left on a timed gadget; -1 for a level-duration one that's on; 0 when off. */
    fun getRemainingTime(type: PowerupType): Double = when (type) {
        PowerupType.SMOKE_SCREEN -> smokeScreenTimer
        PowerupType.LASER_SHIELD -> if (isLaserShieldActive) -1.0 else 0.0
        PowerupType.INVISIBILITY -> invisibilityTimer
        PowerupType.NOISE_SUPPRESSION -> if (isNoiseSuppressed) -1.0 else 0.0
        PowerupType.CHECKPOINTS -> if (isCheckpointsActive) -1.0 else 0.0
        PowerupType.REMOTE_TRIGGER -> 0.0
    }

    /** Everything off - a fresh level. */
    fun reset() {
        smokeScreenTimer = 0.0
        laserShieldCharges = 0
        invisibilityTimer = 0.0
        isNoiseSuppressed = false
        isCheckpointsActive = false
    }
}

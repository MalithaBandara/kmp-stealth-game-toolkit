package io.github.malithabandara.stealthkit.progress

import io.github.malithabandara.stealthkit.geometry.Rect

/**
 * A safe respawn checkpoint in a level.
 * When reached by a grounded player within [triggerZone], this checkpoint is secured.
 * If the player restarts or dies while the Checkpoints gadget is active, they respawn at ([x], [y])
 * (the player's top-left).
 */
data class Checkpoint(
    val x: Double,
    val y: Double,
    val triggerZone: Rect? = null,
    val id: String = ""
)

/**
 * Where a caught player respawns - the checkpoint bookkeeping from Infiltrate's GameWorld.
 *
 * With [manualCheckpoints], each one is secured (in order - never backwards) when the player
 * enters its trigger zone (by default a small box around its spawn point). With none, the level
 * checkpoints itself automatically: every time the player gets [AUTO_CHECKPOINT_SPACING] further
 * right than the last one, where they stand becomes the new one.
 */
class CheckpointProgress(
    val manualCheckpoints: List<Checkpoint> = emptyList(),
    val startX: Double,
    val startY: Double
) {
    var currentManualCheckpointIndex: Int = -1
        private set

    var lastCheckpointX: Double = startX
        private set
    var lastCheckpointY: Double = startY
        private set

    /** Whether any checkpoint past the start has been secured. */
    var hasAdvancedCheckpoint: Boolean = false
        private set

    /**
     * Call every frame. The game only secures a checkpoint when the operative is safe - grounded,
     * not in anyone's view, and with no alert building - so pass that as [isSafe]. Returns true on
     * the frame a checkpoint is secured.
     */
    fun update(playerBounds: Rect, isSafe: Boolean): Boolean {
        if (!isSafe) return false
        var secured = false
        if (manualCheckpoints.isEmpty()) {
            if (playerBounds.x > lastCheckpointX + AUTO_CHECKPOINT_SPACING) {
                lastCheckpointX = playerBounds.x
                lastCheckpointY = playerBounds.y
                hasAdvancedCheckpoint = true
                secured = true
            }
        } else {
            for (i in manualCheckpoints.indices) {
                val cp = manualCheckpoints[i]
                val zone = cp.triggerZone ?: Rect(cp.x - 20.0, cp.y - 20.0, 40.0, playerBounds.height + 40.0)
                if (playerBounds.intersects(zone) && i > currentManualCheckpointIndex) {
                    currentManualCheckpointIndex = i
                    lastCheckpointX = cp.x
                    lastCheckpointY = cp.y
                    hasAdvancedCheckpoint = true
                    secured = true
                }
            }
        }
        return secured
    }

    /** Back to the start - a full level restart. */
    fun reset() {
        currentManualCheckpointIndex = -1
        lastCheckpointX = startX
        lastCheckpointY = startY
        hasAdvancedCheckpoint = false
    }

    companion object {
        /** How far right of the last automatic checkpoint the player has to get to secure a new one. */
        const val AUTO_CHECKPOINT_SPACING = 250.0
    }
}

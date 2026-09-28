import io.github.malithabandara.stealthkit.powerups.PowerupType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Plays the demo level start to finish through the same [Game.step] + [Controls] path the window
 * uses, reacting to the level's state the way a player would - waiting for the lift, timing the
 * laser, reading a guard's patrol. If a level change makes it impossible to finish without
 * being caught, these fail and say where.
 */
class PlaythroughTest {
    private val dt = 1.0 / 60.0

    private class Run(val g: Game) {
        val log = StringBuilder()
    }

    private fun Run.px() = g.player.bounds.centerX

    private fun Run.step(c: Controls = Controls()) {
        g.step(dt, c)
        if (g.timesCaught > 0) fail("Caught (${g.lastCaughtReason}) at t=${"%.1f".format(g.time)}\n$log")
    }

    /** Steps with [controls] until [done], failing after [seconds]. */
    private fun Run.until(what: String, seconds: Double = 20.0, controls: () -> Controls = { Controls() }, done: () -> Boolean) {
        log.appendLine("t=${"%.2f".format(g.time)} x=${"%.0f".format(px())}: $what")
        var t = 0.0
        while (!done()) {
            step(controls())
            t += dt
            if (t > seconds) fail("Timed out: $what (x=${"%.0f".format(px())}, y=${"%.0f".format(g.player.y)})\n$log")
        }
    }

    private fun Run.walkTo(x: Double, crouch: Boolean = false) = if (x > px()) {
        until("walk right to $x", controls = { Controls(right = true, crouch = crouch) }) { px() >= x }
    } else {
        until("walk left to $x", controls = { Controls(left = true, crouch = crouch) }) { px() <= x }
    }

    private fun Run.wait(seconds: Double) {
        val end = g.time + seconds
        until("wait $seconds s") { g.time >= end }
    }

    /** Jumps holding right, and keeps holding right until landed. */
    private fun Run.jumpRight() {
        step(Controls(right = true, jump = true))
        until("jump right", controls = { Controls(right = true) }) { g.player.grounded }
    }

    /** Jumps right and lets go once over [targetX], to land on something narrow. */
    private fun Run.hopOnto(targetX: () -> Double) {
        step(Controls(right = true, jump = true))
        until("hop", controls = { Controls(right = px() < targetX()) }) { g.player.grounded }
    }

    private fun Run.use(type: PowerupType) {
        val before = g.stockOf(type)
        step(Controls(gadget = type))
        assertEquals(before - 1, g.stockOf(type), "$type should have fired")
    }

    // --- sections -----------------------------------------------------------------------------

    /** Over the first guard along the catwalk. */
    private fun Run.guardByCatwalk() {
        walkTo(g.lowCover.left - 20)
        hopOnto { g.lowCover.centerX }
        assertEquals(g.lowCover.top, g.player.y + g.player.height, 1e-6, "on the low crate")
        until("guard 1 walking away", seconds = 15.0) { g.guards[0].facing > 0 }
        jumpRight()
        assertEquals(g.catwalk.top, g.player.y + g.player.height, 1e-6, "on the catwalk")
        walkTo(g.catwalk.right - 5)
        until("drop off the catwalk", controls = { Controls(right = true) }) { g.player.grounded && px() > g.catwalk.right }
    }

    private fun Run.lift() {
        walkTo(880.0)
        until("lift comes to the near side") { g.lift.x <= 910.0 }
        hopOnto { g.lift.bounds.centerX }
        assertEquals(g.lift, g.player.standingOn, "riding the lift")
        until("lift reaches the far side") { g.lift.x >= 1015.0 }
        hopOnto { 1110.0 } // land just past the gap, outside the camera's reach
        assertEquals("lift", g.currentCheckpointId)
    }

    /** Past the first camera by timing its sweep, over the pillar. */
    private fun Run.cameraByTiming() {
        val cam = g.cameras[0]
        // Go as it swings back from the far right: it passes over you once, briefly.
        until("camera 1 at the right end") { cam.currentAngle <= cam.minAngle + 0.05 }
        until("reach the pillar", controls = { Controls(right = true) }) { g.player.bounds.right >= g.pillar.left - 25 }
        jumpRight()
        walkTo(1600.0)
    }

    private fun Run.laserByTiming() {
        val sweep = g.lasers[0]
        until("laser switches off") { !sweep.isActive && sweep.remainingInactiveTime(g.time) > 1.0 }
        walkTo(1720.0)
    }

    private fun Run.gateByLever() {
        walkTo(1790.0)
        step(Controls(interact = true))
        assertTrue(g.lasers[1].isDisabled, "gate laser off")
        walkTo(1975.0)
        assertEquals("gate", g.currentCheckpointId)
    }

    /** Along the belt under the second camera, timing its sweep. */
    private fun Run.beltByTiming() {
        val cam = g.cameras[1]
        until("camera 2 at the left end") { cam.currentAngle >= cam.maxAngle - 0.05 }
        walkTo(2330.0)
    }

    /** Drop the hook crate with its lever, get it against the ledge, climb up. */
    private fun Run.crateToLedge(dropDelay: Double, remote: Boolean = false) {
        walkTo(2335.0)
        wait(dropDelay)
        if (remote) use(PowerupType.REMOTE_TRIGGER) else step(Controls(interact = true))
        assertTrue(g.hookCrate.isDetached, "crate released")
        until("crate settles") { g.landedCrate != null }
        log.appendLine("crate settled at ${g.landedCrate}")
        if (g.hookCrate.bounds.right < g.ledge.left - 1.0) {
            // Too far from the wall to climb from: get behind it and shove it over.
            if (px() > g.hookCrate.bounds.left) {
                until("get behind the crate", controls = { Controls(left = true) }) { g.player.bounds.right < g.hookCrate.bounds.left - 60 }
            }
            until("shove the crate to the wall", controls = { Controls(right = true) }) {
                g.hookCrate.bounds.right >= g.ledge.left - 1e-6
            }
            walkTo(g.hookCrate.bounds.left - 60)
        }
        val crate = g.hookCrate.bounds
        until("guard 2 walking away", seconds = 30.0) { g.guards[1].facing > 0 && g.guards[1].x > 2850.0 }
        until("reach the crate", controls = { Controls(right = true) }) { g.player.bounds.right >= crate.left - 30 }
        hopOnto { crate.centerX }
        assertEquals(crate.top, g.player.y + g.player.height, 1e-6, "on the crate")
        jumpRight()
        assertEquals("ledge", g.currentCheckpointId)
    }

    /** Along the duct over the second guard, down behind him, out. */
    private fun Run.ductToExit(sneak: Boolean) {
        until("reach the duct", controls = { Controls(right = true) }) { px() >= g.duct.left - 40 }
        jumpRight()
        assertEquals(g.duct.top, g.player.y + g.player.height, 1e-6, "on the duct")
        walkTo(g.duct.right - 15)
        until("guard 2 heading back left", seconds = 30.0) { g.guards[1].facing < 0 && g.guards[1].x < 3000.0 }
        until("reach the exit", seconds = 5.0, controls = { Controls(right = true, crouch = sneak) }) { g.won }
    }

    private fun Run.start() {
        g.step(dt, Controls(dismiss = true)) // close the briefing
        until("land") { g.player.grounded }
    }

    // --- tests --------------------------------------------------------------------------------

    @Test
    fun beatableWithNoGadgetsAtAll() {
        val run = Run(Game(initialStock = emptyMap()))
        with(run) {
            start(); guardByCatwalk(); lift(); cameraByTiming(); laserByTiming(); gateByLever(); beltByTiming()
            crateToLedge(dropDelay = 0.0)
            ductToExit(sneak = true)
        }
        assertEquals(0, run.g.timesCaught)
        println("No-gadget run finished in ${"%.1f".format(run.g.time)} s\n${run.log}")
    }

    @Test
    fun everyGadgetDoesItsJob() {
        val run = Run(Game())
        with(run) {
            start()

            // Invisibility: straight past the first guard along the floor.
            walkTo(400.0)
            use(PowerupType.INVISIBILITY)
            until("reach the low crate", controls = { Controls(right = true) }) { g.player.bounds.right >= g.lowCover.left - 20 }
            jumpRight()
            until("reach the tall crate", controls = { Controls(right = true) }) { g.player.bounds.right >= g.tallCover.left - 20 }
            jumpRight()
            lift()

            // Camera jammer: every camera blind and frozen.
            use(PowerupType.SMOKE_SCREEN)
            assertTrue(g.activePowerups.isSmokeScreenActive)
            val frozen = g.cameras[0].currentAngle
            until("reach the pillar", controls = { Controls(right = true) }) { g.player.bounds.right >= g.pillar.left - 25 }
            assertEquals(frozen, g.cameras[0].currentAngle, 1e-9, "a jammed camera doesn't sweep")
            jumpRight()

            // Laser shield: straight into the live beam.
            walkTo(1600.0)
            until("invisibility wears off") { !g.activePowerups.isInvisibilityActive }
            use(PowerupType.LASER_SHIELD)
            val sweep = g.lasers[0]
            until("laser is on") { sweep.isActive && sweep.timeUntilInactive(g.time) > 1.0 }
            walkTo(1720.0)
            assertFalse(g.activePowerups.isLaserShieldActive, "shield spent on the beam")

            // Remote trigger: the nearest unthrown lever is the gate's.
            use(PowerupType.REMOTE_TRIGGER)
            assertTrue(g.lasers[1].isDisabled, "remote trigger threw the gate lever")
            walkTo(1975.0)
            beltByTiming()
            crateToLedge(dropDelay = 0.0)

            // Stealth boots: walk (not crouch) out behind the second guard.
            use(PowerupType.NOISE_SUPPRESSION)
            ductToExit(sneak = false)
            assertEquals(0, g.timesCaught)
        }
    }

    /** The crate lands somewhere different depending on where its swing is when released. */
    @Test
    fun theCrateIsClimbableWhereverItIsDropped() {
        for (i in 0..12) {
            val run = Run(Game(initialStock = emptyMap()))
            try {
                with(run) {
                    start(); guardByCatwalk(); lift(); cameraByTiming(); laserByTiming(); gateByLever(); beltByTiming()
                    crateToLedge(dropDelay = i * 0.5)
                    ductToExit(sneak = true)
                }
            } catch (e: AssertionError) {
                throw AssertionError("drop delay ${i * 0.5}s: ${e.message}", e)
            }
        }
    }

    @Test
    fun walkingStraightThroughGetsYouCaught() {
        val g = Game(initialStock = emptyMap())
        g.step(dt, Controls(dismiss = true))
        var t = 0.0
        while (g.timesCaught == 0 && t < 10.0) {
            g.step(dt, Controls(right = true))
            t += dt
        }
        assertEquals("Spotted!", g.lastCaughtReason)
    }

    @Test
    fun checkpointsDecideWhereYouRespawn() {
        // With the Checkpoints gadget: back to the last one reached, and it's spent.
        val withCp = Run(Game(initialStock = mapOf(PowerupType.CHECKPOINTS to 1)))
        with(withCp) { start(); guardByCatwalk(); lift() }
        assertEquals("lift", withCp.g.currentCheckpointId)
        catchPlayer(withCp.g)
        assertTrue(withCp.g.activePowerups.isCheckpointsActive)
        assertEquals(0, withCp.g.stockOf(PowerupType.CHECKPOINTS))
        assertEquals(1120.0, withCp.g.player.bounds.centerX, 1e-6)

        // Without it: the level starts over.
        val without = Run(Game(initialStock = emptyMap()))
        with(without) { start(); guardByCatwalk(); lift() }
        catchPlayer(without.g)
        assertEquals("start", without.g.currentCheckpointId)
        assertEquals(40.0, without.g.player.bounds.centerX, 1e-6)
    }

    /** Stands under the first camera until it catches the player. */
    private fun catchPlayer(g: Game) {
        val caughtBefore = g.timesCaught
        var t = 0.0
        while (g.timesCaught == caughtBefore && t < 30.0) {
            g.step(dt, Controls(right = g.player.bounds.centerX < 1300.0))
            t += dt
        }
        assertEquals(caughtBefore + 1, g.timesCaught, "should have been caught")
    }

    @Test
    fun theQuickSlotRefusesBeforeSpendingLikeTheGame() {
        val g = Game(initialStock = mapOf(PowerupType.INVISIBILITY to 2, PowerupType.REMOTE_TRIGGER to 3))
        g.step(dt, Controls(dismiss = true))
        assertTrue(g.tryActivatePowerup(PowerupType.INVISIBILITY))
        assertFalse(g.tryActivatePowerup(PowerupType.INVISIBILITY), "already running")
        assertEquals(1, g.stockOf(PowerupType.INVISIBILITY))

        assertTrue(g.tryActivatePowerup(PowerupType.REMOTE_TRIGGER))
        assertTrue(g.tryActivatePowerup(PowerupType.REMOTE_TRIGGER))
        assertTrue(g.levers.all { it.isActivated })
        assertFalse(g.tryActivatePowerup(PowerupType.REMOTE_TRIGGER), "no lever left to throw")
        assertEquals(1, g.stockOf(PowerupType.REMOTE_TRIGGER))

        assertFalse(g.tryActivatePowerup(PowerupType.SMOKE_SCREEN), "none in stock")
    }
}

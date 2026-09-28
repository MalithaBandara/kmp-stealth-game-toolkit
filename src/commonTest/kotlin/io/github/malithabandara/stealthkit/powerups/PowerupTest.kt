package io.github.malithabandara.stealthkit.powerups

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PowerupTest {
    @Test
    fun timedGadgetsRunForTheirDuration() {
        val p = ActivePowerups()
        p.activate(PowerupType.SMOKE_SCREEN)
        assertTrue(p.isSmokeScreenActive)
        p.update(9.9)
        assertTrue(p.isSmokeScreenActive)
        p.update(0.2)
        assertFalse(p.isSmokeScreenActive)
        assertEquals(0.0, p.getRemainingTime(PowerupType.SMOKE_SCREEN), 1e-9)
    }

    @Test
    fun laserShieldSoaksExactlyOneHit() {
        val p = ActivePowerups()
        assertFalse(p.consumeLaserShield())
        p.activate(PowerupType.LASER_SHIELD)
        assertEquals(-1.0, p.getRemainingTime(PowerupType.LASER_SHIELD), 1e-9)
        assertTrue(p.consumeLaserShield())
        assertFalse(p.isLaserShieldActive)
    }

    @Test
    fun levelDurationGadgetsStayOnUntilReset() {
        val p = ActivePowerups()
        p.activate(PowerupType.NOISE_SUPPRESSION)
        p.activate(PowerupType.CHECKPOINTS)
        p.update(1000.0)
        assertTrue(p.isNoiseSuppressed && p.isCheckpointsActive && p.anyActive)
        p.reset()
        assertFalse(p.anyActive)
    }

    @Test
    fun remoteTriggerHasNoStateOfItsOwn() {
        val p = ActivePowerups()
        p.activate(PowerupType.REMOTE_TRIGGER)
        assertFalse(p.isActive(PowerupType.REMOTE_TRIGGER))
        assertFalse(p.anyActive)
    }

    @Test
    fun idsAndStoreNamesResolve() {
        assertEquals(PowerupType.SMOKE_SCREEN, PowerupType.fromId("camera_jammer"))
        assertEquals(PowerupType.NOISE_SUPPRESSION, PowerupType.fromId("Stealth_Boots"))
        assertEquals(PowerupType.NOISE_SUPPRESSION, PowerupType.STEALTH_BOOTS)
        assertEquals(PowerupType.INVISIBILITY, PowerupType.fromId("INVISIBILITY"))
        assertEquals(null, PowerupType.fromId("nonsense"))
        assertTrue(PowerupType.CHECKPOINTS.isLevelDuration)
        assertFalse(PowerupType.INVISIBILITY.isLevelDuration)
    }
}

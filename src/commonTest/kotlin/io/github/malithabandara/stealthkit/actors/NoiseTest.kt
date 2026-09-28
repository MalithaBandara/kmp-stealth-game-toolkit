package io.github.malithabandara.stealthkit.actors

import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.geometry.Vec2d
import kotlin.test.Test
import kotlin.test.assertEquals

class NoiseTest {
    private fun guardAt(x: Double) = Guard(x = x, y = 0.0, patrolMinX = x, patrolMaxX = x + 100.0, facing = -1.0)

    @Test
    fun guardsInRangeWithAClearLineHearAndTurnToward() {
        val near = guardAt(100.0)
        val far = guardAt(1000.0)
        val heard = Noise.alertGuards(Vec2d(250.0, 24.0), 239.0, NoiseLevel.NORMAL.radius, listOf(near, far), emptyList())
        assertEquals(listOf(near), heard)
        assertEquals(GuardState.INVESTIGATING, near.state)
        assertEquals(1.0, near.facing, 1e-9)
        assertEquals(GuardState.PATROL, far.state)
    }

    @Test
    fun wallsBlockSound() {
        val g = guardAt(100.0)
        val wall = Rect(180.0, -100.0, 20.0, 300.0)
        assertEquals(emptyList(), Noise.alertGuards(Vec2d(250.0, 24.0), 239.0, NoiseLevel.NORMAL.radius, listOf(g), listOf(wall)))
    }

    @Test
    fun silentAlertsNobody() {
        assertEquals(emptyList(), Noise.alertGuards(Vec2d(120.0, 24.0), 109.0, NoiseLevel.SILENT.radius, listOf(guardAt(100.0)), emptyList()))
    }
}

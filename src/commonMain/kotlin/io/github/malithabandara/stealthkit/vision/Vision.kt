package io.github.malithabandara.stealthkit.vision

import io.github.malithabandara.stealthkit.actors.Guard
import io.github.malithabandara.stealthkit.geometry.GeometryUtils
import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.geometry.Vec2d
import io.github.malithabandara.stealthkit.sentry.Camera
import kotlin.math.*

object VisionSystem {

    /**
     * The occluder-aware field-of-view cone as a filled polygon, ready to hand to a renderer.
     * Samples uniformly across the cone plus extra rays aimed at occluder corners (for crisp
     * shadow edges instead of a blocky cone), and casts each one against [occluders].
     */
    fun computeVisionPolygon(
        origin: Vec2d,
        facingAngle: Double,
        range: Double,
        fov: Double,
        occluders: List<Rect>,
        sampleCount: Int = 36
    ): List<Vec2d> {
        val halfFov = fov / 2.0
        val startAngle = facingAngle - halfFov
        val endAngle = facingAngle + halfFov

        val angles = mutableListOf<Double>()

        // Uniform ray samples
        for (i in 0..sampleCount) {
            val t = i.toDouble() / sampleCount
            angles.add(startAngle + t * fov)
        }

        // Ray samples aimed at occluder corners for crisp shadow silhouettes
        val epsilon = 0.0001
        for (occluder in occluders) {
            val corners = listOf(
                occluder.topLeft,
                occluder.topRight,
                occluder.bottomLeft,
                occluder.bottomRight
            )
            for (corner in corners) {
                val dist = origin.distanceTo(corner)
                if (dist <= range && dist > 1.0) {
                    val angle = atan2(corner.y - origin.y, corner.x - origin.x)
                    // Normalize relative to startAngle
                    val diff = GeometryUtils.angleDifference(angle, facingAngle)
                    if (abs(diff) <= halfFov) {
                        val normalizedAngle = facingAngle + diff
                        angles.add(normalizedAngle - epsilon)
                        angles.add(normalizedAngle)
                        angles.add(normalizedAngle + epsilon)
                    }
                }
            }
        }

        // Sort angles in increasing order
        val sortedAngles = angles.sorted()

        val hitPoints = mutableListOf<Vec2d>()
        for (angle in sortedAngles) {
            hitPoints.add(GeometryUtils.castRay(origin, angle, range, occluders))
        }

        return listOf(origin) + hitPoints
    }

    /**
     * The distance to the closest of [targetPoints] that falls within [visionRange]/[visionFov] of
     * [eye]/[facingAngle] and has an unbroken line of sight through [occluders], or null if none
     * do. Checking several points on a target (e.g. head and feet) rather than one lets a body
     * that's mostly behind cover still count as spotted once enough of it is exposed.
     */
    fun getPlayerSpottedDistance(
        eye: Vec2d,
        facingAngle: Double,
        visionRange: Double,
        visionFov: Double,
        targetPoints: List<Vec2d>,
        occluders: List<Rect>
    ): Double? {
        val halfFov = visionFov / 2.0
        val maxDistSq = visionRange * visionRange
        var closestDist: Double? = null

        for (targetPoint in targetPoints) {
            val distSq = eye.distanceSquaredTo(targetPoint)
            if (distSq > maxDistSq) continue

            val angleToTarget = atan2(targetPoint.y - eye.y, targetPoint.x - eye.x)
            val angleDiff = abs(GeometryUtils.angleDifference(angleToTarget, facingAngle))
            if (angleDiff > halfFov) continue

            if (GeometryUtils.hasLineOfSight(eye, targetPoint, occluders)) {
                val dist = sqrt(distSq)
                if (closestDist == null || dist < closestDist) {
                    closestDist = dist
                }
            }
        }
        return closestDist
    }

    fun getPlayerSpottedDistance(
        guard: Guard,
        targetPoints: List<Vec2d>,
        occluders: List<Rect>
    ): Double? = getPlayerSpottedDistance(
        eye = guard.eyePosition,
        facingAngle = guard.facingAngle,
        visionRange = guard.visionRange,
        visionFov = guard.visionFov,
        targetPoints = targetPoints,
        occluders = occluders
    )

    fun getPlayerSpottedDistance(
        camera: Camera,
        targetPoints: List<Vec2d>,
        occluders: List<Rect>
    ): Double? = getPlayerSpottedDistance(
        eye = camera.eyePosition,
        facingAngle = camera.facingAngle,
        visionRange = camera.visionRange,
        visionFov = camera.visionFov,
        targetPoints = targetPoints,
        occluders = occluders
    )

    fun isPlayerSpotted(
        guard: Guard,
        targetPoints: List<Vec2d>,
        occluders: List<Rect>
    ): Boolean = getPlayerSpottedDistance(guard, targetPoints, occluders) != null

    fun isPlayerSpotted(
        camera: Camera,
        targetPoints: List<Vec2d>,
        occluders: List<Rect>
    ): Boolean = getPlayerSpottedDistance(camera, targetPoints, occluders) != null
}

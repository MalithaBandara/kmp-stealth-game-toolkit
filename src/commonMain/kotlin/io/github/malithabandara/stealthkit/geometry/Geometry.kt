package io.github.malithabandara.stealthkit.geometry

import kotlin.math.*

data class Vec2d(val x: Double, val y: Double) {
    operator fun plus(other: Vec2d): Vec2d = Vec2d(x + other.x, y + other.y)
    operator fun minus(other: Vec2d): Vec2d = Vec2d(x - other.x, y - other.y)
    operator fun times(scalar: Double): Vec2d = Vec2d(x * scalar, y * scalar)
    operator fun div(scalar: Double): Vec2d = Vec2d(x / scalar, y / scalar)
    operator fun unaryMinus(): Vec2d = Vec2d(-x, -y)

    fun length(): Double = sqrt(x * x + y * y)
    fun lengthSquared(): Double = x * x + y * y
    fun distanceTo(other: Vec2d): Double = (this - other).length()
    fun distanceSquaredTo(other: Vec2d): Double = (this - other).lengthSquared()

    fun normalized(): Vec2d {
        val len = length()
        return if (len > 1e-9) Vec2d(x / len, y / len) else Vec2d(0.0, 0.0)
    }

    companion object {
        val ZERO = Vec2d(0.0, 0.0)
    }
}

data class Segment2d(val p1: Vec2d, val p2: Vec2d) {
    fun intersects(other: Segment2d): Vec2d? {
        val d1 = p2 - p1
        val d2 = other.p2 - other.p1
        val cross = d1.x * d2.y - d1.y * d2.x
        if (abs(cross) < 1e-9) return null // Parallel or collinear

        val d3 = other.p1 - p1
        val t = (d3.x * d2.y - d3.y * d2.x) / cross
        val u = (d3.x * d1.y - d3.y * d1.x) / cross

        if (t in 0.0..1.0 && u in 0.0..1.0) {
            return Vec2d(p1.x + t * d1.x, p1.y + t * d1.y)
        }
        return null
    }
}

data class Rect(val x: Double, val y: Double, val width: Double, val height: Double) {
    val left: Double get() = x
    val top: Double get() = y
    val right: Double get() = x + width
    val bottom: Double get() = y + height
    val centerX: Double get() = x + width / 2.0
    val centerY: Double get() = y + height / 2.0

    val topLeft: Vec2d get() = Vec2d(left, top)
    val topRight: Vec2d get() = Vec2d(right, top)
    val bottomLeft: Vec2d get() = Vec2d(left, bottom)
    val bottomRight: Vec2d get() = Vec2d(right, bottom)

    fun intersects(other: Rect): Boolean {
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }

    fun contains(point: Vec2d): Boolean {
        return point.x in left..right && point.y in top..bottom
    }

    fun edges(): List<Segment2d> = listOf(
        Segment2d(topLeft, topRight),       // Top edge
        Segment2d(topRight, bottomRight),   // Right edge
        Segment2d(bottomRight, bottomLeft), // Bottom edge
        Segment2d(bottomLeft, topLeft)      // Left edge
    )

    fun intersectsSegment(seg: Segment2d): Boolean {
        if (contains(seg.p1) || contains(seg.p2)) return true
        for (edge in edges()) {
            if (seg.intersects(edge) != null) return true
        }
        return false
    }
}

data class Ray2d(val origin: Vec2d, val direction: Vec2d, val maxDistance: Double)

data class RaycastHit(
    val point: Vec2d,
    val distance: Double,
    val normal: Vec2d = Vec2d.ZERO
)

object GeometryUtils {
    fun normalizeAngle(angle: Double): Double {
        var a = angle % (2.0 * PI)
        while (a > PI) a -= 2.0 * PI
        while (a < -PI) a += 2.0 * PI
        return a
    }

    fun angleDifference(angle1: Double, angle2: Double): Double {
        return normalizeAngle(angle1 - angle2)
    }

    fun castRay(origin: Vec2d, angle: Double, range: Double, occluders: List<Rect>): Vec2d {
        val dir = Vec2d(cos(angle), sin(angle))
        val target = origin + dir * range
        val raySegment = Segment2d(origin, target)

        var closestPoint = target
        var closestDistanceSq = range * range

        for (occluder in occluders) {
            for (edge in occluder.edges()) {
                val hit = raySegment.intersects(edge)
                if (hit != null) {
                    val distSq = origin.distanceSquaredTo(hit)
                    if (distSq < closestDistanceSq) {
                        closestDistanceSq = distSq
                        closestPoint = hit
                    }
                }
            }
        }

        return closestPoint
    }

    fun hasLineOfSight(from: Vec2d, to: Vec2d, occluders: List<Rect>): Boolean {
        val segment = Segment2d(from, to)
        for (occluder in occluders) {
            for (edge in occluder.edges()) {
                val hit = segment.intersects(edge)
                if (hit != null) {
                    // Check if hit point is strictly between from and to (not just origin)
                    val distFrom = from.distanceTo(hit)
                    val distTotal = from.distanceTo(to)
                    if (distFrom > 1e-4 && distFrom < distTotal - 1e-4) {
                        return false
                    }
                }
            }
        }
        return true
    }
}

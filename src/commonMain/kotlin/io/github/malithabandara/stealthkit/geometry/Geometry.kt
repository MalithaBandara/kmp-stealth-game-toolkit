package io.github.malithabandara.stealthkit.geometry

import kotlin.math.*

/** A 2D point or vector in world units (y grows downward). */
data class Vec2d(val x: Double, val y: Double) {
    operator fun plus(other: Vec2d): Vec2d = Vec2d(x + other.x, y + other.y)
    operator fun minus(other: Vec2d): Vec2d = Vec2d(x - other.x, y - other.y)
    operator fun times(scalar: Double): Vec2d = Vec2d(x * scalar, y * scalar)
    operator fun div(scalar: Double): Vec2d = Vec2d(x / scalar, y / scalar)
    operator fun unaryMinus(): Vec2d = Vec2d(-x, -y)

    /** Euclidean length. */
    fun length(): Double = sqrt(x * x + y * y)
    /** Squared length - cheaper than [length] for comparisons. */
    fun lengthSquared(): Double = x * x + y * y
    /** Distance to [other]. */
    fun distanceTo(other: Vec2d): Double = (this - other).length()
    /** Squared distance to [other] - cheaper than [distanceTo] for comparisons. */
    fun distanceSquaredTo(other: Vec2d): Double = (this - other).lengthSquared()

    /** This vector scaled to length 1, or zero if it has no length. */
    fun normalized(): Vec2d {
        val len = length()
        return if (len > 1e-9) Vec2d(x / len, y / len) else Vec2d(0.0, 0.0)
    }

    companion object {
        val ZERO = Vec2d(0.0, 0.0)
    }
}

/** A line segment from [p1] to [p2]. */
data class Segment2d(val p1: Vec2d, val p2: Vec2d) {
    /** Where this segment crosses [other], or null if they don't (parallel and collinear segments count as not crossing). */
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

/** An axis-aligned rectangle: ([x], [y]) is its top-left corner. The collision and occlusion primitive. */
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

    /** Whether the two overlap. Touching edges don't count. */
    fun intersects(other: Rect): Boolean {
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }

    /** Whether [point] is inside or on the edge. */
    fun contains(point: Vec2d): Boolean {
        return point.x in left..right && point.y in top..bottom
    }

    /** The four sides, clockwise from the top. */
    fun edges(): List<Segment2d> = listOf(
        Segment2d(topLeft, topRight),       // Top edge
        Segment2d(topRight, bottomRight),   // Right edge
        Segment2d(bottomRight, bottomLeft), // Bottom edge
        Segment2d(bottomLeft, topLeft)      // Left edge
    )

    /** Whether [seg] touches this rectangle - an end inside it, or crossing an edge. */
    fun intersectsSegment(seg: Segment2d): Boolean {
        if (contains(seg.p1) || contains(seg.p2)) return true
        for (edge in edges()) {
            if (seg.intersects(edge) != null) return true
        }
        return false
    }
}

/** A ray from [origin] along [direction], up to [maxDistance]. */
data class Ray2d(val origin: Vec2d, val direction: Vec2d, val maxDistance: Double)

/** Where a ray hit something, how far along it was, and the surface normal there. */
data class RaycastHit(
    val point: Vec2d,
    val distance: Double,
    val normal: Vec2d = Vec2d.ZERO
)

/** Angles, raycasts and line-of-sight checks against lists of [Rect] occluders. */
object GeometryUtils {
    /** Wraps [angle] (radians) into -PI..PI. */
    fun normalizeAngle(angle: Double): Double {
        var a = angle % (2.0 * PI)
        while (a > PI) a -= 2.0 * PI
        while (a < -PI) a += 2.0 * PI
        return a
    }

    /** The signed shortest turn from [angle2] to [angle1], in -PI..PI. */
    fun angleDifference(angle1: Double, angle2: Double): Double {
        return normalizeAngle(angle1 - angle2)
    }

    /** Casts a ray from [origin] at [angle] (0 = right, PI/2 = down) and returns the first point it hits on any of [occluders], or its end at [range]. */
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

    /** Whether the straight line from [from] to [to] is clear of every one of [occluders]. */
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

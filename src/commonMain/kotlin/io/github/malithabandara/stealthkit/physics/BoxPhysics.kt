package io.github.malithabandara.stealthkit.physics

import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.geometry.Vec2d
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A rectangular rigid body in the plane - a crate cut loose from its rope, tumbling.
 *
 * Coordinates are the world's (y DOWN), and [angle] is measured so positive turns the box
 * clockwise on screen: a body vector (bx, by) sits at (bx cos a - by sin a, bx sin a + by cos a)
 * from the centre. [width]/[height] are the box's own, unrotated dimensions; a box lying on its
 * side simply has an angle of +-PI/2.
 */
class RigidBox(
    val width: Double,
    val height: Double,
    var cx: Double,
    var cy: Double,
    var angle: Double = 0.0,
    var vx: Double = 0.0,
    var vy: Double = 0.0,
    var omega: Double = 0.0
) {
    val invMass: Double get() = 1.0 / MASS
    val invInertia: Double get() = 1.0 / (MASS * (width * width + height * height) / 12.0)

    /** Asleep: settled on something static and no longer simulated. */
    var isAsleep: Boolean = false
        internal set

    internal var restTimer: Double = 0.0

    private fun toWorld(bx: Double, by: Double): Vec2d {
        val c = cos(angle)
        val s = sin(angle)
        return Vec2d(cx + bx * c - by * s, cy + bx * s + by * c)
    }

    /** The four corners in world space, clockwise from the top-left of the unrotated box. */
    fun corners(): List<Vec2d> {
        val hw = width / 2.0
        val hh = height / 2.0
        return listOf(toWorld(-hw, -hh), toWorld(hw, -hh), toWorld(hw, hh), toWorld(-hw, hh))
    }

    /** The axis-aligned box around the rotated one - what a caller collides against. */
    fun aabb(): Rect {
        val c = abs(cos(angle))
        val s = abs(sin(angle))
        val w = width * c + height * s
        val h = width * s + height * c
        return Rect(cx - w / 2.0, cy - h / 2.0, w, h)
    }

    /** [p] in the box's own frame. */
    internal fun toLocal(p: Vec2d): Vec2d {
        val dx = p.x - cx
        val dy = p.y - cy
        val c = cos(angle)
        val s = sin(angle)
        return Vec2d(dx * c + dy * s, -dx * s + dy * c)
    }

    /** Rounds [angle] to the nearest quarter turn - what a box lying still on a flat face is at. */
    fun squaredUpAngle(): Double = round(angle / (PI / 2.0)) * (PI / 2.0)

    companion object {
        const val MASS = 1.0
    }
}

/**
 * Something a [RigidBox] can hit: a static rect, or one riding a horizontally moving body (e.g. a
 * cart) at [vx], which has finite [invMass] and takes an impulse back. [owner] identifies which
 * body a rect belongs to, so impulses on several rects of one movable obstacle can be summed.
 */
class BoxObstacle(
    val rect: Rect,
    val vx: Double = 0.0,
    val invMass: Double = 0.0,
    val owner: Any? = null
)

/**
 * Impulse-based contact solver for one [RigidBox] against axis-aligned obstacles. Deliberately
 * small and narrowly scoped - this is not a general physics engine: it simulates one box at a
 * time (no box-vs-box), against axis-aligned rectangles only (no arbitrary polygons, no joints).
 * Within that scope it does real, correct impulse-based physics: corner-in-rect and
 * rect-corner-in-box contacts (which between them catch a box landing on a face, on an edge, or
 * across a thin post), restitution only on real impacts so a resting box does not buzz, Coulomb
 * friction, and a positional correction per contact.
 */
object BoxPhysics {
    const val GRAVITY = 1000.0
    /** How much of an impact's closing speed comes back out. */
    const val RESTITUTION = 0.28
    /** Below this closing speed a contact is treated as resting - no bounce. */
    const val BOUNCE_THRESHOLD = 60.0
    const val FRICTION = 0.55
    const val SUBSTEPS = 8
    private const val SLOP = 0.05
    private const val CORRECTION = 0.8
    /** Linear/angular speeds under which a supported box counts as at rest. */
    const val REST_SPEED = 6.0
    const val REST_SPIN = 0.35
    /** How long it has to stay that way before it is put to sleep. */
    const val REST_TIME = 0.25

    private class Contact(val point: Vec2d, val nx: Double, val ny: Double, val depth: Double, val obstacle: BoxObstacle) {
        /** Closing speed the solve aims for - set once, from the speed before any impulse. */
        var targetVn = 0.0
        var accN = 0.0
        var accT = 0.0
    }

    /**
     * Advances [box] by [dt]. [onImpulse] receives each horizontal impulse handed to a movable
     * obstacle (e.g. a cart), keyed by its [BoxObstacle.owner]. Returns true if the box touched an
     * obstacle during the step - and fills [supports] with the obstacles it is resting on.
     *
     * Sequential impulses with ACCUMULATED clamping: each contact's bounce target is fixed from
     * its closing speed before the solve, and the iterations only ever correct toward it. The
     * simpler "re-apply restitution every pass" form pumps energy into a box landing on two
     * corners at once - it flips dropped boxes up onto their ends.
     */
    fun step(
        box: RigidBox,
        dt: Double,
        obstacles: List<BoxObstacle>,
        supports: MutableSet<BoxObstacle> = HashSet(),
        onImpulse: (owner: Any?, jx: Double) -> Unit = { _, _ -> }
    ): Boolean {
        if (box.isAsleep) return false
        supports.clear()
        var touched = false
        val h = dt / SUBSTEPS
        repeat(SUBSTEPS) {
            box.vy += GRAVITY * h
            box.cx += box.vx * h
            box.cy += box.vy * h
            box.angle += box.omega * h
            val contacts = findContacts(box, obstacles)
            if (contacts.isEmpty()) return@repeat
            touched = true
            for (c in contacts) {
                val vn = normalSpeed(box, c)
                c.targetVn = if (vn < -BOUNCE_THRESHOLD) -RESTITUTION * vn else 0.0
            }
            repeat(ITERATIONS) {
                for (c in contacts) resolve(box, c)
            }
            for (c in contacts) {
                if (c.obstacle.invMass > 0.0) onImpulse(c.obstacle.owner, -(c.accN * c.nx + c.accT * -c.ny))
                val push = max(c.depth - SLOP, 0.0) * CORRECTION / contacts.size
                box.cx += c.nx * push
                box.cy += c.ny * push
                // Supported from below: the obstacle is pushing it up.
                if (c.ny < -0.5) supports.add(c.obstacle)
            }
        }
        return touched
    }

    private const val ITERATIONS = 6

    private fun normalSpeed(box: RigidBox, c: Contact): Double {
        val rx = c.point.x - box.cx
        val ry = c.point.y - box.cy
        val pvx = box.vx - box.omega * ry - c.obstacle.vx
        val pvy = box.vy + box.omega * rx
        return pvx * c.nx + pvy * c.ny
    }

    private fun findContacts(box: RigidBox, obstacles: List<BoxObstacle>): List<Contact> {
        val out = ArrayList<Contact>()
        val corners = box.corners()
        val bb = box.aabb()
        val hw = box.width / 2.0
        val hh = box.height / 2.0
        for (o in obstacles) {
            val r = o.rect
            if (bb.right <= r.left || bb.left >= r.right || bb.bottom <= r.top || bb.top >= r.bottom) continue
            // The box's corners inside the obstacle: pushed out through the obstacle's nearest face.
            for (p in corners) {
                if (p.x <= r.left || p.x >= r.right || p.y <= r.top || p.y >= r.bottom) continue
                val dl = p.x - r.left
                val dr = r.right - p.x
                val dt = p.y - r.top
                val db = r.bottom - p.y
                val m = min(min(dl, dr), min(dt, db))
                when (m) {
                    dt -> out.add(Contact(p, 0.0, -1.0, dt, o))
                    db -> out.add(Contact(p, 0.0, 1.0, db, o))
                    dl -> out.add(Contact(p, -1.0, 0.0, dl, o))
                    else -> out.add(Contact(p, 1.0, 0.0, dr, o))
                }
            }
            // The obstacle's corners inside the box (e.g. a post tip under a box's face): the box
            // is pushed away through its own nearest face.
            for (q in listOf(Vec2d(r.left, r.top), Vec2d(r.right, r.top), Vec2d(r.right, r.bottom), Vec2d(r.left, r.bottom))) {
                val l = box.toLocal(q)
                if (abs(l.x) >= hw || abs(l.y) >= hh) continue
                val px = hw - abs(l.x)
                val py = hh - abs(l.y)
                // Outward face normal in the box frame, then into the world; the box moves the
                // other way.
                val (lnx, lny, depth) = if (px < py) Triple(if (l.x > 0) 1.0 else -1.0, 0.0, px)
                else Triple(0.0, if (l.y > 0) 1.0 else -1.0, py)
                val c = cos(box.angle)
                val s = sin(box.angle)
                val wnx = lnx * c - lny * s
                val wny = lnx * s + lny * c
                out.add(Contact(q, -wnx, -wny, depth, o))
            }
        }
        return out
    }

    private fun resolve(box: RigidBox, c: Contact) {
        val rx = c.point.x - box.cx
        val ry = c.point.y - box.cy
        // Normal: drive the closing speed to the bounce target, never pulling (accN >= 0).
        val vn = normalSpeed(box, c)
        val rn = rx * c.ny - ry * c.nx
        val kN = box.invMass + rn * rn * box.invInertia + c.obstacle.invMass * c.nx * c.nx
        val newAccN = max(c.accN + (c.targetVn - vn) / kN, 0.0)
        val dj = newAccN - c.accN
        c.accN = newAccN
        applyImpulse(box, rx, ry, dj * c.nx, dj * c.ny)

        // Friction along the face, bounded by the normal impulse carried so far.
        val tx = -c.ny
        val ty = c.nx
        val pvx = box.vx - box.omega * ry - c.obstacle.vx
        val pvy = box.vy + box.omega * rx
        val vt = pvx * tx + pvy * ty
        val rt = rx * ty - ry * tx
        val kT = box.invMass + rt * rt * box.invInertia + c.obstacle.invMass * tx * tx
        val limit = FRICTION * c.accN
        val newAccT = (c.accT - vt / kT).coerceIn(-limit, limit)
        val djt = newAccT - c.accT
        c.accT = newAccT
        applyImpulse(box, rx, ry, djt * tx, djt * ty)
    }

    private fun applyImpulse(box: RigidBox, rx: Double, ry: Double, jx: Double, jy: Double) {
        box.vx += jx * box.invMass
        box.vy += jy * box.invMass
        box.omega += (rx * jy - ry * jx) * box.invInertia
    }

    /**
     * Puts [box] to sleep once it has been supported and slow for [REST_TIME], squaring it up onto
     * the face it has come to rest on and sitting that face exactly on [supportTop]. Returns true
     * on the step it falls asleep.
     */
    fun settleIfResting(box: RigidBox, dt: Double, supported: Boolean, supportTop: Double?): Boolean {
        if (box.isAsleep) return false
        val slow = sqrt(box.vx * box.vx + box.vy * box.vy) < REST_SPEED && abs(box.omega) < REST_SPIN
        box.restTimer = if (supported && slow) box.restTimer + dt else 0.0
        if (box.restTimer < REST_TIME) return false
        box.angle = box.squaredUpAngle()
        box.vx = 0.0
        box.vy = 0.0
        box.omega = 0.0
        if (supportTop != null) {
            val bb = box.aabb()
            box.cy += supportTop - bb.bottom
        }
        box.isAsleep = true
        return true
    }
}

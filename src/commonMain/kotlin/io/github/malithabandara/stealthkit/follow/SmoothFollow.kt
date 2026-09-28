package io.github.malithabandara.stealthkit.follow

import kotlin.math.abs
import kotlin.math.exp

/**
 * A one-dimensional **critically damped spring** follow - the standard technique for a camera (or
 * any value) that should chase a moving target smoothly, without the jolts a plain lerp produces.
 *
 * ## Why not `x += (target - x) * factor`
 *
 * That first-order form is smooth in *position* but not in *acceleration*: the instant the
 * target's own velocity steps (a character landing a jump, hitting a wall, stopping dead), the
 * follow's acceleration steps with it, which reads as a jolt. A second-order follow's acceleration
 * is a function of the position error and its own velocity, both of which are continuous, so the
 * rate of change can never jump in a single frame no matter what the target does.
 *
 * ## Critically damped, not merely damped
 *
 * Under-damping overshoots (sails past the target and comes back - a wobble every time the target
 * stops); over-damping is a slow crawl back to centre. Critical damping is the one ratio that
 * returns fastest with no overshoot.
 *
 * ## Frame-rate independence
 *
 * [update] is the exact closed-form solution of the critically damped system over one step, not a
 * per-frame approximation, so different frame rates settle along the same curve.
 *
 * ## What [smoothTime] costs
 *
 * Tracking a target moving at a constant velocity `v`, this settles at a steady lag of
 * `v * smoothTime` behind it - the smoothing is bought with exactly that lag. Larger [smoothTime]
 * means smoother but laggier; pick it by measuring the worst single-frame jolt you're trying to
 * absorb against the lag you can tolerate while the target is moving steadily.
 */
class SmoothFollow(
    /** Roughly the time the follow takes to close most of a gap. Larger is smoother and laggier. */
    var smoothTime: Double = DEFAULT_SMOOTH_TIME,
) {
    /** Where the follow is, in whatever units [update] is fed. */
    var position: Double = 0.0
        private set

    /** The follow's own velocity. Carried across frames - it is what makes the follow C1. */
    var velocity: Double = 0.0
        private set

    /** Teleports the follow and kills its momentum. For the first frame and for hard resets/cuts. */
    fun snapTo(target: Double) {
        position = target
        velocity = 0.0
    }

    /**
     * Advances one frame and returns the new [position].
     *
     * [snapIfFartherThan] is the teleport guard: some moves (a respawn, a scene cut) move the
     * target a long way in one frame, and easing across that distance reads as a whip-pan rather
     * than a natural follow. Any jump larger than this is treated as a cut instead.
     */
    fun update(target: Double, dt: Double, snapIfFartherThan: Double = Double.POSITIVE_INFINITY): Double {
        if (dt <= 0.0 || !dt.isFinite() || !target.isFinite()) return position
        if (abs(target - position) > snapIfFartherThan) {
            snapTo(target)
            return position
        }
        // Critically damped x'' + 2*w*x' + w^2*x = 0 solved exactly over dt, in the error frame
        // (x = position - target). smoothTime is 2/w, the classic SmoothDamp parameterisation.
        val omega = 2.0 / smoothTime.coerceAtLeast(MIN_SMOOTH_TIME)
        val decay = exp(-omega * dt)
        val error = position - target
        val term = (velocity + omega * error) * dt
        position = target + (error + term) * decay
        velocity = (velocity - omega * term) * decay
        return position
    }

    companion object {
        /** A reasonable general-purpose default for a gameplay camera follow. */
        const val DEFAULT_SMOOTH_TIME: Double = 0.12

        /** Guards against a division blow-up if someone sets [smoothTime] to zero. */
        const val MIN_SMOOTH_TIME: Double = 1.0 / 240.0
    }
}

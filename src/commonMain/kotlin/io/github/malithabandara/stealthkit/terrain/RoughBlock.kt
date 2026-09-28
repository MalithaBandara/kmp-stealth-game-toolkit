package io.github.malithabandara.stealthkit.terrain

import io.github.malithabandara.stealthkit.geometry.Vec2d
import kotlin.random.Random

/**
 * Generates a jittered top-edge outline for a rectangular block - the small, natural-looking bumps
 * that make a platform read as rough concrete or a rooftop edge instead of a perfect rectangle.
 *
 * This is purely a drawing outline. Pair it with a plain axis-aligned `Rect` for collision, the
 * same way it's used in the game this library was extracted from: the drawn edge is a few pixels
 * of visual noise on top of a perfectly flat platform underneath, not a change to the platform's
 * actual shape. Trying to collide against the jittered edge itself would be solving a much harder
 * problem for no gameplay benefit.
 */
object RoughBlock {
    /**
     * The outline as a closed polygon (bottom-left -> top-left -> jittered top edge, left to
     * right -> top-right -> bottom-right), in the same coordinate space `Rect`/`Vec2d` use
     * (origin top-left, y down) and relative to the block's own top-left corner (add the block's
     * `x`/`y` to translate it into world space).
     *
     * [seed] makes the result deterministic - the same block (e.g. keyed off its world position)
     * always gets the same bumps, while two different blocks look different from each other.
     * [minSegmentLength]/[maxSegmentLength] control how far apart the jitter points are along the
     * top edge; [maxJitter] caps how far a bump rises above the flat edge (y=0). The walk blends
     * toward each new random target (`0.4`/`0.6`) rather than jumping straight to it, which is
     * what keeps the edge reading as continuously rough instead of jagged sawtooth noise.
     */
    fun outline(
        width: Double,
        height: Double,
        seed: Long = 0L,
        minSegmentLength: Double = 5.0,
        maxSegmentLength: Double = 12.0,
        maxJitter: Double = 0.8,
    ): List<Vec2d> {
        require(width > 0.0 && height > 0.0) { "width and height must be positive" }
        require(minSegmentLength > 0.0 && maxSegmentLength >= minSegmentLength) {
            "minSegmentLength must be positive and no larger than maxSegmentLength"
        }

        val points = ArrayList<Vec2d>()
        points.add(Vec2d(0.0, height))
        points.add(Vec2d(0.0, 0.0))

        val rand = Random(seed xor 0x8A9B2C1DL)
        var currX = 0.0
        var currY = 0.0
        while (currX < width) {
            val segLen = rand.nextDouble(minSegmentLength, maxSegmentLength)
            currX += segLen
            if (currX > width) currX = width
            val targetY = -rand.nextDouble(0.0, maxJitter)
            currY = currY * 0.4 + targetY * 0.6
            points.add(Vec2d(currX, currY))
        }

        points.add(Vec2d(width, 0.0))
        points.add(Vec2d(width, height))
        return points
    }
}

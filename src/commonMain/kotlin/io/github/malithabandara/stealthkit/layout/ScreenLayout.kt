package io.github.malithabandara.stealthkit.layout

import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Answers "how big should the game's virtual canvas be on THIS device" for a 2D game authored
 * against one fixed design resolution.
 *
 * ## The problem this solves
 *
 * A common approach is a fixed virtual canvas (e.g. 1040x480) fitted to the screen with a
 * "contain and letterbox" scale mode: the whole canvas is always visible, but any device whose
 * aspect ratio doesn't match the design exactly gets black bars - which can eat a large fraction
 * of the screen on an unusual aspect ratio (a 4:3 tablet, for example, can lose over a third of
 * its screen this way).
 *
 * ## The rule: the design rect is always fully visible, and nothing is ever cropped
 *
 * [viewportFor] returns a virtual canvas size that has the *device's own* aspect ratio (so a
 * "contain" scale mode has nothing left to letterbox) and that always **contains** the
 * [DESIGN_WIDTH] x [DESIGN_HEIGHT] rect every scene is authored against:
 *
 * - **Wider than the design** (tall-and-narrow phones held sideways, 20:9 / 21:9): height stays
 *   [DESIGN_HEIGHT] and the width grows. The player sees a little more of the world left and
 *   right; the vertical framing everything was authored against is untouched.
 * - **Narrower than the design, down to [FULL_WIDTH_ASPECT]** (every phone: 16:9 is 1.778): width
 *   stays [DESIGN_WIDTH] and the height grows. The horizontal field of view stays identical to the
 *   reference device's - useful when gameplay pacing depends on how much of the world fits on
 *   screen at once. The extra height becomes empty space above the action, which a scene can fill
 *   by pinning the ground near the bottom of whatever canvas it's given and tiling a background
 *   up to the new height.
 * - **Squarer than [FULL_WIDTH_ASPECT]** (3:2, 16:10 and 4:3 tablets, unfolded foldables): the
 *   canvas stops growing at [MAX_CANVAS_HEIGHT] and the width starts to give instead. This is the
 *   zoom cap, and it is the one place the "never crop" rule has a deliberate exception - see below.
 *
 * ## The zoom cap, and why "never crop" has an exception
 *
 * Containing the design rect at every aspect sounds reassuring in the abstract - no device ever
 * sees less of the world than the reference device - but on a very square screen (a 4:3 tablet)
 * it plays out badly in practice: if the ground is pinned near the bottom of the canvas and the
 * world renders at a fixed zoom, a tall canvas just stacks empty space above the action, and the
 * gameplay ends up occupying a shrinking fraction of an expensive screen.
 *
 * There is no free lunch here: if there's nothing below the ground to reveal, the only way to make
 * the action fill more of a squarer screen is to magnify it, and magnifying it necessarily shows
 * less width. The cap picks where to stop: the canvas grows to at most [MAX_CANVAS_HEIGHT] (the
 * height at which [DESIGN_WIDTH] exactly fills a [FULL_WIDTH_ASPECT] screen) and past that the
 * width shrinks with the aspect, down to [MIN_CANVAS_WIDTH].
 *
 * [FULL_WIDTH_ASPECT] is deliberately 16:9 - the squarest aspect a phone reaches in landscape - so
 * **no phone loses a single unit of horizontal field of view**, and the cap is a tablet/foldable
 * concession only.
 *
 * ## Landscape is assumed, defensively
 *
 * A landscape-locked game still can't always trust the size a host reports at startup - some
 * platforms report a portrait-shaped bounds for the first moments of launch before rotation
 * resolves, and some large-screen devices ignore orientation locks outright. [viewportFor]
 * therefore normalises its two inputs with max/min rather than trusting which one is "width", and
 * clamps the aspect to [MIN_ASPECT]..[MAX_ASPECT] so a genuinely portrait window degrades into a
 * very tall canvas instead of something absurd.
 */
object ScreenLayout {
    /**
     * The authored canvas. Every scene, HUD inset and overlay should be laid out against these
     * numbers.
     */
    const val DESIGN_WIDTH: Double = 1040.0
    const val DESIGN_HEIGHT: Double = 480.0

    /** The design aspect - the break-even point between the two regimes above. */
    const val DESIGN_ASPECT: Double = DESIGN_WIDTH / DESIGN_HEIGHT

    /**
     * Aspect guards. The low end is below any real landscape device (4:3 is 1.333) and exists only
     * so a portrait window still yields a usable canvas. Past it, a "contain" scale mode
     * letterboxes again, which is the right answer for a window that shape. The high end is past
     * any shipping phone (21:9 is 2.33).
     */
    const val MIN_ASPECT: Double = 0.75
    const val MAX_ASPECT: Double = 3.0

    /**
     * The squarest screen that still gets the whole [DESIGN_WIDTH]: exactly 16:9, the squarest a
     * phone reaches in landscape. Setting it there rather than lower makes the zoom cap below as
     * strong as it can be while still costing no phone a single unit of horizontal field of view.
     */
    const val FULL_WIDTH_ASPECT: Double = 16.0 / 9.0

    /**
     * The tallest canvas the zoom cap hands out: the height at which [DESIGN_WIDTH] exactly fills
     * a [FULL_WIDTH_ASPECT] screen, 585 units. Past that the canvas zooms in rather than adding
     * more empty space above the action.
     */
    const val MAX_CANVAS_HEIGHT: Double = DESIGN_WIDTH / FULL_WIDTH_ASPECT

    /**
     * The narrowest canvas the zoom cap may produce. It binds below aspect 1.37, which is to say
     * on 4:3 tablets and anything squarer.
     */
    const val MIN_CANVAS_WIDTH: Double = 800.0

    /**
     * The virtual canvas for a screen of [screenWidthDp] x [screenHeightDp] density-independent
     * units (dp on Android, points on iOS, logical pixels on desktop/web). Order does not matter -
     * the larger of the two is taken as the landscape width.
     *
     * Returns whole units - a fractional virtual size just gets truncated by most renderers, and a
     * half-unit of truncation is a half-unit of letterboxing.
     */
    fun viewportFor(screenWidthDp: Double, screenHeightDp: Double): VirtualViewport {
        val long = max(screenWidthDp, screenHeightDp)
        val short = min(screenWidthDp, screenHeightDp)
        // A host that has not measured its window yet (0, NaN, or a single pixel) gets the
        // design canvas rather than a division by zero.
        if (!long.isFinite() || !short.isFinite() || short <= 1.0 || long <= 1.0) {
            return VirtualViewport(DESIGN_WIDTH, DESIGN_HEIGHT)
        }
        val aspect = (long / short).coerceIn(MIN_ASPECT, MAX_ASPECT)
        // What containing the whole design rect would ask for on its own.
        val containHeight = max(DESIGN_HEIGHT, DESIGN_WIDTH / aspect)
        // The zoom cap: stop growing the canvas at MAX_CANVAS_HEIGHT so a squarer screen magnifies
        // the action instead of stacking empty space on top of it, but never let that shrink the
        // canvas past MIN_CANVAS_WIDTH.
        val cappedHeight = max(MAX_CANVAS_HEIGHT, MIN_CANVAS_WIDTH / aspect)
        val height = min(containHeight, cappedHeight)
        return VirtualViewport(round(height * aspect), round(height))
    }

    /** [viewportFor] for a whole [ScreenMetrics] reading. */
    fun viewportFor(metrics: ScreenMetrics): VirtualViewport =
        viewportFor(metrics.widthDp, metrics.heightDp)

    /**
     * How far a HUD row pinned to the **top** of the canvas should hold off the edge on account of
     * the safe area, in landscape: not at all, by default reasoning.
     *
     * A landscape-locked game with its status bar hidden typically has nothing physically
     * occupying the top edge - the short edge (where a notch/camera cutout lives) is a *side* in
     * landscape, not the top. Some platforms nonetheless report a nonzero top inset in landscape
     * for gesture-navigation reasons (a swipe region, not something drawn over the app) - naively
     * honouring it can push HUD elements down for no visual reason. This helper returns 0 in
     * landscape and the real inset only for a genuinely portrait window (where a cutout or status
     * bar really can sit across the top).
     */
    fun gameplayTopInset(insets: SafeAreaInsets, canvasWidth: Double, canvasHeight: Double): Double =
        if (canvasWidth >= canvasHeight) 0.0 else insets.top

    /**
     * The device's safe-area insets expressed in the virtual units a scene lays out in.
     *
     * Converted as a **fraction of the screen** rather than through a units-per-dp factor,
     * deliberately: a host reports its screen size in whatever units it has to hand (dp on
     * Android, points on iOS, logical pixels elsewhere) and the canvas may have been sized from a
     * different measurement of the same screen, so a fraction is the one thing that survives both.
     * An inset that covers 7% of the screen's width covers 7% of the canvas's width, in whatever
     * units either of them is counted in.
     */
    fun safeInsetsInVirtualUnits(
        metrics: ScreenMetrics,
        viewport: VirtualViewport = viewportFor(metrics),
    ): SafeAreaInsets {
        val safe = metrics.safeArea
        if (safe.isEmpty) return SafeAreaInsets.NONE
        // Landscape-normalised the same way viewportFor is: the long side is the horizontal one.
        val screenLong = metrics.longSideDp
        val screenShort = metrics.shortSideDp
        if (!screenLong.isFinite() || !screenShort.isFinite() || screenLong <= 1.0 || screenShort <= 1.0) {
            return SafeAreaInsets.NONE
        }
        return SafeAreaInsets(
            left = safe.left / screenLong * viewport.width,
            top = safe.top / screenShort * viewport.height,
            right = safe.right / screenLong * viewport.width,
            bottom = safe.bottom / screenShort * viewport.height,
        )
    }
}

/** A virtual canvas size in scene units. */
data class VirtualViewport(val width: Double, val height: Double) {
    val aspect: Double get() = if (height > 0.0) width / height else ScreenLayout.DESIGN_ASPECT
}

/**
 * Screen area the OS keeps for itself - a notch or camera cutout (on a *side* in landscape), a
 * home-indicator strip, a display cutout, gesture strips. Same units as whatever produced it:
 * device dp on a [ScreenMetrics], virtual scene units once run through
 * [ScreenLayout.safeInsetsInVirtualUnits].
 */
data class SafeAreaInsets(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
) {
    val isEmpty: Boolean get() = left <= 0.0 && top <= 0.0 && right <= 0.0 && bottom <= 0.0

    companion object {
        val NONE = SafeAreaInsets()
    }
}

/**
 * What a platform host measured about the screen it is running on, in density-independent units.
 * A host publishes this before the UI is built; everything downstream reads it from there rather
 * than reaching for a platform API of its own.
 */
data class ScreenMetrics(
    val widthDp: Double,
    val heightDp: Double,
    val safeArea: SafeAreaInsets = SafeAreaInsets.NONE,
) {
    /** Landscape-normalised. */
    val longSideDp: Double get() = max(widthDp, heightDp)
    val shortSideDp: Double get() = min(widthDp, heightDp)

    /**
     * True for a screen with a tablet's short side. 600dp is Android's own `sw600dp` breakpoint
     * and also separates every iPhone (a landscape iPhone's short side is 320-440pt) from every
     * iPad (744pt and up).
     */
    val isTablet: Boolean get() = shortSideDp >= 600.0
}

/**
 * The live [ScreenMetrics] for this process, published by whatever platform host measured it.
 *
 * `@Volatile` (from `kotlin.concurrent`, not `kotlin.jvm` - that one doesn't exist on
 * Kotlin/Native) because a host may write it on a UI thread while a render loop reads it on a
 * different one.
 *
 * Unset is a supported state: everything that reads this falls back to the design canvas.
 */
object DeviceScreen {
    @Volatile
    var metrics: ScreenMetrics? = null

    /** The virtual canvas for the current device, or the design canvas if nothing is published. */
    val viewport: VirtualViewport
        get() = metrics?.let { ScreenLayout.viewportFor(it) }
            ?: VirtualViewport(ScreenLayout.DESIGN_WIDTH, ScreenLayout.DESIGN_HEIGHT)

    /** Safe-area insets in virtual scene units, or zero if nothing is published. */
    val safeInsets: SafeAreaInsets
        get() = metrics?.let { ScreenLayout.safeInsetsInVirtualUnits(it) } ?: SafeAreaInsets.NONE

    /**
     * Safe-area insets scaled to a canvas whose size the caller already knows - useful when a
     * scene's own actual canvas size may differ by a unit of rounding from [viewport].
     */
    fun safeInsetsForCanvas(canvasWidth: Double, canvasHeight: Double): SafeAreaInsets {
        val m = metrics ?: return SafeAreaInsets.NONE
        return ScreenLayout.safeInsetsInVirtualUnits(m, VirtualViewport(canvasWidth, canvasHeight))
    }

    val isTablet: Boolean get() = metrics?.isTablet ?: false

    fun publish(
        widthDp: Double,
        heightDp: Double,
        safeLeftDp: Double = 0.0,
        safeTopDp: Double = 0.0,
        safeRightDp: Double = 0.0,
        safeBottomDp: Double = 0.0,
    ) {
        metrics = ScreenMetrics(
            widthDp = widthDp,
            heightDp = heightDp,
            safeArea = SafeAreaInsets(safeLeftDp, safeTopDp, safeRightDp, safeBottomDp),
        )
    }

    /**
     * Replaces only the insets, keeping the size already published. Some platforms learn their
     * safe area later than their screen size (the window has to lay out first), so the two can
     * arrive separately.
     */
    fun publishSafeArea(
        leftDp: Double,
        topDp: Double,
        rightDp: Double,
        bottomDp: Double,
    ) {
        val current = metrics ?: return
        metrics = current.copy(safeArea = SafeAreaInsets(leftDp, topDp, rightDp, bottomDp))
    }
}

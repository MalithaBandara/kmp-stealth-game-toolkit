package io.github.malithabandara.stealthkit.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenLayoutTest {
    @Test
    fun theReferenceAspectGetsExactlyTheAuthoredCanvas() {
        val vp = ScreenLayout.viewportFor(1040.0, 480.0)
        assertEquals(1040.0, vp.width, 1e-6)
        assertEquals(480.0, vp.height, 1e-6)
    }

    @Test
    fun widerThanDesignKeepsTheAuthoredHeightAndGrowsWidth() {
        // 21:9, wider than the design's 2.1667 aspect.
        val vp = ScreenLayout.viewportFor(2100.0, 900.0)
        assertEquals(ScreenLayout.DESIGN_HEIGHT, vp.height, 1e-6)
        assertTrue(vp.width > ScreenLayout.DESIGN_WIDTH)
    }

    @Test
    fun theSquarestPhoneAspectKeepsTheAuthoredWidthAndGrowsHeightUpToTheCap() {
        // 16:9 - the squarest a phone reaches in landscape - should exactly hit the zoom cap
        // height with the design width intact (no phone should ever lose horizontal field of view).
        val vp = ScreenLayout.viewportFor(1920.0, 1080.0)
        assertEquals(ScreenLayout.DESIGN_WIDTH, vp.width, 1e-6)
        assertEquals(ScreenLayout.MAX_CANVAS_HEIGHT, vp.height, 1e-6)
    }

    @Test
    fun aFourByThreeTabletZoomsInInsteadOfStackingEmptySpaceAboveTheAction() {
        val vp = ScreenLayout.viewportFor(1024.0, 768.0) // 4:3
        // The zoom cap should have kicked in: width gives way to MIN_CANVAS_WIDTH instead of
        // containing the design rect uncapped (which would ask for a much taller 1040x780 canvas).
        assertEquals(ScreenLayout.MIN_CANVAS_WIDTH, vp.width, 1.0)
        assertTrue(vp.height < ScreenLayout.DESIGN_WIDTH / (4.0 / 3.0)) // less than the uncapped 780
    }

    @Test
    fun orientationOfTheInputDoesNotMatter() {
        val landscape = ScreenLayout.viewportFor(1920.0, 1080.0)
        val portraitInput = ScreenLayout.viewportFor(1080.0, 1920.0)
        assertEquals(landscape.width, portraitInput.width, 1e-6)
        assertEquals(landscape.height, portraitInput.height, 1e-6)
    }

    @Test
    fun degenerateInputsFallBackToTheDesignCanvas() {
        val zero = ScreenLayout.viewportFor(0.0, 0.0)
        assertEquals(ScreenLayout.DESIGN_WIDTH, zero.width, 1e-6)
        assertEquals(ScreenLayout.DESIGN_HEIGHT, zero.height, 1e-6)

        val nan = ScreenLayout.viewportFor(Double.NaN, 480.0)
        assertEquals(ScreenLayout.DESIGN_WIDTH, nan.width, 1e-6)
    }

    @Test
    fun anExtremelyElongatedWindowIsClampedAtMaxAspectRatherThanGrowingWithoutLimit() {
        // Far past any shipping device (MAX_ASPECT is 3.0, i.e. up to 21:9-ish); the resulting
        // canvas aspect should not exceed the clamp even though the raw input ratio does.
        val vp = ScreenLayout.viewportFor(4000.0, 1000.0) // raw aspect 4.0
        assertTrue(vp.aspect <= ScreenLayout.MAX_ASPECT + 1e-6)
    }

    @Test
    fun safeInsetsConvertAsAFractionOfTheScreenIntoVirtualUnits() {
        val metrics = ScreenMetrics(
            widthDp = 1000.0,
            heightDp = 500.0,
            safeArea = SafeAreaInsets(left = 50.0, top = 25.0, right = 50.0, bottom = 25.0) // 5% each side
        )
        val viewport = ScreenLayout.viewportFor(metrics)
        val insets = ScreenLayout.safeInsetsInVirtualUnits(metrics, viewport)
        assertEquals(viewport.width * 0.05, insets.left, 1e-6)
        assertEquals(viewport.height * 0.05, insets.top, 1e-6)
    }

    @Test
    fun emptySafeAreaProducesNoInsets() {
        val metrics = ScreenMetrics(widthDp = 1000.0, heightDp = 500.0)
        val insets = ScreenLayout.safeInsetsInVirtualUnits(metrics)
        assertEquals(SafeAreaInsets.NONE, insets)
    }

    @Test
    fun deviceScreenFallsBackToTheDesignCanvasWhenNothingIsPublished() {
        DeviceScreen.metrics = null
        assertEquals(ScreenLayout.DESIGN_WIDTH, DeviceScreen.viewport.width, 1e-6)
        assertEquals(ScreenLayout.DESIGN_HEIGHT, DeviceScreen.viewport.height, 1e-6)
        assertEquals(SafeAreaInsets.NONE, DeviceScreen.safeInsets)
    }

    @Test
    fun deviceScreenReflectsWhatWasPublished() {
        DeviceScreen.publish(widthDp = 1920.0, heightDp = 1080.0)
        assertEquals(ScreenLayout.DESIGN_WIDTH, DeviceScreen.viewport.width, 1e-6)
        assertEquals(ScreenLayout.MAX_CANVAS_HEIGHT, DeviceScreen.viewport.height, 1e-6)
        DeviceScreen.metrics = null // leave global state clean for other tests
    }

    @Test
    fun gameplayTopInsetIsZeroInLandscapeAndTheRealInsetInPortrait() {
        val insets = SafeAreaInsets(top = 40.0)
        assertEquals(0.0, ScreenLayout.gameplayTopInset(insets, canvasWidth = 1040.0, canvasHeight = 480.0), 1e-9)
        assertEquals(40.0, ScreenLayout.gameplayTopInset(insets, canvasWidth = 480.0, canvasHeight = 1040.0), 1e-9)
    }
}

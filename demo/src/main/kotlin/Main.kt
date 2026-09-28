import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.malithabandara.stealthkit.actors.Guard
import io.github.malithabandara.stealthkit.actors.GuardState
import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.geometry.Vec2d
import io.github.malithabandara.stealthkit.hazards.Laser
import io.github.malithabandara.stealthkit.layout.DeviceScreen
import io.github.malithabandara.stealthkit.layout.ScreenLayout
import io.github.malithabandara.stealthkit.mechanisms.Lever
import io.github.malithabandara.stealthkit.powerups.PowerupType
import io.github.malithabandara.stealthkit.sentry.Camera
import io.github.malithabandara.stealthkit.vision.VisionSystem
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------
// Keyboard -> Controls. Desktop key-repeat sends repeated KeyDowns, so one-shot actions only fire
// on the first.
// ---------------------------------------------------------------------------------------------

private val GADGET_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)

class Keyboard {
    private val held = HashSet<Key>()
    private val pressed = HashSet<Key>()

    fun down(key: Key) {
        if (held.add(key)) pressed.add(key)
    }

    fun up(key: Key) {
        held.remove(key)
    }

    // Key is a value class, so these take a list rather than varargs.
    private fun held(keys: List<Key>) = keys.any { it in held }
    private fun pressed(keys: List<Key>) = keys.any { it in pressed }

    fun controls() = Controls(
        left = held(listOf(Key.A, Key.DirectionLeft)),
        right = held(listOf(Key.D, Key.DirectionRight)),
        crouch = held(listOf(Key.S, Key.DirectionDown)),
        jump = pressed(listOf(Key.Spacebar, Key.W, Key.DirectionUp)),
        interact = pressed(listOf(Key.E)),
        gadget = GADGET_KEYS.indexOfFirst { it in pressed }.takeIf { it >= 0 }?.let { QUICK_SLOT[it] },
        dismiss = pressed.isNotEmpty(),
        restart = pressed(listOf(Key.Enter)),
    )

    fun endFrame() = pressed.clear()
}

// ---------------------------------------------------------------------------------------------
// Rendering: plain Compose Canvas shapes, no assets.
// ---------------------------------------------------------------------------------------------

private object Palette {
    val skyTop = Color(0xFF0B1020)
    val skyBottom = Color(0xFF1C2440)
    val skyline = Color(0xFF151B30)
    val skylineWindow = Color(0x33FFD27A)
    val ground = Color(0xFF3A4152)
    val groundEdge = Color(0xFF596274)
    val crate = Color(0xFF8A6A43)
    val crateEdge = Color(0xFF5C4528)
    val metal = Color(0xFF6B7385)
    val metalDark = Color(0xFF454C5C)
    val belt = Color(0xFF2A2E38)
    val player = Color(0xFF3FD0C0)
    val playerVisor = Color(0xFFE9FFFB)
    val guard = Color(0xFF5B6CFF)
    val alert = Color(0xFFFF5A4E)
    val investigating = Color(0xFFFFA64D)
    val skin = Color(0xFFE2B48C)
    val cone = Color(0xFFFFE680)
    val laser = Color(0xFFFF3040)
    val door = Color(0xFF63F28C)
    val flag = Color(0xFFFFC04D)
    val hud = Color(0xFFE8ECF5)
    val panel = Color(0xE0101626)

    fun gadget(type: PowerupType) = when (type) {
        PowerupType.SMOKE_SCREEN -> Color(0xFF9AA7BD)
        PowerupType.LASER_SHIELD -> Color(0xFF7FD4FF)
        PowerupType.INVISIBILITY -> Color(0xFFB889FF)
        PowerupType.NOISE_SUPPRESSION -> Color(0xFF7BE0A0)
        PowerupType.CHECKPOINTS -> flag
        PowerupType.REMOTE_TRIGGER -> Color(0xFFFF9A4D)
    }
}

private fun Vec2d.offset() = Offset(x.toFloat(), y.toFloat())

private fun DrawScope.fillRect(r: Rect, color: Color) =
    drawRect(color, Offset(r.x.toFloat(), r.y.toFloat()), Size(r.width.toFloat(), r.height.toFloat()))

private fun DrawScope.strokeRect(r: Rect, color: Color, width: Float = 2f) =
    drawRect(color, Offset(r.x.toFloat(), r.y.toFloat()), Size(r.width.toFloat(), r.height.toFloat()), style = Stroke(width))

private fun polygon(points: List<Vec2d>, dx: Double = 0.0, dy: Double = 0.0): Path = Path().apply {
    points.forEachIndexed { i, p ->
        val x = (p.x + dx).toFloat()
        val y = (p.y + dy).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private data class Building(val x: Double, val width: Double, val height: Double)

private val skyline: List<Building> = Random(7).let { r ->
    var x = -200.0
    buildList {
        while (x < LEVEL_END) {
            val w = r.nextDouble(60.0, 120.0)
            add(Building(x, w, r.nextDouble(90.0, 230.0)))
            x += w + r.nextDouble(8.0, 30.0)
        }
    }
}

private fun DrawScope.drawWorld(g: Game) {
    val jammed = g.activePowerups.isSmokeScreenActive

    // Parallax skyline at half the camera's speed.
    val parallax = g.cameraX * 0.5
    for (b in skyline) {
        val r = Rect(b.x + parallax, GROUND_Y - b.height, b.width, b.height)
        fillRect(r, Palette.skyline)
        var wy = r.y + 12
        while (wy < GROUND_Y - 20) {
            var wx = r.x + 10
            while (wx < r.right - 14) {
                if (((wx + wy).toInt() / 7) % 3 == 0) fillRect(Rect(wx, wy, 6.0, 8.0), Palette.skylineWindow)
                wx += 16
            }
            wy += 22
        }
    }

    // Ceiling girder the cameras and laser emitters hang from.
    fillRect(Rect(1150.0, CEILING_Y - 10, 1250.0, 10.0), Palette.metalDark)

    // Vision cones under everything solid.
    val occluders = g.occluders()
    g.guards.forEachIndexed { i, guard ->
        val cone = VisionSystem.computeVisionPolygon(
            guard.eyePosition, guard.facingAngle, guard.visionRange, guard.visionFov, occluders,
        )
        val color = when {
            g.guardSees[i] -> Palette.alert
            guard.state == GuardState.INVESTIGATING -> Palette.investigating
            else -> Palette.cone
        }
        drawPath(polygon(cone), color.copy(alpha = 0.22f))
    }
    // A jammed camera draws no cone - it can't see.
    if (!jammed) {
        g.cameras.forEachIndexed { i, camera ->
            val cone = VisionSystem.computeVisionPolygon(
                camera.eyePosition, camera.facingAngle, camera.visionRange, camera.visionFov, occluders,
            )
            drawPath(polygon(cone), (if (g.cameraSees[i]) Palette.alert else Palette.cone).copy(alpha = 0.18f))
        }
    }

    // Ground and ledge with RoughBlock edges.
    for ((block, outline) in g.roughOutlines) {
        drawPath(polygon(outline, block.x, block.y), Palette.ground)
        drawLine(
            Palette.groundEdge, Offset(block.x.toFloat(), block.y.toFloat() + 3f),
            Offset(block.right.toFloat(), block.y.toFloat() + 3f), 1.5f,
        )
    }

    for (c in listOf(g.lowCover, g.tallCover)) drawCrate(c)
    fillRect(g.pillar, Palette.metal)
    strokeRect(g.pillar, Palette.metalDark)
    for (walk in listOf(g.catwalk, g.duct)) {
        fillRect(walk, Palette.metal)
        strokeRect(walk, Palette.metalDark, 1.5f)
        for (hx in listOf(walk.left + 20, walk.right - 20)) {
            drawLine(Palette.metalDark, Offset(hx.toFloat(), 0f), Offset(hx.toFloat(), walk.top.toFloat()), 2f)
        }
    }

    // Checkpoint flags, lit once secured.
    g.checkpoints.manualCheckpoints.forEachIndexed { i, cp ->
        val x = cp.x + PLAYER_W / 2
        val floor = cp.y + STAND_H
        drawLine(Palette.metal, Offset(x.toFloat(), floor.toFloat()), Offset(x.toFloat(), floor.toFloat() - 40f), 2f)
        drawPath(
            polygon(listOf(Vec2d(x, floor - 40), Vec2d(x + 16, floor - 34), Vec2d(x, floor - 28))),
            if (i <= g.checkpoints.currentManualCheckpointIndex) Palette.flag else Palette.metalDark,
        )
    }

    drawLift(g)
    drawBelt(g)
    drawHookCrate(g)
    g.levers.forEach { drawLever(it) }
    g.lasers.forEach { drawLaser(it) }
    g.cameras.forEachIndexed { i, c -> drawCamera(c, g.cameraSees[i], jammed, g.time) }
    g.guards.forEachIndexed { i, guard -> drawGuard(guard, g.guardSees[i]) }

    fillRect(g.exitDoor, Palette.door.copy(alpha = 0.25f + 0.1f * sin(g.time * 3).toFloat()))
    strokeRect(g.exitDoor, Palette.door, 3f)

    drawPlayer(g)

    // Remote trigger: a signal arcing from the player to the lever it threw.
    g.remoteSignal?.let { (from, to) ->
        drawLine(
            Palette.gadget(PowerupType.REMOTE_TRIGGER), from.offset(), to.offset(), 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        )
        drawCircle(Palette.gadget(PowerupType.REMOTE_TRIGGER), 10f, to.offset(), style = Stroke(2f))
    }
}

private fun DrawScope.drawCrate(r: Rect) {
    fillRect(r, Palette.crate)
    strokeRect(r, Palette.crateEdge)
    drawLine(Palette.crateEdge, r.topLeft.offset(), r.bottomRight.offset(), 2f)
}

private fun DrawScope.drawLift(g: Game) {
    val b = g.lift.bounds
    for (cx in listOf(b.left + 10, b.right - 10)) {
        drawLine(Palette.metalDark, Offset(cx.toFloat(), -300f), Offset(cx.toFloat(), b.top.toFloat()), 2f)
    }
    fillRect(b, Palette.metal)
    strokeRect(b, Palette.metalDark)
}

private fun DrawScope.drawBelt(g: Game) {
    val b = g.belt.bounds
    fillRect(b, Palette.belt)
    // Tread marks scrolling at the belt's speed.
    val phase = ((g.time * g.belt.speed) % 20.0).toFloat()
    clipRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()) {
        var x = b.left.toFloat() - 20f + phase
        while (x < b.right) {
            drawLine(Palette.metalDark, Offset(x, b.top.toFloat() + 3f), Offset(x + 6f, b.bottom.toFloat() - 3f), 2f)
            x += 20f
        }
    }
    // Crates wrap round the ends, so clip them to the belt and cap the ends with housings.
    clipRect(b.left.toFloat(), -500f, b.right.toFloat(), 1000f) {
        g.beltCrates.forEach { drawCrate(it.bounds) }
    }
    for (hx in listOf(b.left - 14, b.right)) {
        fillRect(Rect(hx, b.top - 40, 14.0, 40.0 + b.height), Palette.metalDark)
    }
}

private fun DrawScope.drawHookCrate(g: Game) {
    val hc = g.hookCrate
    val railY = (hc.hook.y - 5).toFloat()
    drawLine(Palette.metalDark, Offset(g.hookRailLeft.toFloat(), railY), Offset(g.hookRailRight.toFloat(), railY), 5f)
    fillRect(hc.currentHook, Palette.metal)

    val topX = hc.ropeTopX.toFloat()
    val topY = hc.ropeTopY.toFloat()
    val rope = Palette.hud.copy(alpha = 0.7f)
    if (hc.isHanging) {
        val ax = hc.ropeTopX + hc.ropeLength * sin(hc.swingAngle)
        val ay = hc.ropeTopY + hc.ropeLength * cos(hc.swingAngle)
        drawLine(rope, Offset(topX, topY), Offset(ax.toFloat(), ay.toFloat()), 2f)
    } else {
        drawLine(rope, Offset(topX, topY), Offset(topX, topY + 18f), 2f)
    }

    val w = hc.initialBounds.width.toFloat()
    val h = hc.initialBounds.height.toFloat()
    val center = Offset(hc.drawCenterX.toFloat(), hc.drawCenterY.toFloat())
    rotate(degrees = Math.toDegrees(hc.drawAngle).toFloat(), pivot = center) {
        val tl = Offset(center.x - w / 2, center.y - h / 2)
        drawRect(Palette.crate, tl, Size(w, h))
        drawRect(Palette.crateEdge, tl, Size(w, h), style = Stroke(3f))
        drawLine(Palette.crateEdge, tl, Offset(tl.x + w, tl.y + h), 3f)
        drawLine(Palette.crateEdge, Offset(tl.x + w, tl.y), Offset(tl.x, tl.y + h), 3f)
    }
}

private fun DrawScope.drawLever(lever: Lever) {
    val base = lever.bounds
    fillRect(Rect(base.x, base.bottom - 6, base.width, 6.0), Palette.metalDark)
    val pivot = Offset(base.centerX.toFloat(), (base.bottom - 5).toFloat())
    val angle = if (lever.isActivated) deg(35.0) else deg(-35.0)
    val tip = Offset(pivot.x + (sin(angle) * 22).toFloat(), pivot.y - (cos(angle) * 22).toFloat())
    drawLine(Palette.metal, pivot, tip, 4f, cap = StrokeCap.Round)
    drawCircle(if (lever.isActivated) Palette.door else Palette.laser, 4.5f, tip)
}

private fun DrawScope.drawLaser(laser: Laser) {
    val s = laser.emitterScale
    val top = Offset(laser.topX.toFloat(), laser.topY.toFloat())
    val bottom = Offset(laser.bottomX.toFloat(), laser.bottomY.toFloat())
    drawLine(Palette.metalDark, Offset(top.x, CEILING_Y.toFloat()), top, 3f)
    fillRect(Rect(laser.topX - 8 * s, laser.topY - 10 * s, 16 * s, 10 * s), Palette.metal)
    fillRect(Rect(laser.bottomX - 8 * s, laser.bottomY - 5 * s, 16 * s, 5 * s), Palette.metal)
    when {
        laser.isActive -> {
            drawLine(Palette.laser.copy(alpha = 0.25f), top, bottom, (laser.beamThickness * 3).toFloat())
            drawLine(Palette.laser, top, bottom, (laser.beamThickness * 0.6).toFloat())
        }
        !laser.isDisabled -> drawLine(
            Palette.laser.copy(alpha = 0.2f), top, bottom, 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
        )
    }
}

private fun DrawScope.drawCamera(c: Camera, seesPlayer: Boolean, jammed: Boolean, time: Double) {
    val mountX = (c.x + c.width / 2).toFloat()
    drawLine(Palette.metalDark, Offset(mountX, CEILING_Y.toFloat()), Offset(mountX, c.y.toFloat()), 3f)
    fillRect(Rect(c.x, c.y, c.width, 5.0), Palette.metalDark)
    val pivot = c.pivotPosition.offset()
    val eye = c.eyePosition.offset()
    drawLine(Palette.metalDark, Offset(mountX, c.y.toFloat()), pivot, 3f)
    drawLine(Palette.metal, pivot, eye, 10f, cap = StrokeCap.Round)
    drawCircle(Color(0xFF1A1F2B), 4.5f, eye)
    if (jammed) {
        // Static fizzing around a jammed lens.
        val jam = Palette.gadget(PowerupType.SMOKE_SCREEN)
        for (i in 0..2) {
            val r = 8f + ((time * 20 + i * 5) % 12).toFloat()
            drawCircle(jam.copy(alpha = 0.5f - r / 40f), r, eye, style = Stroke(1.5f))
        }
    }
    val led = when {
        jammed -> Palette.gadget(PowerupType.SMOKE_SCREEN)
        seesPlayer || c.isPausedFromDetection -> Palette.alert
        else -> Palette.door
    }
    drawCircle(led, 2.5f, pivot)
}

private fun DrawScope.drawGuard(guard: Guard, seesPlayer: Boolean) {
    val b = guard.bounds
    val body = if (seesPlayer) Palette.alert else Palette.guard
    drawRoundRect(
        body, Offset(b.x.toFloat(), (b.y + 14).toFloat()), Size(b.width.toFloat(), (b.height - 14).toFloat()),
        CornerRadius(6f),
    )
    val head = Offset(b.centerX.toFloat(), (b.y + 8).toFloat())
    drawCircle(Palette.skin, 8f, head)
    drawRect(Color(0xFF232A4A), Offset(head.x - 9f, head.y - 9f), Size(18f, 5f))
    // Arm out to the torch, which is exactly where vision starts.
    val eye = guard.eyePosition.offset()
    drawLine(body, Offset(b.centerX.toFloat(), eye.y), eye, 5f, cap = StrokeCap.Round)
    drawCircle(Palette.cone, 3.5f, eye)

    when {
        seesPlayer -> {
            drawRect(Palette.alert, Offset(head.x - 2f, head.y - 34f), Size(4f, 14f))
            drawRect(Palette.alert, Offset(head.x - 2f, head.y - 17f), Size(4f, 4f))
        }
        guard.state == GuardState.INVESTIGATING -> drawCircle(Palette.investigating, 4f, Offset(head.x, head.y - 20f))
    }
}

private fun DrawScope.drawPlayer(g: Game) {
    val p = g.player
    val b = p.bounds
    val pu = g.activePowerups
    val alpha = if (pu.isInvisibilityActive) 0.35f else 1f
    drawRoundRect(
        Palette.player.copy(alpha = alpha), Offset(b.x.toFloat(), b.y.toFloat()),
        Size(b.width.toFloat(), b.height.toFloat()), CornerRadius(7f),
    )
    val visorX = if (p.facing > 0) b.right - 9 else b.left + 3
    drawRect(Palette.playerVisor.copy(alpha = alpha), Offset(visorX.toFloat(), (b.y + 7).toFloat()), Size(6f, 4f))
    if (pu.isLaserShieldActive) {
        drawCircle(
            Palette.gadget(PowerupType.LASER_SHIELD).copy(alpha = 0.55f), (b.height * 0.75).toFloat(),
            Offset(b.centerX.toFloat(), b.centerY.toFloat()), style = Stroke(2f),
        )
    }
    if (pu.isNoiseSuppressed) {
        // Soft pads on the boots.
        drawRect(Palette.gadget(PowerupType.NOISE_SUPPRESSION), Offset(b.x.toFloat() - 1f, b.bottom.toFloat() - 4f), Size(b.width.toFloat() + 2f, 4f))
    }
}

private fun DrawScope.drawSuspicionMeter(level: Double) {
    val w = 220f
    val h = 8f
    val left = (size.width - w) / 2f
    val top = 16f
    drawRoundRect(Color(0x66000000), Offset(left - 2f, top - 2f), Size(w + 4f, h + 4f), CornerRadius(6f))
    if (level > 0.0) {
        val t = level.coerceIn(0.0, 1.0).toFloat()
        val color = Color(red = 1f, green = 0.9f - 0.65f * t, blue = 0.3f - 0.2f * t)
        drawRoundRect(color, Offset(left, top), Size(w * t, h), CornerRadius(4f))
    }
}

// ---------------------------------------------------------------------------------------------
// App shell
// ---------------------------------------------------------------------------------------------

fun main() = application {
    val keyboard = remember { Keyboard() }
    var game by remember { mutableStateOf(Game()) }
    var frame by remember { mutableLongStateOf(0L) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Stealth Toolkit Demo",
        state = rememberWindowState(width = 1200.dp, height = 660.dp),
        onKeyEvent = { event ->
            when (event.type) {
                KeyEventType.KeyDown -> keyboard.down(event.key)
                KeyEventType.KeyUp -> keyboard.up(event.key)
            }
            true
        },
    ) {
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0.0 else ((now - last) / 1e9).coerceAtMost(1.0 / 30.0)
                    last = now
                    if (game.restartRequested) game = Game()
                    game.viewport = DeviceScreen.viewport
                    if (dt > 0.0) game.step(dt, keyboard.controls())
                    keyboard.endFrame()
                    frame++
                }
            }
        }

        val density = LocalDensity.current.density
        Box(
            Modifier.fillMaxSize().onSizeChanged {
                // Publish the window in dp; ScreenLayout turns that into a virtual canvas that
                // matches the window's aspect without letterboxing.
                DeviceScreen.publish(it.width / density.toDouble(), it.height / density.toDouble())
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") frame // redraw every tick
                val g = game
                val vp = g.viewport
                drawRect(Brush.verticalGradient(listOf(Palette.skyTop, Palette.skyBottom)))
                val scale = min(size.width / vp.width, size.height / vp.height).toFloat()
                // Pin the design canvas to the bottom: any extra height becomes sky above it.
                val offsetY = (vp.height - ScreenLayout.DESIGN_HEIGHT).toFloat()
                withTransform({
                    scale(scale, scale, pivot = Offset.Zero)
                    translate(-g.cameraX.toFloat(), offsetY)
                }) {
                    drawWorld(g)
                }
                // A cold tint over everything while the jammer runs; a violet one while invisible.
                if (g.activePowerups.isSmokeScreenActive) drawRect(Color(0x1A9AA7BD))
                if (g.activePowerups.isInvisibilityActive) drawRect(Color(0x14B889FF))
                drawSuspicionMeter(g.suspicion)
            }
            Hud(game, frame)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// HUD: status, per-section advice, the gadget quick-slot tray, and the opening briefing.
// ---------------------------------------------------------------------------------------------

private val small = TextStyle(color = Palette.hud, fontSize = 13.sp)
private val tiny = TextStyle(color = Palette.hud, fontSize = 11.sp)
private val big = TextStyle(color = Palette.hud, fontSize = 26.sp, fontWeight = FontWeight.Bold)

@Composable
private fun Hud(g: Game, frame: Long) {
    @Suppress("UNUSED_EXPRESSION") frame

    Box(Modifier.fillMaxSize().padding(16.dp)) {
        Column(Modifier.align(Alignment.TopStart)) {
            BasicText("Caught: ${g.timesCaught}", style = small)
            BasicText(
                "Checkpoints: " + when {
                    g.activePowerups.isCheckpointsActive -> "ACTIVE - you respawn at the last flag"
                    g.stockOf(PowerupType.CHECKPOINTS) > 0 -> "spent automatically if you're caught"
                    else -> "none - being caught restarts the level"
                },
                style = small,
            )
        }
        g.hint?.let { BasicText(it, Modifier.align(Alignment.TopEnd), style = small) }

        // What to do here - the skill route, and which gadget would make it easy.
        g.currentSection?.let { s ->
            if (!g.showBriefing && !g.won) {
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 22.dp).widthIn(max = 560.dp)
                        .background(Palette.panel, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(s.skill, style = small)
                    s.gadget?.let { type ->
                        val key = QUICK_SLOT.indexOf(type) + 1
                        val have = g.stockOf(type) > 0 && !g.activePowerups.isActive(type)
                        BasicText(
                            if (have) "or press $key  ${type.displayName} to ${s.gadgetUse}"
                            else "(${type.displayName} would ${s.gadgetUse} - none left)",
                            style = small.copy(color = Palette.gadget(type).copy(alpha = if (have) 1f else 0.5f)),
                        )
                    }
                }
            }
        }

        when {
            g.won -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText("Out clean.", style = big)
                BasicText("Caught ${g.timesCaught} time(s).  Enter to play again.", style = small)
            }
            g.message != null && !g.showBriefing -> BasicText(
                g.message!!, Modifier.align(Alignment.Center).background(Palette.panel, RoundedCornerShape(8.dp)).padding(10.dp),
                style = small.copy(fontSize = 15.sp),
            )
        }

        GadgetTray(g, Modifier.align(Alignment.BottomStart))
        BasicText(
            "A/D move   Space jump   S crouch (silent)   E lever",
            Modifier.align(Alignment.BottomEnd),
            style = small.copy(color = Palette.hud.copy(alpha = 0.7f)),
        )

        if (g.showBriefing) Briefing(Modifier.align(Alignment.Center))
    }
}

/** The quick-slot, as in the game: one box per gadget with its key, stock, and a drain bar while it runs. */
@Composable
private fun GadgetTray(g: Game, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        QUICK_SLOT.forEachIndexed { i, type ->
            val count = g.stockOf(type)
            val active = g.activePowerups.isActive(type)
            val color = Palette.gadget(type)
            val dim = count == 0 && !active
            Column(
                Modifier.width(96.dp).background(Palette.panel, RoundedCornerShape(6.dp))
                    .border(if (active) 2.dp else 1.dp, color.copy(alpha = if (dim) 0.25f else 0.9f), RoundedCornerShape(6.dp))
                    .padding(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(color.copy(alpha = if (dim) 0.3f else 1f), RoundedCornerShape(5.dp)))
                    Spacer(Modifier.width(5.dp))
                    BasicText("${i + 1}  ${type.shortName}", style = tiny.copy(fontWeight = FontWeight.Bold, color = Palette.hud.copy(alpha = if (dim) 0.4f else 1f)))
                }
                val status = when {
                    active && type.duration > 0 -> "%.1fs".format(g.activePowerups.getRemainingTime(type))
                    active -> "ON"
                    else -> "x$count"
                }
                BasicText(status, style = tiny.copy(color = color.copy(alpha = if (dim) 0.4f else 1f)))
                if (active && type.duration > 0) {
                    val left = (g.activePowerups.getRemainingTime(type) / type.duration).toFloat()
                    Box(Modifier.fillMaxWidth(left).height(3.dp).background(color))
                }
            }
        }
    }
}

/** The opening briefing: what each gadget does and how to fire it. */
@Composable
private fun Briefing(modifier: Modifier) {
    Column(
        modifier.widthIn(max = 620.dp).background(Palette.panel, RoundedCornerShape(10.dp))
            .border(1.dp, Palette.hud.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).padding(20.dp),
    ) {
        BasicText("Your gadgets", style = big.copy(fontSize = 22.sp))
        Spacer(Modifier.height(4.dp))
        BasicText("One of each, as if bought from the store. Press the number key to fire one - you'll be told which one fits each section.", style = small)
        Spacer(Modifier.height(12.dp))
        QUICK_SLOT.forEachIndexed { i, type -> GadgetLine("${i + 1}", type) }
        GadgetLine("auto", PowerupType.CHECKPOINTS)
        Spacer(Modifier.height(12.dp))
        BasicText("Every section can also be passed with no gadgets at all.   Press any key to start.", style = small.copy(color = Palette.hud.copy(alpha = 0.75f)))
    }
}

@Composable
private fun GadgetLine(key: String, type: PowerupType) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(key, Modifier.width(40.dp), style = small.copy(fontWeight = FontWeight.Bold))
        Box(Modifier.size(10.dp).background(Palette.gadget(type), RoundedCornerShape(5.dp)))
        Spacer(Modifier.width(8.dp))
        BasicText(type.displayName, Modifier.width(170.dp), style = small.copy(color = Palette.gadget(type), fontWeight = FontWeight.Bold))
        BasicText(gadgetDescription(type), style = small)
    }
}

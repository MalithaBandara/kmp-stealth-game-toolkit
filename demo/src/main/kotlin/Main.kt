import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.withFrameNanos
import io.github.malithabandara.stealthkit.actors.Guard
import io.github.malithabandara.stealthkit.actors.GuardState
import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.geometry.Vec2d
import io.github.malithabandara.stealthkit.hazards.Laser
import io.github.malithabandara.stealthkit.platforms.MovingPlatform
import io.github.malithabandara.stealthkit.sentry.Camera
import io.github.malithabandara.stealthkit.vision.VisionSystem
import kotlin.math.PI

/**
 * Everything on screen is a plain shape - no image assets anywhere - to prove the library needs
 * none. This is deliberately not "a game," just a live driver of every stealthkit piece that has
 * a visible state: a patrolling guard with its occluder-aware vision cone, a sweeping camera, a
 * laser cycling on its own timer, and a moving platform - all using the exact same update()/state
 * calls a real game would.
 */

private val WALL = Rect(420.0, 190.0, 36.0, 230.0)

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "KMP Stealth Game Toolkit - demo") {
        MaterialTheme {
            DemoScreen()
        }
    }
}

@Composable
private fun DemoScreen() {
    val guard = remember {
        Guard(
            x = 40.0, y = 260.0, width = 26.0, height = 48.0,
            patrolMinX = 40.0, patrolMaxX = 360.0, speed = 55.0,
            visionRange = 220.0, visionFov = 60.0 * (PI / 180.0)
        )
    }
    val camera = remember {
        Camera.createSweeping(
            x = 640.0, y = 70.0, centerAngle = PI / 2.0, sweepAngleDelta = 40.0 * (PI / 180.0),
            sweepSpeed = 0.6, visionRange = 200.0, visionFov = 40.0 * (PI / 180.0)
        )
    }
    val laser = remember {
        Laser(id = "demo-laser", topX = 760.0, topY = 60.0, bottomX = 760.0, bottomY = 460.0, beamThickness = 5.0, activeDuration = 2.0, inactiveDuration = 1.5)
    }
    val platform = remember {
        MovingPlatform(id = "demo-platform", y = 380.0, width = 70.0, height = 16.0, minX = 500.0, maxX = 660.0, periodSeconds = 3.0)
    }

    var playerX by remember { mutableStateOf(150.0) }
    var playerY by remember { mutableStateOf(430.0) }
    var totalElapsed by remember { mutableStateOf(0.0) }
    var guardSpotted by remember { mutableStateOf(false) }
    var cameraSpotted by remember { mutableStateOf(false) }
    val held = remember { HashSet<Key>() }
    var noisePing by remember { mutableStateOf(0.0) } // seconds remaining a "noise" ring is shown

    val focusRequester = remember { FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        var lastNanos = -1L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos >= 0L) {
                    val dt = ((nanos - lastNanos) / 1_000_000_000.0).coerceAtMost(0.1)
                    totalElapsed += dt

                    val speed = 150.0
                    if (Key.DirectionLeft in held || Key.A in held) playerX -= speed * dt
                    if (Key.DirectionRight in held || Key.D in held) playerX += speed * dt
                    if (Key.DirectionUp in held || Key.W in held) playerY -= speed * dt
                    if (Key.DirectionDown in held || Key.S in held) playerY += speed * dt
                    playerX = playerX.coerceIn(10.0, 880.0)
                    playerY = playerY.coerceIn(60.0, 470.0)

                    val occluders = listOf(WALL)
                    val playerPoint = listOf(Vec2d(playerX, playerY))

                    guard.update(dt, obstacles = occluders)
                    camera.update(dt)
                    laser.update(totalElapsed)
                    platform.update(dt, totalElapsed)

                    // Wiring detection to behavior is the consumer's job - VisionSystem only
                    // answers "can it see the target right now."
                    guardSpotted = VisionSystem.isPlayerSpotted(guard, playerPoint, occluders)
                    if (guardSpotted && guard.state == GuardState.PATROL) {
                        guard.startInvestigating(playerX)
                    } else if (guardSpotted && guard.state == GuardState.INVESTIGATING) {
                        guard.onPlayerSpottedWhileInvestigating(playerX)
                    }

                    cameraSpotted = VisionSystem.isPlayerSpotted(camera, playerPoint, occluders)
                    if (cameraSpotted) camera.onPlayerSpotted() else camera.onVisualLost()

                    if (noisePing > 0.0) noisePing = (noisePing - dt).coerceAtLeast(0.0)
                }
                lastNanos = nanos
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101216))
            .focusable()
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        held.add(event.key)
                        if (event.key == Key.Spacebar) {
                            guard.onNoiseHeard(playerX)
                            noisePing = 0.6
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        held.remove(event.key)
                        true
                    }
                    else -> false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Arrow keys / WASD to move  -  Space to make noise (guard investigates)  -  " +
                "no image assets anywhere on this screen",
            color = Color(0xFFB0B6C0),
            fontSize = 13.sp,
            modifier = Modifier.padding(10.dp)
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Static occluder wall
            drawRect(
                color = Color(0xFF3A3F4B),
                topLeft = Offset(WALL.x.toFloat(), WALL.y.toFloat()),
                size = Size(WALL.width.toFloat(), WALL.height.toFloat())
            )

            // Moving platform
            drawRect(
                color = Color(0xFF6B7280),
                topLeft = Offset(platform.x.toFloat(), platform.y.toFloat()),
                size = Size(platform.width.toFloat(), platform.height.toFloat())
            )

            // Guard vision cone (occluder-aware - it will bend around WALL)
            val guardCone = VisionSystem.computeVisionPolygon(
                origin = guard.eyePosition,
                facingAngle = guard.facingAngle,
                range = guard.visionRange,
                fov = guard.visionFov,
                occluders = listOf(WALL)
            )
            drawPath(
                path = polygonPath(guardCone),
                color = if (guardSpotted) Color(0x55FF5252) else Color(0x33FFD54F)
            )

            // Guard body + a short facing line ("nose")
            val guardColor = when {
                guardSpotted -> Color(0xFFFF5252)
                guard.state == GuardState.INVESTIGATING -> Color(0xFFFFB74D)
                else -> Color(0xFF64B5F6)
            }
            drawRect(
                color = guardColor,
                topLeft = Offset(guard.x.toFloat(), guard.y.toFloat()),
                size = Size(guard.width.toFloat(), guard.height.toFloat())
            )
            val guardEye = guard.eyePosition
            drawLine(
                color = guardColor,
                start = Offset(guardEye.x.toFloat(), guardEye.y.toFloat()),
                end = Offset(
                    (guardEye.x + kotlin.math.cos(guard.facingAngle) * 18.0).toFloat(),
                    (guardEye.y + kotlin.math.sin(guard.facingAngle) * 18.0).toFloat()
                ),
                strokeWidth = 3f
            )

            // Camera cone + body
            val cameraCone = VisionSystem.computeVisionPolygon(
                origin = camera.eyePosition,
                facingAngle = camera.facingAngle,
                range = camera.visionRange,
                fov = camera.visionFov,
                occluders = listOf(WALL)
            )
            drawPath(
                path = polygonPath(cameraCone),
                color = if (cameraSpotted) Color(0x55FF5252) else Color(0x33FF8A65)
            )
            val camCenter = camera.center
            drawCircle(
                color = if (cameraSpotted) Color(0xFFFF5252) else Color(0xFFFF8A65),
                radius = 10f,
                center = Offset(camCenter.x.toFloat(), camCenter.y.toFloat())
            )

            // Laser beam - red while active, dim while safe to cross
            drawLine(
                color = if (laser.isActive) Color(0xFFFF3B30) else Color(0xFF4A4F5A),
                start = Offset(laser.topX.toFloat(), laser.topY.toFloat()),
                end = Offset(laser.bottomX.toFloat(), laser.bottomY.toFloat()),
                strokeWidth = laser.beamThickness.toFloat()
            )

            // A noise ring when Space was just pressed
            if (noisePing > 0.0) {
                drawCircle(
                    color = Color(0x88FFFFFF),
                    radius = (30.0 * (1.0 - noisePing / 0.6)).toFloat() + 10f,
                    center = Offset(playerX.toFloat(), playerY.toFloat()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
            }

            // Player
            drawCircle(
                color = Color(0xFF4CD964),
                radius = 10f,
                center = Offset(playerX.toFloat(), playerY.toFloat())
            )
        }
        Text(
            "guard: ${guard.state}" + (if (guardSpotted) " (SPOTTED)" else "") +
                "   camera: " + (if (cameraSpotted) "SPOTTED" else if (camera.isPausedFromDetection) "paused" else "sweeping") +
                "   laser: " + (if (laser.isActive) "ACTIVE" else "safe"),
            color = Color(0xFF8A93A6),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

private fun polygonPath(points: List<Vec2d>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x.toFloat(), points[0].y.toFloat())
    for (p in points.drop(1)) path.lineTo(p.x.toFloat(), p.y.toFloat())
    path.close()
    return path
}

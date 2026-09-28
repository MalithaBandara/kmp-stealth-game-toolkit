import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import io.github.malithabandara.stealthkit.actors.Guard
import io.github.malithabandara.stealthkit.actors.GuardState
import io.github.malithabandara.stealthkit.follow.SmoothFollow
import io.github.malithabandara.stealthkit.geometry.Rect
import io.github.malithabandara.stealthkit.geometry.Vec2d
import io.github.malithabandara.stealthkit.hazards.Laser
import io.github.malithabandara.stealthkit.layout.ScreenLayout
import io.github.malithabandara.stealthkit.physics.BoxObstacle
import io.github.malithabandara.stealthkit.physics.BoxPhysics
import io.github.malithabandara.stealthkit.platforms.ConveyorCrate
import io.github.malithabandara.stealthkit.platforms.ConveyorDef
import io.github.malithabandara.stealthkit.platforms.HookCrate
import io.github.malithabandara.stealthkit.platforms.MovingPlatform
import io.github.malithabandara.stealthkit.sentry.Camera
import io.github.malithabandara.stealthkit.terrain.RoughBlock
import io.github.malithabandara.stealthkit.vision.VisionSystem
import kotlin.math.PI

/**
 * A live driver of every piece in the library, written the way a real consumer would use it after
 * adding the JitPack dependency (see this module's own build.gradle.kts) - not a tech demo reel,
 * an actual (if tiny) side-scrolling level: a wide world, a camera that follows the player, guards
 * and a sentry camera with occluder-aware vision, a laser gate, a sliding platform over a gap, a
 * conveyor belt carrying crates, and a load on a hook that can be cut loose to tumble via real
 * physics. Every shape on screen is drawn from plain rects/lines/circles/paths - no image assets
 * anywhere in this module.
 */

private const val WORLD_WIDTH = 2200.0
private const val GROUND_Y = 460.0 // the world-y every ground surface's TOP sits on

// --- The level, laid out left to right -------------------------------------------------------
private val GROUND_A = Rect(0.0, GROUND_Y, 430.0, 160.0)
private val WALL = Rect(430.0, 300.0, 30.0, 160.0) // occluder between the guard and the gap
private val GROUND_B = Rect(560.0, GROUND_Y, 440.0, 160.0)
private val CONVEYOR = ConveyorDef(bounds = Rect(1000.0, GROUND_Y - 20.0, 300.0, 20.0), speed = 40.0)
private val GROUND_C = Rect(1300.0, GROUND_Y, 400.0, 160.0)
private val GROUND_D = Rect(1700.0, GROUND_Y, 500.0, 160.0)
private val GROUND_BLOCKS = listOf(GROUND_A, GROUND_B, GROUND_C, GROUND_D)

/** What a real level would hand [BoxPhysics] as static geometry: flat ground, not the bumpy edge. */
private val PHYSICS_FLOOR = BoxObstacle(rect = Rect(GROUND_C.x, GROUND_Y, GROUND_D.right - GROUND_C.x, 300.0))

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "KMP Stealth Game Toolkit - demo") {
        MaterialTheme {
            DemoScreen()
        }
    }
}

@Composable
private fun DemoScreen() {
    // --- actors.Guard / sentry.Camera / hazards.Laser / platforms.MovingPlatform -------------
    val guard = remember {
        Guard(
            x = 40.0, y = GROUND_Y - 48.0, width = 26.0, height = 48.0,
            patrolMinX = 40.0, patrolMaxX = 380.0, speed = 55.0,
            visionRange = 220.0, visionFov = 60.0 * (PI / 180.0)
        )
    }
    val camera = remember {
        Camera.createSweeping(
            x = 760.0, y = 300.0, centerAngle = PI / 2.0, sweepAngleDelta = 45.0 * (PI / 180.0),
            sweepSpeed = 0.6, visionRange = 220.0, visionFov = 40.0 * (PI / 180.0)
        )
    }
    val laser = remember {
        Laser(id = "demo-laser", topX = 1600.0, topY = 200.0, bottomX = 1600.0, bottomY = GROUND_Y, beamThickness = 5.0, activeDuration = 2.0, inactiveDuration = 1.5)
    }
    val platform = remember {
        MovingPlatform(id = "demo-platform", y = GROUND_Y - 16.0, width = 70.0, height = 16.0, minX = 460.0, maxX = 560.0, periodSeconds = 3.0)
    }

    // --- platforms.ConveyorCrate ---------------------------------------------------------------
    val crates = remember {
        listOf(
            ConveyorCrate(initialX = 1030.0, initialY = GROUND_Y - 20.0 - 24.0, width = 24.0, height = 24.0, loopMinX = CONVEYOR.bounds.x - 24.0, loopMaxX = CONVEYOR.bounds.right, shouldLoop = true),
            ConveyorCrate(initialX = 1120.0, initialY = GROUND_Y - 20.0 - 24.0, width = 24.0, height = 24.0, loopMinX = CONVEYOR.bounds.x - 24.0, loopMaxX = CONVEYOR.bounds.right, shouldLoop = true),
            ConveyorCrate(initialX = 1210.0, initialY = GROUND_Y - 20.0 - 24.0, width = 24.0, height = 24.0, loopMinX = CONVEYOR.bounds.x - 24.0, loopMaxX = CONVEYOR.bounds.right, shouldLoop = true),
        )
    }

    // --- platforms.HookCrate + physics.BoxPhysics ----------------------------------------------
    val hookCrate = remember {
        val w = 26.0; val h = 26.0
        val hookX = 1450.0; val hookY = 150.0
        val ropeLength = 130.0
        HookCrate(
            id = "demo-hook", hook = Rect(hookX - 2.0, hookY, 4.0, 4.0),
            bounds = Rect(hookX - w / 2.0, hookY + ropeLength, w, h),
            ropeLength = ropeLength, sweepX = 40.0, sweepPeriodSeconds = 4.0, physical = true
        )
    }
    var hookCrateLanded by remember { mutableStateOf(false) }

    // --- terrain.RoughBlock - each ground segment gets its own deterministic bumps -------------
    val groundOutlines = remember {
        GROUND_BLOCKS.associateWith { block ->
            RoughBlock.outline(width = block.width, height = block.height, seed = (block.x * 47.0).toLong())
        }
    }

    // --- follow.SmoothFollow - the "camera" that decides which slice of the world is visible ---
    val cameraFollow = remember { SmoothFollow(smoothTime = 0.15) }

    var playerX by remember { mutableStateOf(80.0) }
    var playerY by remember { mutableStateOf(GROUND_Y - 10.0) }
    var totalElapsed by remember { mutableStateOf(0.0) }
    var guardSpotted by remember { mutableStateOf(false) }
    var cameraSpotted by remember { mutableStateOf(false) }
    val held = remember { HashSet<Key>() }
    var noisePing by remember { mutableStateOf(0.0) }
    var canvasSize by remember { mutableStateOf(Size(1040f, 480f)) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(Unit) {
        cameraFollow.snapTo(playerX)
        var lastNanos = -1L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos >= 0L) {
                    val dt = ((nanos - lastNanos) / 1_000_000_000.0).coerceAtMost(0.1)
                    totalElapsed += dt

                    val speed = 180.0
                    if (Key.DirectionLeft in held || Key.A in held) playerX -= speed * dt
                    if (Key.DirectionRight in held || Key.D in held) playerX += speed * dt
                    if (Key.DirectionUp in held || Key.W in held) playerY -= speed * dt
                    if (Key.DirectionDown in held || Key.S in held) playerY += speed * dt
                    playerX = playerX.coerceIn(10.0, WORLD_WIDTH - 10.0)
                    playerY = playerY.coerceIn(GROUND_Y - 220.0, GROUND_Y - 10.0)

                    // follow.SmoothFollow tracking the player, exactly like a gameplay camera would.
                    cameraFollow.update(target = playerX, dt = dt, snapIfFartherThan = 900.0)

                    val occluders = listOf(WALL)
                    val playerPoint = listOf(Vec2d(playerX, playerY))

                    guard.update(dt, obstacles = occluders)
                    camera.update(dt)
                    laser.update(totalElapsed)
                    platform.update(dt, totalElapsed)
                    for (crate in crates) crate.update(dx = CONVEYOR.speed * dt, totalElapsedSeconds = totalElapsed)

                    if (!hookCrateLanded) {
                        hookCrate.advanceSweep(dt)
                        val body = hookCrate.body
                        if (body != null) {
                            val touched = BoxPhysics.step(body, dt, obstacles = listOf(PHYSICS_FLOOR))
                            hookCrate.syncBodyBounds()
                            if (touched) {
                                val settled = BoxPhysics.settleIfResting(
                                    body, dt, supported = true, supportTop = PHYSICS_FLOOR.rect.top
                                )
                                if (settled) hookCrateLanded = true
                            }
                        }
                    }

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
                        when (event.key) {
                            Key.Spacebar -> {
                                guard.onNoiseHeard(playerX)
                                noisePing = 0.6
                            }
                            Key.C -> hookCrate.detach()
                            else -> {}
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
            "Arrow/WASD move  -  Space: noise  -  C: cut the hanging crate loose  -  " +
                "resize the window to see layout.ScreenLayout adapt",
            color = Color(0xFFB0B6C0),
            fontSize = 12.sp,
            modifier = Modifier.padding(8.dp)
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            canvasSize = size

            // layout.ScreenLayout - the same call a real game makes every time its window size
            // changes, sizing one virtual canvas to this device without letterboxing or cropping.
            val viewport = ScreenLayout.viewportFor(size.width.toDouble(), size.height.toDouble())
            val zoom = (size.width / viewport.width).toFloat()

            // The visible slice of the world, centred on the smoothed follow position.
            val camLeft = (cameraFollow.position - viewport.width / 2.0)
                .coerceIn(0.0, (WORLD_WIDTH - viewport.width).coerceAtLeast(0.0))

            fun px(worldX: Double): Float = ((worldX - camLeft) * zoom).toFloat()
            fun py(worldY: Double): Float = (worldY * zoom).toFloat()
            fun plen(worldLen: Double): Float = (worldLen * zoom).toFloat()

            fun roughPath(block: Rect): Path {
                val outline = groundOutlines.getValue(block)
                val path = Path()
                path.moveTo(px(block.x + outline[0].x), py(block.y + outline[0].y))
                for (p in outline.drop(1)) path.lineTo(px(block.x + p.x), py(block.y + p.y))
                path.close()
                return path
            }

            // --- terrain.RoughBlock ground ----------------------------------------------------
            for (block in GROUND_BLOCKS) {
                drawPath(roughPath(block), color = Color(0xFF2E323C))
            }

            // --- platforms.Conveyor ------------------------------------------------------------
            drawRect(
                color = Color(0xFF4A3F2E),
                topLeft = Offset(px(CONVEYOR.bounds.x), py(CONVEYOR.bounds.y)),
                size = Size(plen(CONVEYOR.bounds.width), plen(CONVEYOR.bounds.height))
            )
            for (crate in crates) {
                drawRect(
                    color = Color(0xFFCE9A5C),
                    topLeft = Offset(px(crate.x), py(crate.y)),
                    size = Size(plen(crate.width), plen(crate.height))
                )
            }

            // --- occluder wall -------------------------------------------------------------
            drawRect(color = Color(0xFF3A3F4B), topLeft = Offset(px(WALL.x), py(WALL.y)), size = Size(plen(WALL.width), plen(WALL.height)))

            // --- platforms.MovingPlatform --------------------------------------------------
            drawRect(color = Color(0xFF6B7280), topLeft = Offset(px(platform.x), py(platform.y)), size = Size(plen(platform.width), plen(platform.height)))

            // --- platforms.HookCrate + physics.BoxPhysics -----------------------------------
            run {
                val body = hookCrate.body
                if (body == null) {
                    // still hanging: draw the rope then the crate
                    drawLine(
                        color = Color(0xFF5A5F6B),
                        start = Offset(px(hookCrate.ropeTopX), py(hookCrate.ropeTopY)),
                        end = Offset(px(hookCrate.drawCenterX), py(hookCrate.drawCenterY)),
                        strokeWidth = 1.5f
                    )
                }
                val half = 13.0
                drawRect(
                    color = if (hookCrateLanded) Color(0xFF8D6E63) else Color(0xFFA1887F),
                    topLeft = Offset(px(hookCrate.drawCenterX - half), py(hookCrate.drawCenterY - half)),
                    size = Size(plen(26.0), plen(26.0))
                )
            }

            // --- vision.VisionSystem cones + actors.Guard --------------------------------------
            val guardCone = VisionSystem.computeVisionPolygon(
                origin = guard.eyePosition, facingAngle = guard.facingAngle,
                range = guard.visionRange, fov = guard.visionFov, occluders = listOf(WALL)
            )
            val guardConePath = Path().apply {
                moveTo(px(guardCone[0].x), py(guardCone[0].y))
                for (p in guardCone.drop(1)) lineTo(px(p.x), py(p.y))
                close()
            }
            drawPath(guardConePath, color = if (guardSpotted) Color(0x55FF5252) else Color(0x33FFD54F))

            val guardColor = when {
                guardSpotted -> Color(0xFFFF5252)
                guard.state == GuardState.INVESTIGATING -> Color(0xFFFFB74D)
                else -> Color(0xFF64B5F6)
            }
            drawRect(color = guardColor, topLeft = Offset(px(guard.x), py(guard.y)), size = Size(plen(guard.width), plen(guard.height)))
            val guardEye = guard.eyePosition
            drawLine(
                color = guardColor,
                start = Offset(px(guardEye.x), py(guardEye.y)),
                end = Offset(px(guardEye.x + kotlin.math.cos(guard.facingAngle) * 18.0), py(guardEye.y + kotlin.math.sin(guard.facingAngle) * 18.0)),
                strokeWidth = 3f
            )

            // --- vision.VisionSystem cone + sentry.Camera --------------------------------------
            val cameraCone = VisionSystem.computeVisionPolygon(
                origin = camera.eyePosition, facingAngle = camera.facingAngle,
                range = camera.visionRange, fov = camera.visionFov, occluders = listOf(WALL)
            )
            val cameraConePath = Path().apply {
                moveTo(px(cameraCone[0].x), py(cameraCone[0].y))
                for (p in cameraCone.drop(1)) lineTo(px(p.x), py(p.y))
                close()
            }
            drawPath(cameraConePath, color = if (cameraSpotted) Color(0x55FF5252) else Color(0x33FF8A65))
            val camCenter = camera.center
            drawCircle(
                color = if (cameraSpotted) Color(0xFFFF5252) else Color(0xFFFF8A65),
                radius = plen(10.0), center = Offset(px(camCenter.x), py(camCenter.y))
            )

            // --- hazards.Laser -------------------------------------------------------------
            drawLine(
                color = if (laser.isActive) Color(0xFFFF3B30) else Color(0xFF4A4F5A),
                start = Offset(px(laser.topX), py(laser.topY)),
                end = Offset(px(laser.bottomX), py(laser.bottomY)),
                strokeWidth = plen(laser.beamThickness)
            )

            // Noise ring
            if (noisePing > 0.0) {
                drawCircle(
                    color = Color(0x88FFFFFF),
                    radius = plen(30.0 * (1.0 - noisePing / 0.6)) + 10f,
                    center = Offset(px(playerX), py(playerY)),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
            }

            // Player
            drawCircle(color = Color(0xFF4CD964), radius = plen(10.0), center = Offset(px(playerX), py(playerY)))
        }
        Text(
            "guard: ${guard.state}${if (guardSpotted) " (SPOTTED)" else ""}" +
                "   camera: ${if (cameraSpotted) "SPOTTED" else if (camera.isPausedFromDetection) "paused" else "sweeping"}" +
                "   laser: ${if (laser.isActive) "ACTIVE" else "safe"}" +
                "   hook: ${if (hookCrateLanded) "landed" else if (hookCrate.isDetached) "falling" else "hanging"}" +
                "   viewport: ${canvasSize.width.toInt()}x${canvasSize.height.toInt()}px",
            color = Color(0xFF8A93A6),
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

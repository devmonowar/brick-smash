package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.example.engine.BrickSmashEngine
import com.example.model.*
import kotlin.math.sin

@Composable
fun GameCanvas(
    engine: BrickSmashEngine,
    playStatus: PlayStatus,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Pulsing animation for "TAP TO LAUNCH"
    var pulseTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastTime = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                val dt = (now - lastTime) / 1_000_000_000f
                lastTime = now
                pulseTime = (pulseTime + dt * 4f) % (2f * Math.PI.toFloat())
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    onTap()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val logicalDragX = dragAmount.x / scale
                    engine.movePaddleBy(logicalDragX)
                }
            }
    ) {
        // Calculate uniform scale to fit 1000x1600 logical area inside available canvas
        val logicalW = BrickSmashEngine.ARENA_WIDTH
        val logicalH = BrickSmashEngine.ARENA_HEIGHT

        val scaleX = size.width / logicalW
        val scaleY = size.height / logicalH
        val currentScale = minOf(scaleX, scaleY)
        scale = currentScale

        val renderW = logicalW * currentScale
        val renderH = logicalH * currentScale

        val currentOffsetX = (size.width - renderW) / 2f
        val currentOffsetY = (size.height - renderH) / 2f
        offsetX = currentOffsetX
        offsetY = currentOffsetY

        // Coordinate conversion helper
        fun toScreen(lx: Float, ly: Float): Offset {
            return Offset(currentOffsetX + lx * currentScale, currentOffsetY + ly * currentScale)
        }

        // 1. Draw Deep Cyber Space Background
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF060919),
                    Color(0xFF0A0F2C),
                    Color(0xFF080D24)
                )
            ),
            size = size
        )

        // Draw Arena Boundaries Glow Lines
        val topWall = toScreen(0f, BrickSmashEngine.TOP_WALL_Y)
        val leftWall = toScreen(20f, BrickSmashEngine.TOP_WALL_Y)
        val rightWall = toScreen(logicalW - 20f, BrickSmashEngine.TOP_WALL_Y)
        val bottomArena = toScreen(0f, logicalH)

        // Subtle side borders
        drawLine(
            color = Color(0x3300E5FF),
            start = Offset(leftWall.x, topWall.y),
            end = Offset(leftWall.x, bottomArena.y),
            strokeWidth = 3f * currentScale
        )
        drawLine(
            color = Color(0x3300E5FF),
            start = Offset(rightWall.x, topWall.y),
            end = Offset(rightWall.x, bottomArena.y),
            strokeWidth = 3f * currentScale
        )
        drawLine(
            color = Color(0x4400E5FF),
            start = Offset(leftWall.x, topWall.y),
            end = Offset(rightWall.x, topWall.y),
            strokeWidth = 3f * currentScale
        )

        // 2. Draw Bricks
        for (brick in engine.bricks) {
            if (brick.isDestroyed) continue

            val bTopLeft = toScreen(brick.x, brick.y)
            val bSize = Size(brick.width * currentScale, brick.height * currentScale)
            val corner = CornerRadius(6f * currentScale, 6f * currentScale)

            when (brick.type) {
                BrickType.STEEL -> {
                    // Metallic steel brick
                    drawRoundRect(
                        color = Color(0xFF455A64),
                        topLeft = bTopLeft,
                        size = bSize,
                        cornerRadius = corner
                    )
                    // Metallic inner gradient
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF90A4AE), Color(0xFF37474F)),
                            start = bTopLeft,
                            end = Offset(bTopLeft.x + bSize.width, bTopLeft.y + bSize.height)
                        ),
                        topLeft = Offset(bTopLeft.x + 2f * currentScale, bTopLeft.y + 2f * currentScale),
                        size = Size(bSize.width - 4f * currentScale, bSize.height - 4f * currentScale),
                        cornerRadius = corner
                    )
                    // Rivets
                    drawCircle(
                        color = Color(0xFFCFD8DC),
                        radius = 2.5f * currentScale,
                        center = Offset(bTopLeft.x + 8f * currentScale, bTopLeft.y + bSize.height / 2f)
                    )
                    drawCircle(
                        color = Color(0xFFCFD8DC),
                        radius = 2.5f * currentScale,
                        center = Offset(bTopLeft.x + bSize.width - 8f * currentScale, bTopLeft.y + bSize.height / 2f)
                    )
                }
                BrickType.EXPLOSIVE -> {
                    // Explosive warning brick (Red/Orange pulsating)
                    val pulse = (sin(pulseTime.toDouble() * 2) * 0.2 + 0.8).toFloat()
                    drawRoundRect(
                        color = Color(0xFFFF1744).copy(alpha = pulse),
                        topLeft = bTopLeft,
                        size = bSize,
                        cornerRadius = corner
                    )
                    // Inner warning symbol
                    drawCircle(
                        color = Color(0xFFFFEA00),
                        radius = 6f * currentScale,
                        center = Offset(bTopLeft.x + bSize.width / 2f, bTopLeft.y + bSize.height / 2f)
                    )
                }
                BrickType.BONUS -> {
                    // Sparkling Purple/Magenta Bonus Brick
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFFF007F), Color(0xFF7C4DFF)),
                            start = bTopLeft,
                            end = Offset(bTopLeft.x + bSize.width, bTopLeft.y + bSize.height)
                        ),
                        topLeft = bTopLeft,
                        size = bSize,
                        cornerRadius = corner
                    )
                    // Center Star/Diamond
                    drawCircle(
                        color = Color(0xFFFFFFFF),
                        radius = 5f * currentScale,
                        center = Offset(bTopLeft.x + bSize.width / 2f, bTopLeft.y + bSize.height / 2f)
                    )
                }
                BrickType.HARD -> {
                    // Hardened brick showing durability
                    val dmgAlpha = (brick.hitsRemaining.toFloat() / brick.maxHits.toFloat()).coerceIn(0.4f, 1f)
                    drawRoundRect(
                        color = brick.baseColor.copy(alpha = dmgAlpha),
                        topLeft = bTopLeft,
                        size = bSize,
                        cornerRadius = corner
                    )
                    // 3D Bevel highlight
                    drawRoundRect(
                        color = Color(0x66FFFFFF),
                        topLeft = bTopLeft,
                        size = Size(bSize.width, 3f * currentScale),
                        cornerRadius = corner
                    )
                    // Durability number text
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 18f * currentScale
                            isAntiAlias = true
                            typeface = Typeface.DEFAULT_BOLD
                            textAlign = Paint.Align.CENTER
                        }
                        val cx = bTopLeft.x + bSize.width / 2f
                        val cy = bTopLeft.y + bSize.height / 2f + (6f * currentScale)
                        canvas.nativeCanvas.drawText("${brick.hitsRemaining}", cx, cy, paint)
                    }
                }
                BrickType.NORMAL -> {
                    // Standard glowing arcade brick
                    drawRoundRect(
                        color = brick.baseColor,
                        topLeft = bTopLeft,
                        size = bSize,
                        cornerRadius = corner
                    )
                    // Top highlight for 3D effect
                    drawRoundRect(
                        color = Color(0x55FFFFFF),
                        topLeft = bTopLeft,
                        size = Size(bSize.width, 4f * currentScale),
                        cornerRadius = corner
                    )
                    // Border line
                    drawRoundRect(
                        color = Color(0x33000000),
                        topLeft = Offset(bTopLeft.x + 1f, bTopLeft.y + bSize.height - 3f * currentScale),
                        size = Size(bSize.width, 3f * currentScale),
                        cornerRadius = corner
                    )
                }
            }
        }

        // 3. Draw Falling Power-Ups
        for (powerUp in engine.powerUps) {
            val pPos = toScreen(powerUp.x, powerUp.y)
            val pRadius = powerUp.radius * currentScale

            // Glowing Capsule
            drawCircle(
                color = powerUp.type.color.copy(alpha = 0.4f),
                radius = pRadius * 1.5f,
                center = pPos
            )
            drawCircle(
                color = powerUp.type.color,
                radius = pRadius,
                center = pPos
            )
            drawCircle(
                color = Color.White,
                radius = pRadius * 0.4f,
                center = Offset(pPos.x - pRadius * 0.3f, pPos.y - pRadius * 0.3f)
            )

            // Label initial
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 14f * currentScale
                    isAntiAlias = true
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }
                val letter = when (powerUp.type) {
                    PowerUpType.WIDE_PADDLE -> "W"
                    PowerUpType.SLOW_BALL -> "S"
                    PowerUpType.MULTI_BALL -> "M"
                    PowerUpType.EXTRA_LIFE -> "+"
                }
                canvas.nativeCanvas.drawText(letter, pPos.x, pPos.y + 5f * currentScale, paint)
            }
        }

        // 4. Draw Paddle
        val paddle = engine.paddle
        val paddleTopLeft = toScreen(paddle.x - paddle.currentWidth / 2f, paddle.y - paddle.height / 2f)
        val paddleSize = Size(paddle.currentWidth * currentScale, paddle.height * currentScale)
        val paddleCorner = CornerRadius(12f * currentScale, 12f * currentScale)

        // Outer Neon Glow
        drawRoundRect(
            color = Color(0x4000E5FF),
            topLeft = Offset(paddleTopLeft.x - 4f * currentScale, paddleTopLeft.y - 4f * currentScale),
            size = Size(paddleSize.width + 8f * currentScale, paddleSize.height + 8f * currentScale),
            cornerRadius = CornerRadius(16f * currentScale, 16f * currentScale)
        )
        // Paddle Body
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF00E5FF), Color(0xFF0091EA)),
                startY = paddleTopLeft.y,
                endY = paddleTopLeft.y + paddleSize.height
            ),
            topLeft = paddleTopLeft,
            size = paddleSize,
            cornerRadius = paddleCorner
        )
        // White Core Highlight
        drawRoundRect(
            color = Color(0xDDFFFFFF),
            topLeft = Offset(paddleTopLeft.x + 12f * currentScale, paddleTopLeft.y + 4f * currentScale),
            size = Size(paddleSize.width - 24f * currentScale, 4f * currentScale),
            cornerRadius = CornerRadius(2f * currentScale, 2f * currentScale)
        )

        // 5. Draw Balls
        for (ball in engine.balls) {
            val ballCenter = toScreen(ball.x, ball.y)
            val ballRadius = ball.radius * currentScale

            // Ball outer glow
            drawCircle(
                color = Color(0x5500E5FF),
                radius = ballRadius * 1.6f,
                center = ballCenter
            )
            // Ball solid body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFF00E5FF), Color(0xFF00B0FF)),
                    center = Offset(ballCenter.x - ballRadius * 0.3f, ballCenter.y - ballRadius * 0.3f),
                    radius = ballRadius
                ),
                radius = ballRadius,
                center = ballCenter
            )
        }

        // 6. Draw Particles
        for (particle in engine.particles) {
            val pPos = toScreen(particle.x, particle.y)
            drawCircle(
                color = particle.color.copy(alpha = particle.alpha),
                radius = particle.radius * currentScale,
                center = pPos
            )
        }

        // 7. Draw Floating Score Popups
        for (popup in engine.scorePopups) {
            val pos = toScreen(popup.x, popup.y)
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = android.graphics.Color.argb(
                        (popup.alpha * 255).toInt(),
                        (popup.color.red * 255).toInt(),
                        (popup.color.green * 255).toInt(),
                        (popup.color.blue * 255).toInt()
                    )
                    textSize = 22f * currentScale
                    isAntiAlias = true
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }
                canvas.nativeCanvas.drawText(popup.text, pos.x, pos.y, paint)
            }
        }

        // 8. "TAP TO LAUNCH" Pulsing Indicator when ball is attached
        if (playStatus == PlayStatus.READY_TO_LAUNCH) {
            val pulse = (sin(pulseTime.toDouble()) * 0.35 + 0.65).toFloat()
            val textY = toScreen(0f, paddle.y - 65f).y

            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = android.graphics.Color.argb(
                        (pulse * 255).toInt(),
                        0,
                        229,
                        255
                    )
                    textSize = 28f * currentScale
                    isAntiAlias = true
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }
                val centerX = size.width / 2f
                canvas.nativeCanvas.drawText("TAP TO LAUNCH", centerX, textY, paint)
            }
        }
    }
}

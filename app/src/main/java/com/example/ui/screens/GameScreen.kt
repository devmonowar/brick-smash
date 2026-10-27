package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SoundManager
import com.example.data.GamePreferences
import com.example.engine.BrickSmashEngine
import com.example.engine.LevelData
import com.example.model.PlayStatus
import com.example.ui.components.GameCanvas
import com.example.ui.components.GameHUD
import com.example.ui.dialogs.GameOverDialog
import com.example.ui.dialogs.LevelCompleteDialog
import com.example.ui.dialogs.PauseDialog
import kotlinx.coroutines.isActive

@Composable
fun GameScreen(
    engine: BrickSmashEngine,
    soundManager: SoundManager,
    preferences: GamePreferences,
    onExitToMainMenu: () -> Unit
) {
    val playStatus by engine.playStatus.collectAsState()
    val level by engine.currentLevel.collectAsState()
    val score by engine.score.collectAsState()
    val bestScore by engine.bestScore.collectAsState()
    val lives by engine.lives.collectAsState()
    val combo by engine.combo.collectAsState()
    val activePowerUps by engine.activePowerUps.collectAsState()

    var isSoundEnabled by remember { mutableStateOf(soundManager.isSoundEnabled) }
    var isHapticsEnabled by remember { mutableStateOf(soundManager.isHapticsEnabled) }

    // Intercept Back button: pause game if in play, otherwise return to menu
    BackHandler {
        if (playStatus == PlayStatus.BALL_IN_PLAY || playStatus == PlayStatus.READY_TO_LAUNCH) {
            engine.pauseGame()
        } else {
            onExitToMainMenu()
        }
    }

    // Continuous 60+ FPS Game Loop
    LaunchedEffect(playStatus) {
        if (playStatus == PlayStatus.BALL_IN_PLAY || playStatus == PlayStatus.READY_TO_LAUNCH) {
            var lastNanos = System.nanoTime()
            while (isActive && (playStatus == PlayStatus.BALL_IN_PLAY || playStatus == PlayStatus.READY_TO_LAUNCH)) {
                withFrameNanos { nowNanos ->
                    val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    lastNanos = nowNanos
                    engine.update(dt)
                }
            }
        }
    }

    // Directional control state for on-screen touch buttons
    var isSteeringLeft by remember { mutableStateOf(false) }
    var isSteeringRight by remember { mutableStateOf(false) }

    LaunchedEffect(isSteeringLeft, isSteeringRight) {
        var lastTime = System.nanoTime()
        while (isActive && (isSteeringLeft || isSteeringRight)) {
            withFrameNanos { now ->
                val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastTime = now
                val speed = 800f
                if (isSteeringLeft) {
                    engine.movePaddleBy(-speed * dt)
                }
                if (isSteeringRight) {
                    engine.movePaddleBy(speed * dt)
                }
            }
        }
    }

    val currentLevelDef = LevelData.levels.getOrElse(level - 1) { LevelData.levels.first() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060919))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Top HUD
            GameHUD(
                level = level,
                levelName = currentLevelDef.name,
                score = score,
                bestScore = bestScore,
                lives = lives,
                combo = combo,
                activePowerUps = activePowerUps,
                isSoundEnabled = isSoundEnabled,
                onToggleSound = {
                    isSoundEnabled = !isSoundEnabled
                    soundManager.isSoundEnabled = isSoundEnabled
                    preferences.isSoundEnabled = isSoundEnabled
                },
                onPauseClick = {
                    engine.pauseGame()
                }
            )

            // 2. Playable Game Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                GameCanvas(
                    engine = engine,
                    playStatus = playStatus,
                    onTap = {
                        if (playStatus == PlayStatus.READY_TO_LAUNCH) {
                            engine.launchBall()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Quick Launch Floating Button when ball is attached
                if (playStatus == PlayStatus.READY_TO_LAUNCH) {
                    Button(
                        onClick = { engine.launchBall() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 70.dp)
                            .testTag("launch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LAUNCH BALL",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // 3. Ergonomic Bottom Touch Controls (Paddle Left / Right buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10193E))
                        .border(1.5.dp, Color(0x6600E5FF), CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isSteeringLeft = true
                                    tryAwaitRelease()
                                    isSteeringLeft = false
                                }
                            )
                        }
                        .testTag("paddle_left_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Move Left",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Drag or hold arrows",
                    color = Color(0xFF546E7A),
                    fontSize = 11.sp
                )

                // Right Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10193E))
                        .border(1.5.dp, Color(0x6600E5FF), CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isSteeringRight = true
                                    tryAwaitRelease()
                                    isSteeringRight = false
                                }
                            )
                        }
                        .testTag("paddle_right_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Move Right",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // 4. Dialog Overlays
        when (playStatus) {
            PlayStatus.PAUSED -> {
                PauseDialog(
                    onResume = { engine.resumeGame() },
                    onRestart = { engine.restartCurrentLevel() },
                    onMainMenu = onExitToMainMenu,
                    isSoundEnabled = isSoundEnabled,
                    onToggleSound = {
                        isSoundEnabled = !isSoundEnabled
                        soundManager.isSoundEnabled = isSoundEnabled
                        preferences.isSoundEnabled = isSoundEnabled
                    },
                    isHapticsEnabled = isHapticsEnabled,
                    onToggleHaptics = {
                        isHapticsEnabled = !isHapticsEnabled
                        soundManager.isHapticsEnabled = isHapticsEnabled
                        preferences.isHapticsEnabled = isHapticsEnabled
                    }
                )
            }
            PlayStatus.GAME_OVER -> {
                GameOverDialog(
                    finalScore = score,
                    bestScore = bestScore,
                    isNewBest = score >= bestScore && score > 0,
                    onTryAgain = { engine.loadLevel(level, resetScore = true) },
                    onMainMenu = onExitToMainMenu
                )
            }
            PlayStatus.LEVEL_COMPLETED, PlayStatus.ALL_LEVELS_CLEARED -> {
                val stars = when {
                    lives >= 4 -> 3
                    lives >= 2 -> 2
                    else -> 1
                }
                LevelCompleteDialog(
                    level = level,
                    levelName = currentLevelDef.name,
                    score = score,
                    stars = stars,
                    hasNextLevel = level < LevelData.levels.size,
                    onNextLevel = { engine.nextLevel() },
                    onReplay = { engine.loadLevel(level, resetScore = false) },
                    onMainMenu = onExitToMainMenu
                )
            }
            else -> {}
        }
    }
}

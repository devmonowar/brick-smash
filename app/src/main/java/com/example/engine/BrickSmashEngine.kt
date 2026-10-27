package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.audio.SoundManager
import com.example.data.GamePreferences
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*
import kotlin.random.Random

class BrickSmashEngine(
    private val soundManager: SoundManager,
    private val preferences: GamePreferences
) {
    companion object {
        const val ARENA_WIDTH = 1000f
        const val ARENA_HEIGHT = 1600f
        const val TOP_WALL_Y = 140f
        const val PADDLE_DEFAULT_Y = 1450f
        const val PADDLE_BASE_WIDTH = 170f
        const val PADDLE_WIDE_WIDTH = 260f
        const val PADDLE_HEIGHT = 26f
        const val INITIAL_LIVES = 5
    }

    // Game state observables
    private val _playStatus = MutableStateFlow(PlayStatus.READY_TO_LAUNCH)
    val playStatus: StateFlow<PlayStatus> = _playStatus.asStateFlow()

    private val _currentLevel = MutableStateFlow(1)
    val currentLevel: StateFlow<Int> = _currentLevel.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _bestScore = MutableStateFlow(preferences.bestScore)
    val bestScore: StateFlow<Int> = _bestScore.asStateFlow()

    private val _lives = MutableStateFlow(INITIAL_LIVES)
    val lives: StateFlow<Int> = _lives.asStateFlow()

    private val _combo = MutableStateFlow(0)
    val combo: StateFlow<Int> = _combo.asStateFlow()

    private val _activePowerUps = MutableStateFlow<Map<PowerUpType, Float>>(emptyMap())
    val activePowerUps: StateFlow<Map<PowerUpType, Float>> = _activePowerUps.asStateFlow()

    // Game world elements
    val paddle = Paddle(
        x = ARENA_WIDTH / 2f,
        y = PADDLE_DEFAULT_Y,
        baseWidth = PADDLE_BASE_WIDTH,
        currentWidth = PADDLE_BASE_WIDTH,
        height = PADDLE_HEIGHT
    )

    val balls = mutableListOf<Ball>()
    val bricks = mutableListOf<Brick>()
    val powerUps = mutableListOf<PowerUp>()
    val particles = mutableListOf<Particle>()
    val scorePopups = mutableListOf<ScorePopup>()

    private var baseSpeed: Float = 600f
    private var powerUpIdGen = 0L
    private var popupIdGen = 0L

    init {
        loadLevel(1, resetScore = true)
    }

    fun loadLevel(levelNum: Int, resetScore: Boolean = false) {
        val clampedLevel = levelNum.coerceIn(1, LevelData.levels.size)
        _currentLevel.value = clampedLevel
        if (resetScore) {
            _score.value = 0
            _lives.value = INITIAL_LIVES
        }
        _combo.value = 0
        _activePowerUps.value = emptyMap()

        val def = LevelData.levels[clampedLevel - 1]
        baseSpeed = def.baseBallSpeed

        // Setup bricks
        bricks.clear()
        bricks.addAll(LevelData.createBricksForLevel(clampedLevel - 1, ARENA_WIDTH))

        // Reset paddle
        paddle.x = ARENA_WIDTH / 2f
        paddle.currentWidth = PADDLE_BASE_WIDTH

        // Clear active items
        powerUps.clear()
        particles.clear()
        scorePopups.clear()

        // Setup initial ball attached to paddle
        balls.clear()
        val initialBall = Ball(
            x = paddle.x,
            y = paddle.y - paddle.height / 2f - 14f,
            vx = 0f,
            vy = 0f,
            radius = 14f,
            isAttached = true
        )
        balls.add(initialBall)

        _playStatus.value = PlayStatus.READY_TO_LAUNCH
    }

    fun launchBall() {
        if (_playStatus.value != PlayStatus.READY_TO_LAUNCH) return

        val speed = getEffectiveSpeed()
        // Launch with random angle between -75 and -55 degrees (up-left or up-right)
        val angleDeg = if (Random.nextBoolean()) Random.nextFloat() * 20f - 75f else Random.nextFloat() * 20f - 105f
        val angleRad = Math.toRadians(angleDeg.toDouble())

        for (ball in balls) {
            if (ball.isAttached) {
                ball.isAttached = false
                ball.vx = (speed * cos(angleRad)).toFloat()
                ball.vy = (speed * sin(angleRad)).toFloat()
            }
        }

        soundManager.playLaunch()
        _playStatus.value = PlayStatus.BALL_IN_PLAY
    }

    fun setPaddleX(targetX: Float) {
        val halfW = paddle.currentWidth / 2f
        val clampedX = targetX.coerceIn(halfW + 15f, ARENA_WIDTH - halfW - 15f)
        val dx = clampedX - paddle.x
        paddle.x = clampedX

        // Move attached balls along with paddle
        for (ball in balls) {
            if (ball.isAttached) {
                ball.x = paddle.x + ball.offsetXOnPaddle
                ball.y = paddle.y - paddle.height / 2f - ball.radius
            }
        }
    }

    fun movePaddleBy(deltaX: Float) {
        setPaddleX(paddle.x + deltaX)
    }

    fun pauseGame() {
        if (_playStatus.value == PlayStatus.BALL_IN_PLAY || _playStatus.value == PlayStatus.READY_TO_LAUNCH) {
            _playStatus.value = PlayStatus.PAUSED
        }
    }

    fun resumeGame() {
        if (_playStatus.value == PlayStatus.PAUSED) {
            val hasActiveBall = balls.any { !it.isAttached }
            _playStatus.value = if (hasActiveBall) PlayStatus.BALL_IN_PLAY else PlayStatus.READY_TO_LAUNCH
        }
    }

    fun restartCurrentLevel() {
        loadLevel(_currentLevel.value, resetScore = false)
    }

    fun nextLevel() {
        val next = _currentLevel.value + 1
        if (next <= LevelData.levels.size) {
            loadLevel(next, resetScore = false)
        } else {
            _playStatus.value = PlayStatus.ALL_LEVELS_CLEARED
        }
    }

    private fun getEffectiveSpeed(): Float {
        val slowFactor = if (_activePowerUps.value.containsKey(PowerUpType.SLOW_BALL)) 0.7f else 1.0f
        return baseSpeed * slowFactor
    }

    fun update(deltaSeconds: Float) {
        if (_playStatus.value != PlayStatus.BALL_IN_PLAY) {
            // Still update particles and score popups if ready or paused
            updateVisualEffects(deltaSeconds)
            return
        }

        val dt = deltaSeconds.coerceIn(0.001f, 0.05f)

        // 1. Update Power-Up timers
        updatePowerUpTimers(dt)

        // 2. Update Paddle Width animation towards target
        val targetWidth = if (_activePowerUps.value.containsKey(PowerUpType.WIDE_PADDLE)) PADDLE_WIDE_WIDTH else PADDLE_BASE_WIDTH
        paddle.currentWidth += (targetWidth - paddle.currentWidth) * min(1f, dt * 10f)

        // 3. Update Balls
        val currentSpeed = getEffectiveSpeed()
        val ballsIterator = balls.iterator()
        while (ballsIterator.hasNext()) {
            val ball = ballsIterator.next()
            if (ball.isAttached) {
                ball.x = paddle.x + ball.offsetXOnPaddle
                ball.y = paddle.y - paddle.height / 2f - ball.radius
                continue
            }

            // Normalise velocity vector to match effective speed
            val mag = hypot(ball.vx, ball.vy)
            if (mag > 0.001f) {
                ball.vx = (ball.vx / mag) * currentSpeed
                ball.vy = (ball.vy / mag) * currentSpeed
            }

            // Anti-stuck prevention: avoid purely horizontal or purely vertical ball loops
            if (abs(ball.vy) < currentSpeed * 0.2f) {
                ball.vy = if (ball.vy >= 0) currentSpeed * 0.25f else -currentSpeed * 0.25f
            }
            if (abs(ball.vx) < currentSpeed * 0.15f) {
                ball.vx = if (ball.vx >= 0) currentSpeed * 0.2f else -currentSpeed * 0.2f
            }


            // Step movement
            ball.x += ball.vx * dt
            ball.y += ball.vy * dt

            // Wall collisions
            if (ball.x - ball.radius <= 20f) {
                ball.x = 20f + ball.radius
                ball.vx = abs(ball.vx)
                soundManager.playBounceWall()
            } else if (ball.x + ball.radius >= ARENA_WIDTH - 20f) {
                ball.x = ARENA_WIDTH - 20f - ball.radius
                ball.vx = -abs(ball.vx)
                soundManager.playBounceWall()
            }

            if (ball.y - ball.radius <= TOP_WALL_Y) {
                ball.y = TOP_WALL_Y + ball.radius
                ball.vy = abs(ball.vy)
                soundManager.playBounceWall()
            }

            // Paddle collision
            val paddleTop = paddle.y - paddle.height / 2f
            val paddleBottom = paddle.y + paddle.height / 2f
            val paddleLeft = paddle.x - paddle.currentWidth / 2f
            val paddleRight = paddle.x + paddle.currentWidth / 2f

            if (ball.vy > 0 &&
                ball.y + ball.radius >= paddleTop &&
                ball.y - ball.radius <= paddleBottom &&
                ball.x + ball.radius >= paddleLeft &&
                ball.x - ball.radius <= paddleRight
            ) {
                ball.y = paddleTop - ball.radius
                // Calculate impact offset (-1.0 to 1.0)
                val hitOffset = ((ball.x - paddle.x) / (paddle.currentWidth / 2f)).coerceIn(-1f, 1f)
                val maxBounceAngle = Math.toRadians(65.0)
                val bounceAngle = hitOffset * maxBounceAngle

                ball.vx = (currentSpeed * sin(bounceAngle)).toFloat()
                ball.vy = -(currentSpeed * cos(bounceAngle)).toFloat()

                soundManager.playBouncePaddle()

                // Spawn paddle bounce sparks
                for (p in 0 until 6) {
                    val angle = Random.nextFloat() * PI.toFloat() - PI.toFloat()
                    val pSpeed = Random.nextFloat() * 140f + 60f
                    particles.add(
                        Particle(
                            x = ball.x,
                            y = paddleTop,
                            vx = cos(angle) * pSpeed,
                            vy = -abs(sin(angle) * pSpeed),
                            color = Color(0xFF00E5FF),
                            radius = Random.nextFloat() * 3f + 2f,
                            maxLife = 0.35f
                        )
                    )
                }
            }

            // Brick collisions
            checkBrickCollisions(ball)

            // Bottom Boundary check (Ball loss)
            if (ball.y - ball.radius > ARENA_HEIGHT) {
                ballsIterator.remove()
            }
        }

        // 4. Handle Ball Loss & Lives
        if (balls.isEmpty()) {
            _combo.value = 0
            val remainingLives = _lives.value - 1
            _lives.value = remainingLives
            soundManager.playLifeLost()

            if (remainingLives <= 0) {
                _playStatus.value = PlayStatus.GAME_OVER
                soundManager.playGameOver()
                updateHighScore()
            } else {
                // Respawn single ball attached to paddle
                val respawnedBall = Ball(
                    x = paddle.x,
                    y = paddle.y - paddle.height / 2f - 14f,
                    vx = 0f,
                    vy = 0f,
                    radius = 14f,
                    isAttached = true
                )
                balls.add(respawnedBall)
                _playStatus.value = PlayStatus.READY_TO_LAUNCH
            }
        }

        // 5. Update Falling Power-Ups
        updatePowerUps(dt)

        // 6. Update Visual Particles and Popups
        updateVisualEffects(dt)

        // 7. Check Level Complete
        checkLevelCompletion()
    }

    private fun checkBrickCollisions(ball: Ball) {
        for (brick in bricks) {
            if (brick.isDestroyed) continue

            // Circle - Box Collision
            val closestX = ball.x.coerceIn(brick.x, brick.x + brick.width)
            val closestY = ball.y.coerceIn(brick.y, brick.y + brick.height)

            val dx = ball.x - closestX
            val dy = ball.y - closestY
            val distSq = dx * dx + dy * dy

            if (distSq < ball.radius * ball.radius) {
                // Determine collision normal
                val overlapLeft = abs(ball.x + ball.radius - brick.x)
                val overlapRight = abs(brick.x + brick.width - (ball.x - ball.radius))
                val overlapTop = abs(ball.y + ball.radius - brick.y)
                val overlapBottom = abs(brick.y + brick.height - (ball.y - ball.radius))

                val minOverlap = min(min(overlapLeft, overlapRight), min(overlapTop, overlapBottom))
                if (minOverlap == overlapLeft || minOverlap == overlapRight) {
                    ball.vx = -ball.vx
                } else {
                    ball.vy = -ball.vy
                }

                // Handle damage to brick
                handleBrickHit(brick)
                break // Only hit one brick per frame for stability
            }
        }
    }

    private fun handleBrickHit(brick: Brick) {
        if (brick.type == BrickType.STEEL) {
            soundManager.playSteelHit()
            // Sparks
            for (i in 0 until 6) {
                val pAngle = Random.nextFloat() * 2f * PI.toFloat()
                val pSpd = Random.nextFloat() * 120f + 50f
                particles.add(
                    Particle(
                        x = brick.x + brick.width / 2f,
                        y = brick.y + brick.height / 2f,
                        vx = cos(pAngle) * pSpd,
                        vy = sin(pAngle) * pSpd,
                        color = Color(0xFFB0BEC5),
                        radius = Random.nextFloat() * 3f + 2f,
                        maxLife = 0.3f
                    )
                )
            }
            return
        }

        brick.hitsRemaining--

        if (brick.hitsRemaining <= 0) {
            brick.isDestroyed = true
            val currentCombo = _combo.value + 1
            _combo.value = currentCombo

            val comboMultiplier = when {
                currentCombo >= 10 -> 3.0f
                currentCombo >= 5 -> 2.0f
                currentCombo >= 3 -> 1.5f
                else -> 1.0f
            }

            val earnedScore = (brick.scoreValue * comboMultiplier).toInt()
            _score.value += earnedScore

            // Show floating popup
            val popupText = if (comboMultiplier > 1f) "+$earnedScore (x${comboMultiplier.toInt()})" else "+$earnedScore"
            addScorePopup(popupText, brick.x + brick.width / 2f, brick.y, brick.baseColor)

            if (brick.type == BrickType.EXPLOSIVE) {
                soundManager.playExplosion()
                triggerExplosion(brick.x + brick.width / 2f, brick.y + brick.height / 2f)
            } else {
                soundManager.playDestroyBrick()
                spawnBrickShatterParticles(brick)
            }

            if (brick.type == BrickType.BONUS) {
                spawnRandomPowerUp(brick.x + brick.width / 2f, brick.y + brick.height / 2f)
            }

            updateHighScore()
        } else {
            soundManager.playHitBrick()
            // Small hit chips
            for (i in 0 until 4) {
                val pAngle = Random.nextFloat() * 2f * PI.toFloat()
                val pSpd = Random.nextFloat() * 80f + 40f
                particles.add(
                    Particle(
                        x = brick.x + brick.width / 2f,
                        y = brick.y + brick.height / 2f,
                        vx = cos(pAngle) * pSpd,
                        vy = sin(pAngle) * pSpd,
                        color = brick.baseColor,
                        radius = Random.nextFloat() * 2.5f + 1.5f,
                        maxLife = 0.25f
                    )
                )
            }
        }
    }

    private fun triggerExplosion(centerX: Float, centerY: Float) {
        // High density blast particles
        for (i in 0 until 35) {
            val pAngle = Random.nextFloat() * 2f * PI.toFloat()
            val pSpd = Random.nextFloat() * 320f + 80f
            val pColor = if (Random.nextBoolean()) Color(0xFFFF1744) else Color(0xFFFF9100)
            particles.add(
                Particle(
                    x = centerX,
                    y = centerY,
                    vx = cos(pAngle) * pSpd,
                    vy = sin(pAngle) * pSpd,
                    color = pColor,
                    radius = Random.nextFloat() * 5f + 3f,
                    maxLife = 0.6f
                )
            )
        }

        addScorePopup("BOOM!", centerX, centerY - 20f, Color(0xFFFF1744))

        val blastRadius = 210f
        for (neighbor in bricks) {
            if (neighbor.isDestroyed || neighbor.type == BrickType.STEEL) continue
            val nbCenterX = neighbor.x + neighbor.width / 2f
            val nbCenterY = neighbor.y + neighbor.height / 2f
            val dist = hypot(centerX - nbCenterX, centerY - nbCenterY)
            if (dist <= blastRadius) {
                neighbor.hitsRemaining -= 2
                if (neighbor.hitsRemaining <= 0) {
                    neighbor.isDestroyed = true
                    _score.value += neighbor.scoreValue
                    spawnBrickShatterParticles(neighbor)
                    if (neighbor.type == BrickType.BONUS) {
                        spawnRandomPowerUp(nbCenterX, nbCenterY)
                    }
                }
            }
        }
    }

    private fun spawnBrickShatterParticles(brick: Brick) {
        val count = 14
        val centerX = brick.x + brick.width / 2f
        val centerY = brick.y + brick.height / 2f
        for (i in 0 until count) {
            val pAngle = Random.nextFloat() * 2f * PI.toFloat()
            val pSpd = Random.nextFloat() * 180f + 60f
            particles.add(
                Particle(
                    x = centerX,
                    y = centerY,
                    vx = cos(pAngle) * pSpd,
                    vy = sin(pAngle) * pSpd,
                    color = brick.baseColor,
                    radius = Random.nextFloat() * 4f + 2f,
                    maxLife = 0.45f
                )
            )
        }
    }

    private fun spawnRandomPowerUp(x: Float, y: Float) {
        val choices = listOf(
            PowerUpType.WIDE_PADDLE,
            PowerUpType.SLOW_BALL,
            PowerUpType.MULTI_BALL,
            PowerUpType.EXTRA_LIFE
        )
        val selected = choices.random()
        powerUps.add(
            PowerUp(
                id = ++powerUpIdGen,
                x = x,
                y = y,
                type = selected
            )
        )
    }

    private fun updatePowerUps(dt: Float) {
        val iter = powerUps.iterator()
        val paddleTop = paddle.y - paddle.height / 2f
        val paddleBottom = paddle.y + paddle.height / 2f
        val paddleLeft = paddle.x - paddle.currentWidth / 2f
        val paddleRight = paddle.x + paddle.currentWidth / 2f

        while (iter.hasNext()) {
            val p = iter.next()
            p.y += p.vy * dt

            // Check collision with paddle
            if (p.y + p.radius >= paddleTop &&
                p.y - p.radius <= paddleBottom &&
                p.x + p.radius >= paddleLeft &&
                p.x - p.radius <= paddleRight
            ) {
                applyPowerUp(p.type)
                addScorePopup(p.type.label, p.x, p.y - 15f, p.type.color)
                soundManager.playPowerUpCollected()
                iter.remove()
                continue
            }

            // Exceeded bottom
            if (p.y - p.radius > ARENA_HEIGHT) {
                iter.remove()
            }
        }
    }

    private fun applyPowerUp(type: PowerUpType) {
        when (type) {
            PowerUpType.WIDE_PADDLE, PowerUpType.SLOW_BALL -> {
                val current = _activePowerUps.value.toMutableMap()
                current[type] = type.durationMs / 1000f
                _activePowerUps.value = current
            }
            PowerUpType.MULTI_BALL -> {
                // Duplicate active ball with spread angles
                val referenceBall = balls.firstOrNull { !it.isAttached } ?: balls.firstOrNull()
                if (referenceBall != null) {
                    val currentSpeed = getEffectiveSpeed()
                    for (i in 1..2) {
                        val angleOffset = if (i == 1) 0.45f else -0.45f
                        val currentAngle = atan2(referenceBall.vy, referenceBall.vx)
                        val newAngle = currentAngle + angleOffset
                        balls.add(
                            Ball(
                                x = referenceBall.x,
                                y = referenceBall.y,
                                vx = currentSpeed * cos(newAngle),
                                vy = currentSpeed * sin(newAngle),
                                radius = referenceBall.radius,
                                isAttached = false
                            )
                        )
                    }
                }
            }
            PowerUpType.EXTRA_LIFE -> {
                _lives.value = min(5, _lives.value + 1)
            }
        }
    }

    private fun updatePowerUpTimers(dt: Float) {
        val current = _activePowerUps.value.toMutableMap()
        if (current.isEmpty()) return

        val iter = current.iterator()
        var changed = false
        while (iter.hasNext()) {
            val entry = iter.next()
            val remaining = entry.value - dt
            if (remaining <= 0f) {
                iter.remove()
                changed = true
            } else {
                entry.setValue(remaining)
            }
        }
        if (changed) {
            _activePowerUps.value = current
        }
    }

    private fun updateVisualEffects(dt: Float) {
        // Particles
        val pIter = particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.currentLife += dt
            if (p.currentLife >= p.maxLife) {
                pIter.remove()
            } else {
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.alpha = 1f - (p.currentLife / p.maxLife)
            }
        }

        // Score popups
        val spIter = scorePopups.iterator()
        while (spIter.hasNext()) {
            val sp = spIter.next()
            sp.currentLife += dt
            if (sp.currentLife >= sp.maxLife) {
                spIter.remove()
            } else {
                sp.y -= 70f * dt
                sp.alpha = 1f - (sp.currentLife / sp.maxLife)
            }
        }
    }

    private fun addScorePopup(text: String, x: Float, y: Float, color: Color) {
        scorePopups.add(
            ScorePopup(
                id = ++popupIdGen,
                text = text,
                x = x,
                y = y,
                color = color
            )
        )
    }

    private fun checkLevelCompletion() {
        val remainingTargets = bricks.count { !it.isDestroyed && it.type != BrickType.STEEL }
        if (remainingTargets == 0) {
            _playStatus.value = PlayStatus.LEVEL_COMPLETED
            soundManager.playLevelComplete()

            // Bonus points for remaining lives and level clear
            val livesBonus = _lives.value * 100
            val levelBonus = _currentLevel.value * 200
            val totalEarned = livesBonus + levelBonus
            _score.value += totalEarned
            updateHighScore()

            // Unlock next level in preferences
            val nextLvl = _currentLevel.value + 1
            if (nextLvl > preferences.unlockedLevel && nextLvl <= LevelData.levels.size) {
                preferences.unlockedLevel = nextLvl
            }
            val stars = when {
                _lives.value >= 4 -> 3
                _lives.value >= 2 -> 2
                else -> 1
            }
            preferences.setLevelStars(_currentLevel.value, stars)
            preferences.setLevelHighScore(_currentLevel.value, _score.value)
        }
    }

    private fun updateHighScore() {
        if (_score.value > _bestScore.value) {
            _bestScore.value = _score.value
            preferences.bestScore = _score.value
        }
    }
}

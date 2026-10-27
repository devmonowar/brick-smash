package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.SoundManager
import com.example.data.GamePreferences
import com.example.engine.BrickSmashEngine
import com.example.engine.LevelData
import com.example.model.BrickType
import com.example.model.PlayStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrickSmashEngineTest {

    private lateinit var context: Context
    private lateinit var soundManager: SoundManager
    private lateinit var preferences: GamePreferences
    private lateinit var engine: BrickSmashEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        soundManager = SoundManager(context).apply {
            isSoundEnabled = false // Silent in tests
            isHapticsEnabled = false
        }
        preferences = GamePreferences(context)
        preferences.resetProgress()
        engine = BrickSmashEngine(soundManager, preferences)
    }

    @Test
    fun testLevel1Initialization() {
        assertEquals(1, engine.currentLevel.value)
        assertEquals(PlayStatus.READY_TO_LAUNCH, engine.playStatus.value)
        assertEquals(5, engine.lives.value)
        assertEquals(0, engine.score.value)
        assertEquals(1, engine.balls.size)
        assertTrue(engine.balls[0].isAttached)
        assertTrue(engine.bricks.isNotEmpty())
    }

    @Test
    fun testBallLaunch() {
        engine.launchBall()
        assertEquals(PlayStatus.BALL_IN_PLAY, engine.playStatus.value)
        assertFalse(engine.balls[0].isAttached)
        assertNotEquals(0f, engine.balls[0].vy)
    }

    @Test
    fun testPaddleMovementClamped() {
        // Move far left
        engine.setPaddleX(-500f)
        assertTrue(engine.paddle.x >= engine.paddle.currentWidth / 2f)

        // Move far right
        engine.setPaddleX(2000f)
        assertTrue(engine.paddle.x <= BrickSmashEngine.ARENA_WIDTH - engine.paddle.currentWidth / 2f)
    }

    @Test
    fun testTenLevelsLoadedProperly() {
        assertEquals(10, LevelData.levels.size)
        for (i in 1..10) {
            engine.loadLevel(i)
            assertEquals(i, engine.currentLevel.value)
            assertTrue("Level $i should have bricks", engine.bricks.isNotEmpty())
        }
    }

    @Test
    fun testBrickDestructionAndScoring() {
        engine.launchBall()
        val initialScore = engine.score.value

        // Find first normal brick
        val normalBrick = engine.bricks.first { it.type == BrickType.NORMAL }
        // Place ball directly at brick center
        val ball = engine.balls[0]
        ball.x = normalBrick.x + normalBrick.width / 2f
        ball.y = normalBrick.y + normalBrick.height / 2f
        ball.vy = -100f

        engine.update(0.016f)

        assertTrue(normalBrick.isDestroyed)
        assertTrue(engine.score.value > initialScore)
        assertTrue(engine.combo.value >= 1)
    }

    @Test
    fun testHardBrickRequiresMultipleHits() {
        engine.loadLevel(2) // Level 2 has hard bricks
        engine.launchBall()
        val hardBrick = engine.bricks.first { it.type == BrickType.HARD }
        val initialHits = hardBrick.hitsRemaining
        assertTrue(initialHits >= 2)

        // Place ball at hard brick
        val ball = engine.balls[0]
        ball.x = hardBrick.x + hardBrick.width / 2f
        ball.y = hardBrick.y + hardBrick.height / 2f
        ball.vy = -100f

        engine.update(0.016f)

        assertEquals(initialHits - 1, hardBrick.hitsRemaining)
        assertFalse(hardBrick.isDestroyed)
    }

    @Test
    fun testBallLossDecrementsLife() {
        engine.launchBall()
        val initialLives = engine.lives.value

        // Move ball below screen
        val ball = engine.balls[0]
        ball.y = BrickSmashEngine.ARENA_HEIGHT + 100f

        engine.update(0.016f)

        assertEquals(initialLives - 1, engine.lives.value)
        assertEquals(PlayStatus.READY_TO_LAUNCH, engine.playStatus.value)
        assertEquals(1, engine.balls.size)
        assertTrue(engine.balls[0].isAttached)
    }

    @Test
    fun testGameOverWhenAllLivesLost() {
        for (life in 1..5) {
            engine.launchBall()
            val ball = engine.balls.first()
            ball.y = BrickSmashEngine.ARENA_HEIGHT + 100f
            engine.update(0.016f)
        }

        assertEquals(0, engine.lives.value)
        assertEquals(PlayStatus.GAME_OVER, engine.playStatus.value)
    }
}

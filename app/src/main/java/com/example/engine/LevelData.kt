package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.model.Brick
import com.example.model.BrickType

data class LevelDefinition(
    val levelNumber: Int,
    val name: String,
    val subtitle: String,
    val baseBallSpeed: Float,
    val layout: List<String>
)

object LevelData {

    // Legend for layout strings:
    // '.' : Empty space
    // 'N' : Normal Brick (1 hit, 10 pts)
    // 'H' : Hard Brick 2-hits (25 pts)
    // '3' : Hard Brick 3-hits (35 pts)
    // '4' : Hard Brick 4-hits (50 pts)
    // 'S' : Steel Brick (Indestructible, not a required target)
    // 'E' : Explosive Brick (30 pts, AOE explosion)
    // 'B' : Bonus Brick (50 pts, drops power-up)

    val levels: List<LevelDefinition> = listOf(
        // Level 1: Neon Gate
        LevelDefinition(
            levelNumber = 1,
            name = "Neon Gate",
            subtitle = "Welcome to Brick Smash",
            baseBallSpeed = 580f,
            layout = listOf(
                "NNNNNNNN",
                "NNBNNBNN",
                "NNNNNNNN",
                ".NNNNNN."
            )
        ),
        // Level 2: Pyramid Peak
        LevelDefinition(
            levelNumber = 2,
            name = "Pyramid Peak",
            subtitle = "Watch out for tough bricks",
            baseBallSpeed = 610f,
            layout = listOf(
                "...HH...",
                "..NNNN..",
                ".NNEEBN.",
                "NNNNNNNN",
                ".NNNNNN."
            )
        ),
        // Level 3: Castle Wall
        LevelDefinition(
            levelNumber = 3,
            name = "Castle Wall",
            subtitle = "Steel pillars cannot be broken",
            baseBallSpeed = 640f,
            layout = listOf(
                "S.NNNN.S",
                "S.HEEH.S",
                "S.NBNN.S",
                "NNNNNNNN",
                ".NN..NN."
            )
        ),
        // Level 4: Diamond Matrix
        LevelDefinition(
            levelNumber = 4,
            name = "Diamond Matrix",
            subtitle = "Hit the explosive core",
            baseBallSpeed = 670f,
            layout = listOf(
                "...EE...",
                "..NHHN..",
                ".N3BB3N.",
                "..NHHN..",
                "...EE..."
            )
        ),
        // Level 5: Checkerboard Hazard
        LevelDefinition(
            levelNumber = 5,
            name = "Checkerboard",
            subtitle = "Steel blocks redirect your ball",
            baseBallSpeed = 700f,
            layout = listOf(
                "N.S.S.N",
                ".H.E.H.",
                "S.B.B.S",
                ".H.E.H.",
                "N.S.S.N"
            )
        ),
        // Level 6: The Fortress
        LevelDefinition(
            levelNumber = 6,
            name = "The Fortress",
            subtitle = "Blast through the vault",
            baseBallSpeed = 730f,
            layout = listOf(
                "SSSSSSSS",
                "S.EEEE.S",
                "S.3HH3.S",
                "S.NBNN.S",
                ".NNNNNN."
            )
        ),
        // Level 7: Spiral Vortex
        LevelDefinition(
            levelNumber = 7,
            name = "Spiral Vortex",
            subtitle = "Chain hits for combo multipliers",
            baseBallSpeed = 760f,
            layout = listOf(
                "NNNNNNNN",
                "N......N",
                "N.S33S.N",
                "N.SEES.N",
                "N.SBB..N",
                "N......N",
                "NNNNNNNN"
            )
        ),
        // Level 8: Minefield
        LevelDefinition(
            levelNumber = 8,
            name = "Minefield",
            subtitle = "Trigger chain detonations!",
            baseBallSpeed = 790f,
            layout = listOf(
                "E.E.E.E.",
                ".3.B.3.E",
                "E.E.E.E.",
                ".3.B.3.E",
                "NNNNNNNN"
            )
        ),
        // Level 9: Titan's Shield
        LevelDefinition(
            levelNumber = 9,
            name = "Titan's Shield",
            subtitle = "Hardened 4-hit armor plates",
            baseBallSpeed = 820f,
            layout = listOf(
                "S.4444.S",
                "S.3EE3.S",
                "S.HHBH.S",
                ".NNNNNN.",
                "S......S"
            )
        ),
        // Level 10: Cosmic Finale
        LevelDefinition(
            levelNumber = 10,
            name = "Cosmic Finale",
            subtitle = "The ultimate arcade showdown!",
            baseBallSpeed = 860f,
            layout = listOf(
                "4S4EE4S4",
                "3.3BB3.3",
                "H.HH.H.H",
                "S.E44E.S",
                "NNNNNNNN",
                ".NN..NN."
            )
        )
    )

    fun createBricksForLevel(
        levelIndex: Int,
        arenaWidth: Float = 1000f,
        topOffset: Float = 220f
    ): List<Brick> {
        val level = levels.getOrElse(levelIndex) { levels.first() }
        val rows = level.layout.size
        val cols = level.layout.maxOfOrNull { it.length } ?: 8

        val marginX = 60f
        val availableWidth = arenaWidth - (2 * marginX)
        val spacingX = 8f
        val spacingY = 10f

        val brickWidth = (availableWidth - (spacingX * (cols - 1))) / cols
        val brickHeight = 36f

        val bricks = mutableListOf<Brick>()
        var brickId = 0

        for (r in 0 until rows) {
            val rowStr = level.layout[r]
            for (c in 0 until rowStr.length) {
                val char = rowStr[c]
                if (char == '.') continue

                val bx = marginX + c * (brickWidth + spacingX)
                val by = topOffset + r * (brickHeight + spacingY)

                val (type, maxHits, color, score) = when (char) {
                    'N' -> {
                        val rowColor = when (r % 5) {
                            0 -> Color(0xFF00E5FF) // Neon Cyan
                            1 -> Color(0xFFFF2A7A) // Neon Magenta
                            2 -> Color(0xFFFFD600) // Neon Yellow
                            3 -> Color(0xFF00E676) // Neon Green
                            else -> Color(0xFF7C4DFF) // Neon Purple
                        }
                        Tuple4(BrickType.NORMAL, 1, rowColor, 10)
                    }
                    'H' -> Tuple4(BrickType.HARD, 2, Color(0xFFFF9100), 25)
                    '3' -> Tuple4(BrickType.HARD, 3, Color(0xFFFF3D00), 35)
                    '4' -> Tuple4(BrickType.HARD, 4, Color(0xFFD50000), 50)
                    'S' -> Tuple4(BrickType.STEEL, 9999, Color(0xFF90A4AE), 0)
                    'E' -> Tuple4(BrickType.EXPLOSIVE, 1, Color(0xFFFF1744), 30)
                    'B' -> Tuple4(BrickType.BONUS, 1, Color(0xFFE040FB), 50)
                    else -> Tuple4(BrickType.NORMAL, 1, Color(0xFF00E5FF), 10)
                }

                bricks.add(
                    Brick(
                        id = ++brickId,
                        row = r,
                        col = c,
                        x = bx,
                        y = by,
                        width = brickWidth,
                        height = brickHeight,
                        type = type,
                        hitsRemaining = maxHits,
                        maxHits = maxHits,
                        isDestroyed = false,
                        baseColor = color,
                        scoreValue = score
                    )
                )
            }
        }
        return bricks
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}

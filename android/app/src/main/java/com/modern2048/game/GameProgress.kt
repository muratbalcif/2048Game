package com.modern2048.game

/** Lifetime counters and unlocks, independent of the current board. */
data class GameProgress(
    val moves: Int = 0,
    val merges: Int = 0,
    val gamesStarted: Int = 1,
    val bestTile: Int = 0,
    val totalPoints: Long = 0
) {
    fun recordMove(before: GameState, after: GameState, scoreDelta: Int, mergedCount: Int): GameProgress =
        copy(
            moves = moves + 1,
            merges = merges + mergedCount,
            bestTile = maxOf(bestTile, after.board.maxOrNull() ?: 0),
            totalPoints = totalPoints + scoreDelta.coerceAtLeast(0)
        )
    fun nextGame(): GameProgress = copy(gamesStarted = gamesStarted + 1)
}

data class GameAchievement(val id: String, val title: String, val unlocked: Boolean)

object GameAchievements {
    fun list(progress: GameProgress): List<GameAchievement> = listOf(
        GameAchievement("first_merge", "İlk Birleşme", progress.merges >= 1),
        GameAchievement("hundred_moves", "100 Hamle", progress.moves >= 100),
        GameAchievement("tile_128", "128 Taşı", progress.bestTile >= 128),
        GameAchievement("tile_512", "512 Taşı", progress.bestTile >= 512),
        GameAchievement("tile_2048", "2048 Ustası", progress.bestTile >= 2048),
        GameAchievement("five_games", "5 Oyun", progress.gamesStarted >= 5)
    )
}

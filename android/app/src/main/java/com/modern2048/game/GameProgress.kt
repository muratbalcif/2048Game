package com.modern2048.game

import androidx.annotation.StringRes

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

 data class GameAchievement(
    val id: String,
    @StringRes val titleRes: Int,
    val unlocked: Boolean
)

object GameAchievements {
    fun list(progress: GameProgress) = listOf(
        GameAchievement("first_merge", R.string.ach_first_merge, progress.merges >= 1),
        GameAchievement("merge_100", R.string.ach_merge_100, progress.merges >= 100),
        GameAchievement("merge_500", R.string.ach_merge_500, progress.merges >= 500),
        GameAchievement("merge_2000", R.string.ach_merge_2000, progress.merges >= 2000),

        GameAchievement("tile_64", R.string.ach_tile_64, progress.bestTile >= 64),
        GameAchievement("tile_128", R.string.ach_tile_128, progress.bestTile >= 128),
        GameAchievement("tile_256", R.string.ach_tile_256, progress.bestTile >= 256),
        GameAchievement("tile_512", R.string.ach_tile_512, progress.bestTile >= 512),
        GameAchievement("tile_1024", R.string.ach_tile_1024, progress.bestTile >= 1024),
        GameAchievement("tile_2048", R.string.ach_tile_2048, progress.bestTile >= 2048),
        GameAchievement("tile_4096", R.string.ach_tile_4096, progress.bestTile >= 4096),

        GameAchievement("moves_100", R.string.ach_hundred_moves, progress.moves >= 100),
        GameAchievement("moves_500", R.string.ach_moves_500, progress.moves >= 500),
        GameAchievement("moves_1000", R.string.ach_moves_1000, progress.moves >= 1000),
        GameAchievement("moves_5000", R.string.ach_moves_5000, progress.moves >= 5000),

        GameAchievement("games_5", R.string.ach_five_games, progress.gamesStarted >= 5),
        GameAchievement("games_25", R.string.ach_games_25, progress.gamesStarted >= 25),
        GameAchievement("games_100", R.string.ach_games_100, progress.gamesStarted >= 100),

        GameAchievement("points_10000", R.string.ach_points_10000, progress.totalPoints >= 10000),
        GameAchievement("points_100000", R.string.ach_points_100000, progress.totalPoints >= 100000)
    )
}
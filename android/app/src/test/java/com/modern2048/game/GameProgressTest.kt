package com.modern2048.game

import org.junit.Assert.*
import org.junit.Test

class GameProgressTest {
    @Test fun countersAndUnlocks() {
        val before = GameState(board = listOf(64, 64) + List(14) { 0 })
        val after = GameState(board = listOf(128, 2) + List(14) { 0 }, score = 128)
        val progress = GameProgress().recordMove(before, after, 128, 1)
        assertEquals(1, progress.moves)
        assertEquals(1, progress.merges)
        assertEquals(128, progress.bestTile)
        assertEquals(128L, progress.totalPoints)
        assertTrue(GameAchievements.list(progress).first { it.id == "first_merge" }.unlocked)
        assertTrue(GameAchievements.list(progress).first { it.id == "tile_128" }.unlocked)
        assertFalse(GameAchievements.list(progress).first { it.id == "tile_2048" }.unlocked)
    }

    @Test fun nextGameKeepsLifetimeCounters() {
        val progress = GameProgress(moves = 10, merges = 3).nextGame()
        assertEquals(2, progress.gamesStarted)
        assertEquals(10, progress.moves)
    }
}

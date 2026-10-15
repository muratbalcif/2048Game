package com.modern2048.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GameEngineRegressionTest {
    @Test fun doublePairMergesOncePerPair() {
        val board = listOf(2, 2, 2, 2) + List(12) { 0 }
        val result = GameEngine.move(GameState(board), Direction.LEFT, Random(42))
        assertTrue(result.moved)
        assertEquals(8, result.scoreDelta)
        assertEquals(4, result.state.board[0])
        assertEquals(4, result.state.board[1])
        assertEquals(8, result.state.score)
    }

    @Test fun noOpDoesNotSpawnOrScore() {
        val board = listOf(2, 4, 8, 16) + List(12) { 0 }
        val initial = GameState(board, score = 64)
        val result = GameEngine.move(initial, Direction.LEFT, Random(42))
        assertFalse(result.moved)
        assertEquals(initial, result.state)
        assertEquals(0, result.scoreDelta)
    }

    @Test fun fullBoardWithoutPairsHasNoMoves() {
        val board = listOf(
            2, 4, 2, 4,
            4, 2, 4, 2,
            2, 4, 2, 4,
            4, 2, 4, 2
        )
        assertFalse(GameEngine.canMove(board))
    }

    @Test fun spawnOnlyAddsOneTile() {
        val board = listOf(2, 0, 0, 0) + List(12) { 0 }
        val result = GameEngine.move(GameState(board), Direction.RIGHT, Random(7))
        assertTrue(result.moved)
        assertEquals(2, result.state.board.count { it != 0 })
        assertEquals(2, result.state.board[3])
    }
}

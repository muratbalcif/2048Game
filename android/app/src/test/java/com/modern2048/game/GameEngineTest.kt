package com.modern2048.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private fun state(row: List<Int>) = GameState(board = row + List(12) { 0 })

    @Test fun mergesPairsOnce() {
        val result = GameEngine.move(state(listOf(2, 2, 2, 2)), Direction.LEFT, Random(0))
        assertTrue(result.moved)
        assertEquals(8, result.scoreDelta)
        assertEquals(listOf(4, 4), result.state.board.take(2))
    }

    @Test fun ignoresNoOpMoves() {
        val result = GameEngine.move(state(listOf(2, 4, 8, 16)), Direction.LEFT, Random(0))
        assertFalse(result.moved)
        assertEquals(0, result.scoreDelta)
    }

    @Test fun startsWithTwoTiles() {
        val state = GameEngine.newGame(Random(0))
        assertEquals(2, state.board.count { it != 0 })
    }
}

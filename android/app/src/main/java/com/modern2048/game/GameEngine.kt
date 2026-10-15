package com.modern2048.game

import kotlin.random.Random

enum class Direction { UP, DOWN, LEFT, RIGHT }

data class GameState(
    val board: List<Int> = List(16) { 0 },
    val score: Int = 0,
    val won: Boolean = false,
    val lost: Boolean = false
)

data class MoveResult(val state: GameState, val moved: Boolean, val scoreDelta: Int)

object GameEngine {
    fun newGame(random: Random = Random.Default): GameState {
        var board = List(16) { 0 }
        repeat(2) { board = spawn(board, random) }
        return GameState(board = board)
    }

    fun move(state: GameState, direction: Direction, random: Random = Random.Default): MoveResult = moveInternal(state, direction, random, true)

    fun moveWithoutSpawn(state: GameState, direction: Direction): MoveResult = moveInternal(state, direction, Random.Default, false)

    private fun moveInternal(state: GameState, direction: Direction, random: Random, spawnTile: Boolean): MoveResult {
        require(state.board.size == 16)
        if (state.lost) return MoveResult(state, false, 0)
        val output = state.board.toMutableList()
        var gained = 0
        for (line in 0..3) {
            val indices = (0..3).map { offset ->
                when (direction) {
                    Direction.LEFT -> line * 4 + offset
                    Direction.RIGHT -> line * 4 + (3 - offset)
                    Direction.UP -> offset * 4 + line
                    Direction.DOWN -> (3 - offset) * 4 + line
                }
            }
            val values = indices.map { state.board[it] }.filter { it != 0 }
            val merged = mutableListOf<Int>()
            var i = 0
            while (i < values.size) {
                if (i + 1 < values.size && values[i] == values[i + 1]) {
                    val combined = values[i] * 2
                    merged.add(combined)
                    gained += combined
                    i += 2
                } else {
                    merged.add(values[i])
                    i++
                }
            }
            indices.forEachIndexed { index, position -> output[position] = merged.getOrElse(index) { 0 } }
        }
        if (output == state.board) return MoveResult(state, false, 0)
        val spawned = if (spawnTile) spawn(output, random) else output
        return MoveResult(
            GameState(spawned, state.score + gained, state.won || spawned.any { it >= 2048 }, !canMove(spawned)),
            true,
            gained
        )
    }

    fun canMove(board: List<Int>): Boolean {
        require(board.size == 16)
        if (board.any { it == 0 }) return true
        return (0..15).any { index ->
            (index % 4 < 3 && board[index] == board[index + 1]) ||
                (index / 4 < 3 && board[index] == board[index + 4])
        }
    }

    private fun spawn(board: List<Int>, random: Random): List<Int> {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return board
        return board.toMutableList().apply {
            this[empty[random.nextInt(empty.size)]] = if (random.nextDouble() < 0.9) 2 else 4
        }
    }
}

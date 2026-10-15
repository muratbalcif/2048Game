package com.modern2048.game

/** Same unsigned xorshift32 and spawn order as backend/src/replay.ts. */
data class VerifiedGame(val sessionId: String, val playerId: String, val seed: Long, val moves: String = "")

object VerifiedEngine {
    private fun next(seed: Long): Long {
        var x = seed and 0xffffffffL
        x = (x xor (x shl 13)) and 0xffffffffL
        x = x xor (x ushr 17)
        x = (x xor (x shl 5)) and 0xffffffffL
        return x
    }
    private fun spawn(board: List<Int>, seed: Long): Pair<List<Int>, Long> {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return board to seed
        val indexSeed = next(seed)
        val valueSeed = next(indexSeed)
        val output = board.toMutableList()
        output[empty[(indexSeed % empty.size).toLong().toInt()]] = if (valueSeed % 10L == 0L) 4 else 2
        return output to valueSeed
    }
    fun initial(seed: Long): GameState {
        val (first, s1) = spawn(List(16) { 0 }, seed)
        return GameState(board = spawn(first, s1).first)
    }
    fun replay(seed: Long, moves: String): GameState? {
        if (moves.length > 20000 || moves.any { it !in "UDLR" }) return null
        var board = List(16) { 0 }
        val a = spawn(board, seed); board = a.first
        var random = a.second
        val b = spawn(board, random); board = b.first; random = b.second
        var state = GameState(board = board)
        for (move in moves) {
            val direction = when (move) { 'U' -> Direction.UP; 'D' -> Direction.DOWN; 'L' -> Direction.LEFT; else -> Direction.RIGHT }
            val merged = GameEngine.moveWithoutSpawn(state, direction)
            if (!merged.moved) return null
            val spawned = spawn(merged.state.board, random)
            random = spawned.second
            val next = spawned.first
            state = GameState(next, merged.state.score, merged.state.won || next.any { it >= 2048 }, !GameEngine.canMove(next))
        }
        return state
    }
    fun letter(direction: Direction): Char = when (direction) {
        Direction.UP -> 'U'; Direction.DOWN -> 'D'; Direction.LEFT -> 'L'; Direction.RIGHT -> 'R'
    }
}

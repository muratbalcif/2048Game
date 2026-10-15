package com.modern2048.game

/** Deterministic movement metadata for animating a 4×4 board. */
data class TileMotion(val from: Int, val to: Int, val value: Int, val merged: Boolean)

object TileMotionPlanner {
    fun plan(board: List<Int>, direction: Direction): List<TileMotion> {
        require(board.size == 16)
        val motions = mutableListOf<TileMotion>()
        for (line in 0..3) {
            val indices = (0..3).map { offset ->
                when (direction) {
                    Direction.LEFT -> line * 4 + offset
                    Direction.RIGHT -> line * 4 + 3 - offset
                    Direction.UP -> offset * 4 + line
                    Direction.DOWN -> (3 - offset) * 4 + line
                }
            }
            val occupied = indices.filter { board[it] != 0 }
            var source = 0
            var target = 0
            while (source < occupied.size) {
                val first = occupied[source]
                val merge = source + 1 < occupied.size && board[first] == board[occupied[source + 1]]
                val destination = indices[target]
                motions += TileMotion(first, destination, board[first], merge)
                if (merge) {
                    val second = occupied[source + 1]
                    motions += TileMotion(second, destination, board[second], true)
                }
                source += if (merge) 2 else 1
                target++
            }
        }
        return motions
    }
}

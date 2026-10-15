package com.modern2048.game

import org.junit.Assert.*
import org.junit.Test

class TileMotionPlannerTest {
    @Test fun leftMerge() {
        val board = listOf(2, 2, 4, 0) + List(12) { 0 }
        val motions = TileMotionPlanner.plan(board, Direction.LEFT)
        assertEquals(listOf(0, 0, 1), motions.map { it.to })
        assertEquals(listOf(true, true, false), motions.map { it.merged })
    }

    @Test fun rightMerge() {
        val board = listOf(2, 2, 4, 0) + List(12) { 0 }
        val motions = TileMotionPlanner.plan(board, Direction.RIGHT)
        assertEquals(listOf(3, 2, 2), motions.map { it.to })
    }

    @Test fun verticalMovement() {
        val board = listOf(2, 0, 0, 0) + List(8) { 0 } + listOf(2, 0, 0, 0)
        val motions = TileMotionPlanner.plan(board, Direction.UP)
        assertEquals(listOf(0, 0), motions.map { it.to })
        assertTrue(motions.all { it.merged })
    }

    @Test fun noMergeChain() {
        val board = listOf(2, 2, 2, 2) + List(12) { 0 }
        val motions = TileMotionPlanner.plan(board, Direction.LEFT)
        assertEquals(listOf(0, 0, 1, 1), motions.map { it.to })
    }
}

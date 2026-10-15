package com.modern2048.game

import org.junit.Assert.*
import org.junit.Test

class GameModeTest {
    @Test fun target1024WinsBeforeClassic() {
        val board = listOf(1024) + List(15) { 0 }
        assertTrue(GameMode.TARGET_1024.reached(board))
        assertFalse(GameMode.CLASSIC.reached(board))
        assertFalse(GameMode.ENDLESS.reached(board))
    }

    @Test fun endlessNeverTriggersTargetWin() {
        assertFalse(GameMode.ENDLESS.reached(List(16) { 2048 }))
    }
}

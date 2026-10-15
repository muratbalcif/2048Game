package com.modern2048.game

import org.junit.Assert.*
import org.junit.Test

class RewardedContinueTest {
    private fun state(score: Int, lost: Boolean = false) =
        GameState(board = List(16) { 0 }, score = score, lost = lost)

    @Test fun restoresOldestOfThreeMoves() {
        val reward = RewardedContinue()
            .record(state(10)).record(state(20)).record(state(30))
        val (restored, next) = reward.redeem(state(40, lost = true))
        assertEquals(10, restored.score)
        assertTrue(next.used)
        assertTrue(next.history.isEmpty())
    }

    @Test fun onlyKeepsLastThreeMoves() {
        val reward = (1..5).fold(RewardedContinue()) { acc, i -> acc.record(state(i)) }
        assertEquals(listOf(3, 4, 5), reward.history.map { it.score })
    }

    @Test fun cannotRedeemTwiceOrBeforeLoss() {
        val reward = RewardedContinue().record(state(1))
        assertEquals(state(2), reward.redeem(state(2)).first)
        val (restored, spent) = reward.redeem(state(3, lost = true))
        assertEquals(1, restored.score)
        assertEquals(state(4, lost = true), spent.redeem(state(4, lost = true)).first)
    }

    @Test fun resetRestoresEligibility() {
        val spent = RewardedContinue(used = true)
        assertFalse(spent.available)
        assertFalse(spent.reset().used)
    }
}

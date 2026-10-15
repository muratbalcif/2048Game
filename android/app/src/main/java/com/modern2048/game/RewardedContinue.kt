package com.modern2048.game

/**
 * Reward must be granted only by the ad SDK's earned-reward callback.
 * Never grant it when the ad merely opens, closes, or fails.
 */
data class RewardedContinue(
    val history: List<GameState> = emptyList(),
    val used: Boolean = false
) {
    val available: Boolean get() = history.isNotEmpty()

    fun record(previous: GameState): RewardedContinue =
        copy(history = (history + previous).takeLast(3))

    fun redeem(current: GameState): Pair<GameState, RewardedContinue> {
        if (!current.lost || !available) return current to this
        return history.first() to copy(history = emptyList(), used = false)
    }

    fun reset(): RewardedContinue = RewardedContinue()
}

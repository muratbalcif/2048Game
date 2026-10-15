package com.modern2048.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persist a complete snapshot after each successful move. */
class GameStorage(context: Context) {
    private val prefs = context.getSharedPreferences("modern2048", Context.MODE_PRIVATE)

    fun load(): GameState? = runCatching {
        val raw = prefs.getString("game", null) ?: return null
        val json = JSONObject(raw)
        val cells = json.getJSONArray("board")
        if (cells.length() != 16) return null
        val board = (0 until 16).map { cells.getInt(it) }
        if (board.any { it < 0 || (it != 0 && (it < 2 || (it and (it - 1)) != 0)) }) return null
        val score = json.getInt("score")
        if (score < 0) return null
        GameState(board, score, json.getBoolean("won"), json.getBoolean("lost"))
    }.getOrNull()

    fun save(state: GameState) {
        val json = JSONObject()
            .put("board", JSONArray(state.board))
            .put("score", state.score)
            .put("won", state.won)
            .put("lost", state.lost)
        prefs.edit().putString("game", json.toString()).apply()
    }

    fun loadReward(): RewardedContinue = runCatching {
        val raw = prefs.getString("reward", null) ?: return RewardedContinue()
        val json = JSONObject(raw)
        val states = json.getJSONArray("history")
        if (states.length() > 3) return RewardedContinue()
        val history = (0 until states.length()).map { i ->
            val entry = states.getJSONObject(i)
            val values = entry.getJSONArray("board")
            require(values.length() == 16)
            val board = (0 until 16).map { values.getInt(it) }
            require(board.all { it == 0 || (it >= 2 && (it and (it - 1)) == 0) })
            val score = entry.getInt("score")
            require(score >= 0)
            GameState(board, score, entry.getBoolean("won"), entry.getBoolean("lost"))
        }
        RewardedContinue(history, json.getBoolean("used"))
    }.getOrDefault(RewardedContinue())

    fun saveReward(reward: RewardedContinue) {
        val history = JSONArray()
        reward.history.forEach { state ->
            history.put(JSONObject()
                .put("board", JSONArray(state.board))
                .put("score", state.score)
                .put("won", state.won)
                .put("lost", state.lost))
        }
        prefs.edit().putString("reward", JSONObject()
            .put("history", history)
            .put("used", reward.used).toString()).apply()
    }

    fun soundEnabled(): Boolean = prefs.getBoolean("sound_enabled", true)
    fun hapticsEnabled(): Boolean = prefs.getBoolean("haptics_enabled", true)
    fun saveSoundEnabled(enabled: Boolean) { prefs.edit().putBoolean("sound_enabled", enabled).apply() }
    fun saveHapticsEnabled(enabled: Boolean) { prefs.edit().putBoolean("haptics_enabled", enabled).apply() }

    fun best(): Int = prefs.getInt("best", 0)
    fun saveBest(score: Int) { prefs.edit().putInt("best", maxOf(best(), score)).apply() }
}

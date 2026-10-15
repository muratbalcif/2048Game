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
        if (board.any { it < 0 || (it != 0 && (it and (it - 1)) != 0) }) return null
        GameState(board, json.getInt("score"), json.getBoolean("won"), json.getBoolean("lost"))
    }.getOrNull()

    fun save(state: GameState) {
        val json = JSONObject()
            .put("board", JSONArray(state.board))
            .put("score", state.score)
            .put("won", state.won)
            .put("lost", state.lost)
        prefs.edit().putString("game", json.toString()).apply()
    }

    fun best(): Int = prefs.getInt("best", 0)
    fun saveBest(score: Int) { prefs.edit().putInt("best", maxOf(best(), score)).apply() }
}

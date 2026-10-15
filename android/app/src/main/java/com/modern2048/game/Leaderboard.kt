package com.modern2048.game

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class RankEntry(val rank: Int, val name: String, val score: Int, val mine: Boolean)
data class LeaderboardResult(val entries: List<RankEntry>, val myRank: Int?, val error: String? = null)

class Leaderboard(private val context: Context) {
    private val prefs = context.getSharedPreferences("modern2048_leaderboard", Context.MODE_PRIVATE)
    private val playerId: String by lazy {
        prefs.getString("player_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("player_id", it).apply()
        }
    }
    private val base = BuildConfig.LEADERBOARD_BASE_URL.trimEnd('/')
    val configured: Boolean get() = base.startsWith("https://") && !base.contains("REPLACE_")
    private fun mode(value: GameMode): String = when (value) {
        GameMode.CLASSIC -> "classic"
        GameMode.TARGET_1024 -> "target1024"
        GameMode.ENDLESS -> "endless"
    }

    // Only server-seeded and replay-verified sessions may enter the global ranking.
    // Offline/local game scores must never be submitted as verified scores.
    suspend fun publishAndLoad(gameMode: GameMode, score: Int): LeaderboardResult = load(gameMode)

    suspend fun load(gameMode: GameMode): LeaderboardResult = withContext(Dispatchers.IO) {
        if (!configured) return@withContext LeaderboardResult(emptyList(), null, "Global sıralama henüz etkin değil")
        try {
            val connection = URL("$base/v1/leaderboard?mode=${mode(gameMode)}&playerId=$playerId").openConnection() as HttpURLConnection
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val json = JSONObject(response)
            val rows = json.getJSONArray("entries")
            val entries = (0 until minOf(rows.length(), 5)).map { index ->
                val item = rows.getJSONObject(index)
                RankEntry(item.getInt("rank"), item.getString("nickname"), item.getInt("score"), item.getString("playerId") == playerId)
            }
            val me = json.optJSONObject("me")
            LeaderboardResult(entries, me?.getInt("rank"))
        } catch (_: Exception) {
            LeaderboardResult(emptyList(), null, "Sıralama yüklenemedi. İnternet bağlantını kontrol et.")
        }
    }
}

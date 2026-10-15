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
    var nickname: String
        get() = prefs.getString("nickname", null) ?: "Oyuncu-${playerId.take(6)}"
        set(value) { prefs.edit().putString("nickname", value.trim()).apply() }
    suspend fun updateNickname(value: String): Boolean = withContext(Dispatchers.IO) {
        val name = value.trim()
        val sessionId = prefs.getString("proof_session_id", null)
        if (!name.matches(Regex("[\\p{L}\\p{N} _.-]{2,20}")) ||
            sessionId == null || !configured) return@withContext false
        try {
            val body = JSONObject().put("playerId", playerId).put("sessionId", sessionId)
                .put("nickname", name).toString()
            val conn = URL("$base/v2/players/nickname").openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val ok = conn.responseCode == 200
            conn.disconnect()
            if (ok) nickname = name
            ok
        } catch (_: Exception) { false }
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

    suspend fun startVerified(mode: GameMode): VerifiedGame? = withContext(Dispatchers.IO) {
        if (!configured) return@withContext null
        try {
            val request = JSONObject().put("playerId", playerId).put("nickname", nickname)
                .put("mode", mode(mode)).toString()
            val conn = URL("$base/v2/sessions").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(request.toByteArray()) }
            if (conn.responseCode != 201) { conn.disconnect(); return@withContext null }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()
            prefs.edit().putString("proof_session_id", json.getString("sessionId")).apply()
            VerifiedGame(json.getString("sessionId"), playerId, json.getLong("seed"))
        } catch (_: Exception) { null }
    }

    suspend fun finalizeVerified(session: VerifiedGame, score: Int): Boolean = withContext(Dispatchers.IO) {
        if (!configured || session.moves.isEmpty()) return@withContext false
        try {
            val body = JSONObject().put("sessionId", session.sessionId).put("playerId", session.playerId)
                .put("moves", session.moves).put("score", score).toString()
            val conn = URL("$base/v2/sessions/finalize").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val accepted = conn.responseCode == 200
            conn.disconnect()
            accepted
        } catch (_: Exception) { false }
    }

    /** Returns true only after the backend records a Google-signed SSV reward. */
    suspend fun isRewardVerified(session: VerifiedGame): Boolean = withContext(Dispatchers.IO) {
        if (!configured) return@withContext false
        try {
            val conn = URL("$base/v2/sessions/reward-status?sessionId=${session.sessionId}&playerId=${session.playerId}")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            val verified = conn.responseCode == 200 &&
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optBoolean("credited", false)
            conn.disconnect()
            verified
        } catch (_: Exception) { false }
    }

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

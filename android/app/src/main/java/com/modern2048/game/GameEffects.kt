package com.modern2048.game

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class GameEffects(context: Context) {
    private val app = context.applicationContext
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        app.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        app.getSystemService(Vibrator::class.java)
    }
    private var tone: ToneGenerator? = null

    fun play(scoreDelta: Int, won: Boolean) {
        val duration = when {
            won -> 80L
            scoreDelta >= 64 -> 45L
            scoreDelta > 0 -> 25L
            else -> 12L
        }
        vibrator?.takeIf { it.hasVibrator() }?.vibrate(
            VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
        )
        if (won || scoreDelta > 0) {
            val generator = tone ?: runCatching {
                ToneGenerator(AudioManager.STREAM_MUSIC, 30).also { tone = it }
            }.getOrNull()
            if (won) generator?.startTone(ToneGenerator.TONE_PROP_ACK, 140)
            else generator?.startTone(ToneGenerator.TONE_PROP_BEEP, 65)
        }
    }

    fun release() { tone?.release(); tone = null }
}

@Composable
fun rememberGameEffects(context: Context): GameEffects = remember(context) { GameEffects(context) }

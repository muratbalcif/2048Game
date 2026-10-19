package com.modern2048.game

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Counts actual cold launches and new-game transitions.
 * Shows ads only at a transition, never during active gameplay.
 */
class InterstitialAds(private val activity: Activity) {
    private val preferences = activity.getSharedPreferences("modern2048_interstitial", Activity.MODE_PRIVATE)
    private val isDebug = activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private var ad: InterstitialAd? = null
    private var loading = false
    private var showing = false
    private var launchDue = false
    private var gameDue = false

    init {
        val launches = preferences.getInt("launches", 0) + 1
        preferences.edit().putInt("launches", launches).apply()
        launchDue = launches % 4 == 0
        Log.d("Modern2048Ads", "launches=$launches launchDue=$launchDue")
        load()
    }

    fun onHomeReady() {
        if (!launchDue) return
        // Give the first screen time to appear before attempting a full-screen ad.
        Handler(Looper.getMainLooper()).postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) {
                showIfReady { launchDue = false }
            }
        }, 1500)
    }

    fun onNewGame() {
        val games = preferences.getInt("games", 0) + 1
        preferences.edit().putInt("games", games).apply()
        if (games % 3 == 0) gameDue = true
        Log.d("Modern2048Ads", "games=$games gameDue=$gameDue")
        if (gameDue) showIfReady { gameDue = false }
    }

    private fun load() {
        if (loading || ad != null || showing) return
        loading = true
        val unitId = if (isDebug) TEST_UNIT_ID else PRODUCTION_UNIT_ID
        InterstitialAd.load(activity, unitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(loaded: InterstitialAd) {
                loading = false
                ad = loaded
                Log.d("Modern2048Ads", "interstitial loaded")
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loading = false
                ad = null
                Log.w("Modern2048Ads", "interstitial load failed code=${error.code} message=${error.message}")
            }
        })
    }

    private fun showIfReady(onShown: () -> Unit) {
        if (showing) return
        val ready = ad ?: run { Log.d("Modern2048Ads", "interstitial not ready at transition"); load(); return }
        showing = true
        ad = null
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() { Log.d("Modern2048Ads", "interstitial shown"); onShown() }
            override fun onAdDismissedFullScreenContent() {
                showing = false
                load()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w("Modern2048Ads", "interstitial show failed: ${error.message}")
                showing = false
                load()
            }
        }
        ready.show(activity)
    }

    companion object {
        private const val TEST_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val PRODUCTION_UNIT_ID = "ca-app-pub-9057596586284356/7302769279"
    }
}

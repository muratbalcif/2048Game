package com.modern2048.game

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/** Google official rewarded test unit. Never use this ID in a production release. */
class RewardedAds(private val activity: Activity) {
    private var ad: RewardedAd? = null
    private var loading = false
    private var showing = false
    var onAvailabilityChanged: ((Boolean) -> Unit)? = null

    private val testBuild: Boolean = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    init {
       MobileAds.initialize(activity) { activity.runOnUiThread { load() } }
    }

    fun load() {
        if (loading || showing || ad != null) return
        loading = true
        RewardedAd.load(
            activity,
            if (testBuild) TEST_UNIT_ID else REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    loading = false
                    ad = loaded
                    Log.d("Modern2048Ads", "rewarded loaded")
                    onAvailabilityChanged?.invoke(true)
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    ad = null
                    Log.w("Modern2048Ads", "rewarded load failed code=${error.code}: ${error.message}")
                    onAvailabilityChanged?.invoke(false)
                }
            }
        )
    }

    fun show(onRewardEarned: () -> Unit, sessionId: String? = null, playerId: String? = null) {
        val ready = ad ?: return
        if (showing) return
        if (sessionId != null && playerId != null) {
            ready.setServerSideVerificationOptions(
                ServerSideVerificationOptions.Builder()
                    .setCustomData(sessionId)
                    .setUserId(playerId)
                    .build()
            )
        }
        showing = true
        ad = null
        onAvailabilityChanged?.invoke(false)
        var rewarded = false
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showing = false
                load()
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                Log.w("Modern2048Ads", "rewarded show failed: ${error.message}")
                showing = false
                load()
            }
        }
        ready.show(activity) {
            if (!rewarded) {
                rewarded = true
                onRewardEarned()
            }
        }
    }

    companion object {
        private const val TEST_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        private const val REWARDED_UNIT_ID = "ca-app-pub-9057596586284356/1602829386"
    }
}

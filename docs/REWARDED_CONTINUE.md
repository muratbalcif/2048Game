# Rewarded continue — 3-move rewind

## Product behavior
- Offer an optional rewarded ad only on game over.
- Reward: rewind the last three successful moves (or fewer if only fewer are available).
- Restore the oldest recorded pre-move snapshot, including score and win/loss flags.
- Do not restore the global best score backwards.
- One rewarded continue per game. Reset only on New Game.
- If an ad is unavailable, the user can still restart or use the normal undo.
- Never grant reward on ad open, dismiss, load failure, or impression. Only the SDK's earned-reward callback may redeem.
- Hide the rewarded option after redemption.

## Integration requirements
- Android: Google Mobile Ads RewardedAd; iOS: Google Mobile Ads GADRewardedAd.
- Use official test ad unit IDs in debug; configure real IDs per platform for release.
- Consent/privacy flow and store declarations are required before production.
- Persist last three snapshots and reward-used state across process death, and handle stale callbacks safely.
- A single ad should trigger exactly one redemption even if callbacks are repeated.
- Avoid reward stacking, multiple simultaneous ad presentations and forced ad viewing.

## Current implementation
- Android and iOS use Google's official rewarded test app/unit IDs.
- SDK load/present/earned-reward callbacks are wired to game-over UI.
- The last three successful move snapshots and the one-use entitlement are persisted on both platforms.
- Manual undo also removes the matching rewind-history entry.
- Stale reward callbacks are ignored after the game state changes.
- Android unit tests cover history bounds, redemption and one-time use.
- Actual Android/iOS builds and device ad playback remain unverified.
- Production release requires real ad unit IDs, privacy/consent review, and release QA.


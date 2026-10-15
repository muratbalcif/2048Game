# Cross-platform global leaderboard (Cloudflare Workers + D1)

Android and iOS use the same `/v1/leaderboard?mode=classic&playerId=...` and `POST /v1/scores` API.

## Deploy
1. `cd backend && npm install`
2. `npx wrangler login`
3. `npx wrangler d1 create modern2048-leaderboard`
4. Replace the all-zero placeholder database ID in `wrangler.toml` with the real ID.
5. `npm run migrate && npm run deploy`
6. Set Android `LEADERBOARD_BASE_URL` Gradle property and iOS `LEADERBOARD_BASE_URL` build setting to the deployed HTTPS Worker URL. Never put a placeholder URL into production.\n\nAndroid example: `./gradlew :app:assembleDebug -PLEADERBOARD_BASE_URL=https://modern2048-leaderboard.<your-subdomain>.workers.dev` (run in `android/`). For iOS, set `LEADERBOARD_BASE_URL` in your Xcode build configuration (e.g. `xcodebuild ... LEADERBOARD_BASE_URL=https://...`), regenerate project using XcodeGen first. If no URL is configured, the game shows an offline/unconfigured leaderboard message and remains playable.

## Verified API v2
- `POST /v2/sessions` body `{playerId,nickname,mode}` issues a one-day session with a server-generated 32-bit seed and deterministic initial board.
- The client must use the deterministic xorshift32 spawn algorithm in `src/replay.ts`, recording **only valid moves** as a string of `U`, `D`, `L`, `R`.
- `POST /v2/sessions/finalize` body `{sessionId,playerId,moves,score}` replays the entire game. The score is accepted only if the board is genuinely lost, all moves are valid, and the replayed score matches.
- `GET /v1/leaderboard?mode=classic&playerId=<UUID>` returns only `verified=1` scores.
- `POST /v1/scores` returns HTTP 410; legacy unverified submissions are disabled.

**Mobile integration implemented in code:** both clients now expose a separate “Sıralamalı başlat” action, fetch a server seed, reproduce the deterministic game board, record accepted moves and finalize a lost game. Normal offline play remains separate. Ranked undo and rewarded rewind are disabled during the active session. **Release limitations:** ranked session and accepted moves are now stored locally on both platforms and checked against the saved board at startup. A restart or mode change deliberately forfeits the session; unfinished sessions expire after 24 hours. Finalization retry, persistence ordering under abrupt process death, real Android/iOS builds, and live Worker integration tests still need verification.

## Security limits
Replay validation rejects arbitrary score numbers, but does not establish a real human identity, prevent automation/bots, or prove that a game was played in real time. Anonymous UUIDs can be rotated and server sessions can be scripted. For competitive prize-bearing rankings, add attestation (Play Integrity / App Attest), edge abuse controls, and risk monitoring. Local undo/reward moves must not be mixed into a verified session unless the server protocol explicitly supports them.

## Deployment verification
After deployment, run:
```bash
curl -fsS https://YOUR-WORKER.workers.dev/health
curl -fsS 'https://YOUR-WORKER.workers.dev/v1/leaderboard?mode=classic'
```
Then build both apps with the **same HTTPS Worker base URL** and play one finished game per platform. Confirm that each platform can see the other platform's ranking. Never use the zero UUID as the production database ID.

## Fair-play release gate
This beta implementation accepts client-reported scores and client-supplied player UUIDs. No server replay verification or authenticated sessions exist. The current rate limit is per *untrusted* player ID and can be bypassed by rotating IDs. Do not present this as a tamper-resistant competition. Before a public competitive leaderboard:
- issue server-authenticated game sessions and server-controlled random seeds;
- upload a bounded move log, then replay and validate each move and resulting score on the server;
- enforce one-time finalization, time limits, and Cloudflare edge/IP-based abuse limits;
- decide how to handle legacy unverified scores and protect deletion/rename operations.

No Cloudflare account credentials or API tokens should be committed to GitHub.

## GitHub Actions production deployment (prepared)
The workflow `.github/workflows/deploy-leaderboard.yml` is manually triggered; it will not deploy on every push.

1. In Cloudflare, create a D1 database named `modern2048-leaderboard`.
2. Create a scoped API token with **Workers Scripts: Edit**, **D1: Edit**, and **Account: Read** permissions for the target account. Use the least privileges available for the deployment operations.
3. In GitHub repository **Settings → Environments**, create `leaderboard-production`. Optionally require a reviewer.
4. Add environment secrets `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, and `D1_DATABASE_ID`. The last one is the actual D1 database UUID from Cloudflare, **not** the all-zero placeholder.
5. Open **Actions → Deploy Global Leaderboard → Run workflow**. The workflow validates configuration, checks TypeScript, applies migrations, and deploys the Worker.
6. Get the actual Worker URL from Cloudflare, confirm `/health` and `/v1/leaderboard?mode=classic`, and configure both mobile apps to use that same HTTPS base URL.

No secrets or real Cloudflare account identifiers are required in the repository source. The production workflow will fail early if required secrets are absent.

### Important operational note
This workflow deploys the **beta score-submission API**. It does not add replay-based anti-cheat verification. Do not enable competitive rewards or claim tamper-proof rankings until server-side verification is implemented.

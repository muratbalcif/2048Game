# Cross-platform global leaderboard (Cloudflare Workers + D1)

Android and iOS use the same `/v1/leaderboard?mode=classic&playerId=...` and `POST /v1/scores` API.

## Deploy
1. `cd backend && npm install`
2. `npx wrangler login`
3. `npx wrangler d1 create modern2048-leaderboard`
4. Replace the all-zero placeholder database ID in `wrangler.toml` with the real ID.
5. `npm run migrate && npm run deploy`
6. Set Android `LEADERBOARD_BASE_URL` Gradle property and iOS `LEADERBOARD_BASE_URL` build setting to the deployed HTTPS Worker URL. Never put a placeholder URL into production.\n\nAndroid example: `./gradlew :app:assembleDebug -PLEADERBOARD_BASE_URL=https://modern2048-leaderboard.<your-subdomain>.workers.dev` (run in `android/`). For iOS, set `LEADERBOARD_BASE_URL` in your Xcode build configuration (e.g. `xcodebuild ... LEADERBOARD_BASE_URL=https://...`), regenerate project using XcodeGen first. If no URL is configured, the game shows an offline/unconfigured leaderboard message and remains playable.

## API
`GET /v1/leaderboard?mode=classic&playerId=<UUID>` returns `{mode,entries:[{playerId,nickname,score,updatedAt,rank}],me:{rank,score}|null}`.
`POST /v1/scores` JSON: `{playerId:"UUID",nickname:"Oyuncu",mode:"classic",score:100}`. Only a higher score replaces a player's existing best. Minimum interval: 10 seconds between submissions per player ID.

## Security caveat
The current protocol uses client-generated anonymous UUIDs and client-reported scores. It is **not cheat-proof**, even with throttling and validation; an attacker can spoof scores or UUIDs. Treat rankings as beta/unverified. For competitive public launch, implement server-issued sessions, replay-verified deterministic games, authenticated identities, and edge rate limits before promoting rankings as fair. Do not collect email or other PII.

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

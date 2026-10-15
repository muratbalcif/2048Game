# Cross-platform global leaderboard (Cloudflare Workers + D1)

Android and iOS use the same `/v1/leaderboard?mode=classic&playerId=...` and `POST /v1/scores` API.

## Deploy
1. `cd backend && npm install`
2. `npx wrangler login`
3. `npx wrangler d1 create modern2048-leaderboard`
4. Copy the resulting database ID into `wrangler.toml`.
5. `npm run migrate && npm run deploy`
6. Set Android `LEADERBOARD_BASE_URL` Gradle property and iOS `LEADERBOARD_BASE_URL` build setting to the deployed HTTPS Worker URL. Never put a placeholder URL into production.

## API
`GET /v1/leaderboard?mode=classic&playerId=<UUID>` returns `{mode,entries:[{playerId,nickname,score,updatedAt,rank}],me:{rank,score}|null}`.
`POST /v1/scores` JSON: `{playerId:"UUID",nickname:"Oyuncu",mode:"classic",score:100}`. Only a higher score replaces a player's existing best. Minimum interval: 10 seconds between submissions per player ID.

## Security caveat
The current protocol uses client-generated anonymous UUIDs and client-reported scores. It is **not cheat-proof**, even with throttling and validation; an attacker can spoof scores or UUIDs. Treat rankings as beta/unverified. For competitive public launch, implement server-issued sessions, replay-verified deterministic games, authenticated identities, and edge rate limits before promoting rankings as fair. Do not collect email or other PII.

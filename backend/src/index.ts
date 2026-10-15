import { newGame, replay } from "./replay";
interface Env { DB: D1Database; ALLOWED_ORIGIN: string }
type Mode = "classic" | "target1024" | "endless";
const modes: Mode[] = ["classic", "target1024", "endless"];
const headers = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" };
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers });
async function parsePayload(request: Request, maxBytes = 4096): Promise<Record<string, unknown> | Response> {
  try {
    const raw = await request.text();
    if (new TextEncoder().encode(raw).length > maxBytes) return json({ error: "too_large" }, 413);
    const body: unknown = JSON.parse(raw);
    if (!body || typeof body !== "object" || Array.isArray(body)) return json({ error: "invalid_json" }, 400);
    return body as Record<string, unknown>;
  } catch { return json({ error: "invalid_json" }, 400); }
}
const isId = (v: unknown): v is string => typeof v === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v);
const validMode = (v: unknown): v is Mode => typeof v === "string" && modes.includes(v as Mode);
const nickname = (v: unknown) => typeof v === "string" && /^[\p{L}\p{N} _.-]{2,20}$/u.test(v.trim()) ? v.trim() : null;
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") return json({ ok: true });
    if (request.method === "GET" && url.pathname === "/v1/leaderboard") {
      const mode = url.searchParams.get("mode") ?? "classic";
      if (!validMode(mode)) return json({ error: "invalid_mode" }, 400);
      const id = url.searchParams.get("playerId");
      const entries = await env.DB.prepare(
        `SELECT s.player_id AS playerId, p.nickname, s.score, s.updated_at AS updatedAt
         FROM scores s JOIN players p ON p.player_id=s.player_id
         WHERE s.mode=? AND s.verified=1 ORDER BY s.score DESC, s.updated_at ASC LIMIT 50`
      ).bind(mode).all();
      let me: unknown = null;
      if (isId(id)) {
        me = await env.DB.prepare(
          `SELECT rank, score FROM (
             SELECT player_id, score, ROW_NUMBER() OVER (ORDER BY score DESC, updated_at ASC) AS rank
             FROM scores WHERE mode=? AND verified=1
           ) WHERE player_id=?`
        ).bind(mode, id).first();
      }
      return json({ mode, entries: entries.results.map((entry, index) => ({ ...entry, rank: index + 1 })), me });
    }
    if (request.method === "POST" && url.pathname === "/v2/sessions") {
      const payload = await parsePayload(request);
      if (payload instanceof Response) return payload;
      const playerId = payload.playerId;
      const mode = payload.mode;
      const name = nickname(payload.nickname);
      if (!isId(playerId) || !validMode(mode) || !name) return json({ error: "invalid_session" }, 400);
      const now = Date.now();
      const active = await env.DB.prepare(
        "SELECT COUNT(*) AS count FROM verified_sessions WHERE player_id=? AND finalized_at IS NULL AND expires_at>?"
      ).bind(playerId, now).first<{count:number}>();
      if ((active?.count ?? 0) >= 3) return json({ error: "too_many_sessions" }, 429);
      const sessionId = crypto.randomUUID();
      const seed = new DataView(crypto.getRandomValues(new Uint8Array(4)).buffer).getUint32(0);
      const initial = newGame(seed).state;
      await env.DB.batch([
        env.DB.prepare("INSERT INTO players(player_id,nickname,created_at,updated_at) VALUES(?,?,?,?) ON CONFLICT(player_id) DO UPDATE SET nickname=excluded.nickname,updated_at=excluded.updated_at").bind(playerId,name,now,now),
        env.DB.prepare("INSERT INTO verified_sessions(session_id,player_id,mode,seed,created_at,expires_at) VALUES(?,?,?,?,?,?)").bind(sessionId,playerId,mode,seed,now,now+24*60*60*1000)
      ]);
      return json({ sessionId, seed, board: initial.board, expiresAt: now+24*60*60*1000 }, 201);
    }
    if (request.method === "POST" && url.pathname === "/v2/sessions/finalize") {
      const payload = await parsePayload(request, 25000);
      if (payload instanceof Response) return payload;
      if (!isId(payload.sessionId) || !isId(payload.playerId) ||
          typeof payload.moves !== "string" || typeof payload.score !== "number" ||
          !Number.isSafeInteger(payload.score)) return json({ error: "invalid_submission" }, 400);
      const session = await env.DB.prepare(
        "SELECT player_id,mode,seed,expires_at,finalized_at FROM verified_sessions WHERE session_id=?"
      ).bind(payload.sessionId).first<{player_id:string;mode:Mode;seed:number;expires_at:number;finalized_at:number|null}>();
      if (!session || session.player_id !== payload.playerId || session.finalized_at !== null ||
          session.expires_at < Date.now()) return json({ error: "invalid_or_expired_session" }, 409);
      const result = replay(session.seed, payload.moves);
      if (!result || !result.state.lost || result.state.score !== payload.score)
        return json({ error: "replay_mismatch_or_unfinished" }, 422);
      const now = Date.now();
      const rewarded = await env.DB.prepare(
        "SELECT transaction_id FROM verified_ad_rewards WHERE session_id=? AND reward_type='double_score'"
      ).bind(payload.sessionId).first();
      const finalScore = rewarded ? Math.min(result.state.score * 2, 2147483647) : result.state.score;
      // A conditional update makes finalization idempotence enforceable across concurrent requests.
      const claimed = await env.DB.prepare(
        "UPDATE verified_sessions SET finalized_at=?,base_score=? WHERE session_id=? AND finalized_at IS NULL AND expires_at>=?"
      ).bind(now,result.state.score,payload.sessionId,now).run();
      if (claimed.meta.changes !== 1) return json({ error: "already_finalized" }, 409);
      await env.DB.prepare(
        "INSERT INTO scores(player_id,mode,score,updated_at,verified) VALUES(?,?,?,?,1) ON CONFLICT(player_id,mode) DO UPDATE SET score=excluded.score,updated_at=excluded.updated_at,verified=1 WHERE excluded.score>scores.score OR scores.verified=0"
      ).bind(payload.playerId,session.mode,finalScore,now).run();
      return json({ accepted: true, verified: true, score: finalScore, rewarded: !!rewarded });
    }
    if (request.method === "GET" && url.pathname === "/v1/admob/ssv") {
      const params = url.searchParams;
      const signature = params.get("signature");
      const keyId = params.get("key_id");
      const transactionId = params.get("transaction_id");
      const sessionId = params.get("custom_data");
      const playerId = params.get("user_id");
      const rewardItem = params.get("reward_item");
      if (!signature || !keyId || !transactionId || !isId(sessionId) || !isId(playerId) || rewardItem !== "double_score")
        return json({ error: "invalid_reward" }, 400);
      // Google signs the exact raw query preceding &signature=...&key_id=... .
      const raw = url.search.slice(1);
      const signatureIndex = raw.indexOf("&signature=");
      if (signatureIndex < 0 || !raw.slice(signatureIndex).startsWith("&signature="))
        return json({ error: "invalid_signature_order" }, 400);
      const signed = raw.slice(0, signatureIndex);
      if (!(await verifyAdMobSignature(signed, signature, keyId)))
        return json({ error: "invalid_signature" }, 403);
      const session = await env.DB.prepare(
        "SELECT player_id,mode,base_score,finalized_at FROM verified_sessions WHERE session_id=?"
      ).bind(sessionId).first<{player_id:string;mode:Mode;base_score:number|null;finalized_at:number|null}>();
      if (!session || session.player_id !== playerId) return json({ error: "unknown_session" }, 404);
      const now = Date.now();
      await env.DB.prepare(
        "INSERT OR IGNORE INTO verified_ad_rewards(transaction_id,session_id,player_id,reward_type,verified_at) VALUES(?,?,?,'double_score',?)"
      ).bind(transactionId,sessionId,playerId,now).run();
      if (session.finalized_at !== null && session.base_score !== null) {
        const reward = await env.DB.prepare(
          "SELECT transaction_id FROM verified_ad_rewards WHERE session_id=? AND reward_type='double_score'"
        ).bind(sessionId).first();
        if (reward) {
          const doubled = Math.min(session.base_score * 2, 2147483647);
          await env.DB.prepare(
            "INSERT INTO scores(player_id,mode,score,updated_at,verified) VALUES(?,?,?,?,1) ON CONFLICT(player_id,mode) DO UPDATE SET score=excluded.score,updated_at=excluded.updated_at,verified=1 WHERE excluded.score>scores.score OR scores.verified=0"
          ).bind(playerId,session.mode,doubled,now).run();
        }
      }
      return json({ ok: true });
    }
    if (request.method === "POST" && url.pathname === "/v1/scores") {
      return json({ error: "legacy_score_submission_disabled", migration: "/v2/sessions" }, 410);
    }
    return json({ error: "not_found" }, 404);
  }
} satisfies ExportedHandler<Env>;

/** AdMob SSV: verify Google's signed query with the published public keys. */
async function verifyAdMobSignature(signed: string, signature: string, keyId: string): Promise<boolean> {
  try {
    const response = await fetch("https://www.gstatic.com/admob/reward/verifier-keys.json");
    if (!response.ok) return false;
    const body = await response.json() as {keys: {keyId:number; pem:string}[]};
    const entry = body.keys.find(k => String(k.keyId) === keyId);
    if (!entry) return false;
    const der = Uint8Array.from(atob(entry.pem.replace(/-----[^-]+-----/g, "").replace(/\s/g, "")), c => c.charCodeAt(0));
    const key = await crypto.subtle.importKey("spki", der, {name:"ECDSA",namedCurve:"P-256"}, false, ["verify"]);
    const decoded = Uint8Array.from(atob(signature.replace(/-/g,"+").replace(/_/g,"/")), c => c.charCodeAt(0));
    // AdMob uses ASN.1 DER ECDSA signatures; WebCrypto requires IEEE-P1363 r||s.
    const raw = ecdsaDerToRaw(decoded);
    return await crypto.subtle.verify({name:"ECDSA",hash:"SHA-256"},key,raw,new TextEncoder().encode(signed));
  } catch { return false; }
}
function ecdsaDerToRaw(der: Uint8Array): Uint8Array {
  if (der[0] !== 0x30) throw new Error("invalid DER");
  let offset = 2;
  if (der[1] & 0x80) offset = 2 + (der[1] & 0x7f);
  const raw = new Uint8Array(64);
  for (let part = 0; part < 2; part++) {
    if (der[offset++] !== 0x02) throw new Error("invalid INTEGER");
    const length = der[offset++];
    const value = der.slice(offset, offset + length);
    offset += length;
    const trimmed = value[0] === 0 ? value.slice(1) : value;
    if (trimmed.length > 32) throw new Error("invalid integer size");
    raw.set(trimmed, part * 32 + 32 - trimmed.length);
  }
  return raw;
}

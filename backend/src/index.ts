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
      // A conditional update makes finalization idempotence enforceable across concurrent requests.
      const claimed = await env.DB.prepare(
        "UPDATE verified_sessions SET finalized_at=? WHERE session_id=? AND finalized_at IS NULL AND expires_at>=?"
      ).bind(now,payload.sessionId,now).run();
      if (claimed.meta.changes !== 1) return json({ error: "already_finalized" }, 409);
      await env.DB.prepare(
        "INSERT INTO scores(player_id,mode,score,updated_at,verified) VALUES(?,?,?,?,1) ON CONFLICT(player_id,mode) DO UPDATE SET score=excluded.score,updated_at=excluded.updated_at,verified=1 WHERE excluded.score>scores.score OR scores.verified=0"
      ).bind(payload.playerId,session.mode,result.state.score,now).run();
      return json({ accepted: true, verified: true, score: result.state.score });
    }
    if (request.method === "POST" && url.pathname === "/v1/scores") {
      return json({ error: "legacy_score_submission_disabled", migration: "/v2/sessions" }, 410);
    }
    return json({ error: "not_found" }, 404);
  }
} satisfies ExportedHandler<Env>;

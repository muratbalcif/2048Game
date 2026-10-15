interface Env { DB: D1Database; ALLOWED_ORIGIN: string }
type Mode = "classic" | "target1024" | "endless";
const modes: Mode[] = ["classic", "target1024", "endless"];
const headers = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" };
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers });
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
         WHERE s.mode=? ORDER BY s.score DESC, s.updated_at ASC LIMIT 50`
      ).bind(mode).all();
      let me: unknown = null;
      if (isId(id)) {
        me = await env.DB.prepare(
          `SELECT rank, score FROM (
             SELECT player_id, score, ROW_NUMBER() OVER (ORDER BY score DESC, updated_at ASC) AS rank
             FROM scores WHERE mode=?
           ) WHERE player_id=?`
        ).bind(mode, id).first();
      }
      return json({ mode, entries: entries.results.map((entry, index) => ({ ...entry, rank: index + 1 })), me });
    }
    if (request.method === "POST" && url.pathname === "/v1/scores") {
      const length = Number(request.headers.get("content-length") ?? 0);
      if (length > 4096) return json({ error: "too_large" }, 413);
      let body: Record<string, unknown>;
      try {
        const raw = await request.text();
        if (raw.length > 4096) return json({ error: "too_large" }, 413);
        body = JSON.parse(raw) as Record<string, unknown>;
      } catch { return json({ error: "invalid_json" }, 400); }
      if (!body || typeof body !== "object" || Array.isArray(body)) return json({ error: "invalid_submission" }, 400);
      const { playerId, mode, score } = body;
      const name = nickname(body.nickname);
      if (!isId(playerId) || !validMode(mode) || typeof score !== "number" || !Number.isSafeInteger(score) || score < 0 || score > 2_000_000_000 || !name)
        return json({ error: "invalid_submission" }, 400);
      const now = Date.now();
      // This is abuse throttling, NOT cryptographic score verification.
      const last = await env.DB.prepare("SELECT last_submission FROM submission_limits WHERE player_id=?").bind(playerId).first<{last_submission:number}>();
      if (last && now - last.last_submission < 10_000) return json({ error: "rate_limited" }, 429);
      await env.DB.batch([
        env.DB.prepare("INSERT INTO players(player_id,nickname,created_at,updated_at) VALUES(?,?,?,?) ON CONFLICT(player_id) DO UPDATE SET nickname=excluded.nickname,updated_at=excluded.updated_at").bind(playerId,name,now,now),
        env.DB.prepare("INSERT INTO scores(player_id,mode,score,updated_at) VALUES(?,?,?,?) ON CONFLICT(player_id,mode) DO UPDATE SET score=excluded.score,updated_at=excluded.updated_at WHERE excluded.score > scores.score").bind(playerId,mode,score,now),
        env.DB.prepare("INSERT INTO submission_limits(player_id,last_submission) VALUES(?,?) ON CONFLICT(player_id) DO UPDATE SET last_submission=excluded.last_submission").bind(playerId,now)
      ]);
      return json({ accepted: true });
    }
    return json({ error: "not_found" }, 404);
  }
} satisfies ExportedHandler<Env>;

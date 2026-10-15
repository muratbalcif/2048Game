CREATE TABLE IF NOT EXISTS verified_sessions (
  session_id TEXT PRIMARY KEY,
  player_id TEXT NOT NULL,
  mode TEXT NOT NULL CHECK (mode IN ('classic','target1024','endless')),
  seed INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  finalized_at INTEGER,
  FOREIGN KEY (player_id) REFERENCES players(player_id)
);
CREATE INDEX IF NOT EXISTS idx_verified_sessions_expiry ON verified_sessions(expires_at);
ALTER TABLE scores ADD COLUMN verified INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS players (
  player_id TEXT PRIMARY KEY,
  nickname TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS scores (
  player_id TEXT NOT NULL,
  mode TEXT NOT NULL CHECK (mode IN ('classic','target1024','endless')),
  score INTEGER NOT NULL CHECK (score >= 0),
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (player_id, mode),
  FOREIGN KEY (player_id) REFERENCES players(player_id)
);
CREATE INDEX IF NOT EXISTS idx_scores_rank ON scores(mode, score DESC, updated_at ASC);
CREATE TABLE IF NOT EXISTS submission_limits (
  player_id TEXT PRIMARY KEY,
  last_submission INTEGER NOT NULL
);

-- A reward is credited only after a server-verified AdMob SSV callback.
-- Never accept an unverified client-provided multiplier.
CREATE TABLE IF NOT EXISTS verified_ad_rewards (
  transaction_id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL,
  player_id TEXT NOT NULL,
  reward_type TEXT NOT NULL CHECK (reward_type = 'double_score'),
  verified_at INTEGER NOT NULL,
  FOREIGN KEY (session_id) REFERENCES verified_sessions(session_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_one_double_reward_per_session
  ON verified_ad_rewards(session_id, reward_type);

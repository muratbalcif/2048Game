ALTER TABLE verified_sessions ADD COLUMN base_score INTEGER;
CREATE INDEX IF NOT EXISTS idx_verified_rewards_session ON verified_ad_rewards(session_id);

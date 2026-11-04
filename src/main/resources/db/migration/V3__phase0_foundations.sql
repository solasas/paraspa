-- Phase 0 foundations: avatars, integrity constraints, pagination indexes.
-- Idempotent so it is safe on databases created by Hibernate before Flyway.

-- 1. Avatar
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);

-- 2. One row per (user, skill, type) and one rating per (session, rater).
--    Remove any pre-existing duplicates first (keep the oldest row) so the constraints can be added.
DELETE FROM user_skills a
    USING user_skills b
    WHERE a.id > b.id
      AND a.user_id = b.user_id
      AND a.skill_id = b.skill_id
      AND a.type = b.type;

DELETE FROM ratings a
    USING ratings b
    WHERE a.id > b.id
      AND a.session_id = b.session_id
      AND a.rated_by_id = b.rated_by_id;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_user_skills_user_skill_type') THEN
        ALTER TABLE user_skills
            ADD CONSTRAINT uk_user_skills_user_skill_type UNIQUE (user_id, skill_id, type);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_ratings_session_rated_by') THEN
        ALTER TABLE ratings
            ADD CONSTRAINT uk_ratings_session_rated_by UNIQUE (session_id, rated_by_id);
    END IF;
END $$;

-- 3. Indexes behind the paginated list queries (Postgres does not index foreign keys on its own)
CREATE INDEX IF NOT EXISTS idx_skill_exchanges_requester ON skill_exchanges (requester_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_skill_exchanges_receiver  ON skill_exchanges (receiver_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_sender_receiver  ON messages (sender_id, receiver_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_receiver_sender  ON messages (receiver_id, sender_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_author_created      ON posts (author_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_skill_created       ON posts (skill_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_post_comments_post        ON post_comments (post_id, created_at);
CREATE INDEX IF NOT EXISTS idx_follows_following         ON follows (following_id);

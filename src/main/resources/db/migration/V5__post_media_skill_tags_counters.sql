-- Posts rework: image attachments, many-to-many skill tags, stored counters, cursor-friendly index.

-- 1. Text becomes optional (a post may be images only)
ALTER TABLE posts ALTER COLUMN content DROP NOT NULL;

-- 2. Denormalised counters, kept in step by the application with atomic UPDATEs
ALTER TABLE posts ADD COLUMN IF NOT EXISTS like_count    BIGINT NOT NULL DEFAULT 0;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS comment_count BIGINT NOT NULL DEFAULT 0;

UPDATE posts p SET
    like_count    = (SELECT COUNT(*) FROM post_likes    l WHERE l.post_id = p.id),
    comment_count = (SELECT COUNT(*) FROM post_comments c WHERE c.post_id = p.id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_posts_counts_non_negative') THEN
        ALTER TABLE posts
            ADD CONSTRAINT chk_posts_counts_non_negative CHECK (like_count >= 0 AND comment_count >= 0);
    END IF;
END $$;

-- 3. Image attachments: at most 4 per post (positions 0-3), removed together with the post
CREATE TABLE IF NOT EXISTS post_media (
    id       BIGSERIAL PRIMARY KEY,
    post_id  BIGINT       NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    url      VARCHAR(500) NOT NULL,
    position INTEGER      NOT NULL,
    CONSTRAINT uk_post_media_post_position UNIQUE (post_id, position),
    CONSTRAINT chk_post_media_position CHECK (position >= 0 AND position < 4)
);

-- 4. Skill tags: many-to-many. The old single, required posts.skill_id becomes one tag per existing post.
CREATE TABLE IF NOT EXISTS post_skills (
    post_id  BIGINT NOT NULL REFERENCES posts (id)  ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skills (id),
    PRIMARY KEY (post_id, skill_id)
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'posts' AND column_name = 'skill_id') THEN
        INSERT INTO post_skills (post_id, skill_id)
            SELECT id, skill_id FROM posts WHERE skill_id IS NOT NULL
            ON CONFLICT DO NOTHING;
        ALTER TABLE posts DROP COLUMN skill_id;   -- also drops idx_posts_skill_created
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_post_skills_skill ON post_skills (skill_id, post_id);

-- 5. "A user's posts, newest first" with a (created_at, id) keyset cursor. Replaces the V3 index, which
--    lacked the id tie-breaker.
DROP INDEX IF EXISTS idx_posts_author_created;
CREATE INDEX IF NOT EXISTS idx_posts_author_created_id ON posts (author_id, created_at DESC, id DESC);

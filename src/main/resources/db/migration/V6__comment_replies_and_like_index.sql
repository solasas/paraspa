-- Engagement: one level of comment replies, and an index for "who liked this post".

-- 1. post_comments becomes comments (it now holds replies too)
DO $$
BEGIN
    IF to_regclass('post_comments') IS NOT NULL AND to_regclass('comments') IS NULL THEN
        ALTER TABLE post_comments RENAME TO comments;
    END IF;
END $$;
ALTER SEQUENCE IF EXISTS post_comments_id_seq RENAME TO comments_id_seq;
ALTER INDEX IF EXISTS post_comments_pkey RENAME TO comments_pkey;
DROP INDEX IF EXISTS idx_post_comments_post;   -- from V3, replaced by the partial indexes below

-- 2. Replies: parent_id points at a top-level comment of the SAME post.
--    The composite foreign key (parent_id, post_id) -> (id, post_id) makes "reply to a comment on another post"
--    impossible at the database level, and ON DELETE CASCADE removes the replies with their parent.
--    (The application also forbids replying to a reply; that rule needs the parent's own parent, so it lives in code.)
ALTER TABLE comments ADD COLUMN IF NOT EXISTS parent_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_comments_id_post') THEN
        ALTER TABLE comments ADD CONSTRAINT uk_comments_id_post UNIQUE (id, post_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_comments_parent_same_post') THEN
        ALTER TABLE comments
            ADD CONSTRAINT fk_comments_parent_same_post
            FOREIGN KEY (parent_id, post_id) REFERENCES comments (id, post_id) ON DELETE CASCADE;
    END IF;
END $$;

-- 3. Paging: top-level comments of a post in id order, and the replies of a comment in id order
CREATE INDEX IF NOT EXISTS idx_comments_post_top ON comments (post_id, id) WHERE parent_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_comments_parent   ON comments (parent_id, id) WHERE parent_id IS NOT NULL;

-- 4. "Who liked this post", newest first
CREATE INDEX IF NOT EXISTS idx_post_likes_post_created ON post_likes (post_id, created_at DESC, id DESC);

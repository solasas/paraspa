-- Follow system hardening: no self-follows, and indexes for the paginated follower/following lists.

-- A self-follow cannot be created through the API, but clear any stray rows so the constraint can be added.
DELETE FROM follows WHERE follower_id = following_id;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_follows_not_self') THEN
        ALTER TABLE follows
            ADD CONSTRAINT chk_follows_not_self CHECK (follower_id <> following_id);
    END IF;
END $$;

-- "Who does X follow?" and "who follows X?", newest first. Both lead with the id column that is
-- filtered on and end with the sort key, so the paged queries need no extra sort.
-- (The unique (follower_id, following_id) constraint already indexes follower_id, but not in
-- created_at order, and following_id had only a plain single-column index.)
CREATE INDEX IF NOT EXISTS idx_follows_follower_created  ON follows (follower_id,  created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_follows_following_created ON follows (following_id, created_at DESC, id DESC);

-- superseded by idx_follows_following_created (created in V3)
DROP INDEX IF EXISTS idx_follows_following;

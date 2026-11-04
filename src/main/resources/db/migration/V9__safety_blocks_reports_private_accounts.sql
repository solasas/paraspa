-- Safety: private accounts with follow requests, blocking, reporting.

-- 1. Private accounts
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_private BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. A follow is either a request waiting for the owner (PENDING) or an approved follow (ACCEPTED).
--    Every follow that exists today was a normal follow, so it stays ACCEPTED.
ALTER TABLE follows ADD COLUMN IF NOT EXISTS status VARCHAR(255) NOT NULL DEFAULT 'ACCEPTED';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'follows_status_check') THEN
        ALTER TABLE follows ADD CONSTRAINT follows_status_check CHECK (status IN ('PENDING', 'ACCEPTED'));
    END IF;
END $$;

-- the owner's list of requests waiting for an answer; pending rows are few, so a partial index keeps it tiny
CREATE INDEX IF NOT EXISTS idx_follows_pending ON follows (following_id, created_at DESC, id DESC) WHERE status = 'PENDING';

-- 3. Blocks: "blocker does not want to see or hear from blocked". The pair is treated as hidden in both directions.
CREATE TABLE IF NOT EXISTS blocks (
    id         BIGSERIAL PRIMARY KEY,
    blocker_id BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_blocks_blocker_blocked UNIQUE (blocker_id, blocked_id),
    CONSTRAINT chk_blocks_not_self CHECK (blocker_id <> blocked_id)
);
-- the unique constraint serves "whom did I block"; this one serves "who blocked me" (every listing checks both ways)
CREATE INDEX IF NOT EXISTS idx_blocks_blocked ON blocks (blocked_id, blocker_id);

-- 4. Reports. One report per reporter and target, ever (the unique constraint is what stops duplicates).
CREATE TABLE IF NOT EXISTS reports (
    id              BIGSERIAL PRIMARY KEY,
    reporter_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_type     VARCHAR(255) NOT NULL,
    target_id       BIGINT       NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    details         TEXT,
    status          VARCHAR(255) NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMP(6) NOT NULL,
    resolved_at     TIMESTAMP(6),
    resolved_by_id  BIGINT REFERENCES users (id) ON DELETE SET NULL,
    resolution_note TEXT,
    CONSTRAINT uk_reports_reporter_target UNIQUE (reporter_id, target_type, target_id),
    CONSTRAINT reports_target_type_check CHECK (target_type IN ('USER', 'POST', 'COMMENT')),
    CONSTRAINT reports_reason_check CHECK (reason IN
        ('SPAM', 'HARASSMENT', 'HATE_SPEECH', 'SEXUAL_CONTENT', 'VIOLENCE', 'MISINFORMATION', 'IMPERSONATION', 'OTHER')),
    CONSTRAINT reports_status_check CHECK (status IN ('OPEN', 'RESOLVED'))
);
-- the moderators' queue (open reports, oldest first) and "how many reports does this target have"
CREATE INDEX IF NOT EXISTS idx_reports_status_id ON reports (status, id);
CREATE INDEX IF NOT EXISTS idx_reports_target ON reports (target_type, target_id);

-- 5. Two new notification types for follow requests
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN
    ('FOLLOW', 'LIKE', 'COMMENT', 'REPLY', 'EXCHANGE_REQUEST', 'EXCHANGE_ACCEPTED', 'MESSAGE',
     'FOLLOW_REQUEST', 'FOLLOW_ACCEPTED'));

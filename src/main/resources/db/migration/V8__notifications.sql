-- Notifications: "someone did something that concerns you".

CREATE TABLE IF NOT EXISTS notifications (
    id           BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    actor_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         VARCHAR(255) NOT NULL,
    target_type  VARCHAR(255) NOT NULL,
    target_id    BIGINT       NOT NULL,
    read         BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Set only for notifications that must exist once ever (a like, a follow): "<actor>:<type>:<targetType>:<targetId>".
    -- NULL for the others. The unique constraint below is what collapses duplicates, even for simultaneous requests.
    dedupe_key   VARCHAR(255),
    created_at   TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_notifications_recipient_dedupe UNIQUE (recipient_id, dedupe_key),
    CONSTRAINT chk_notifications_not_self CHECK (recipient_id <> actor_id),
    CONSTRAINT notifications_type_check CHECK (type IN
        ('FOLLOW', 'LIKE', 'COMMENT', 'REPLY', 'EXCHANGE_REQUEST', 'EXCHANGE_ACCEPTED', 'MESSAGE')),
    CONSTRAINT notifications_target_type_check CHECK (target_type IN ('USER', 'POST', 'EXCHANGE', 'MESSAGE'))
);

-- The list: a user's notifications, newest first, paged with a (created_at, id) cursor
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created ON notifications (recipient_id, created_at DESC, id DESC);

-- The badge: only unread rows are indexed, so counting them stays cheap however many old ones pile up
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications (recipient_id) WHERE read = FALSE;

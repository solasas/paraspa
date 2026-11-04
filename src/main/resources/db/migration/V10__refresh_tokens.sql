-- Refresh tokens: opaque random values, only their SHA-256 hash is stored (a database leak does not leak sessions).
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    revoked_at  TIMESTAMP,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

-- "revoke everything of this user" (password change, reuse detection) and the cleanup of expired rows
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

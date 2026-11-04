-- Follow system and skill posts (likes, comments).
-- Idempotent: a database that Hibernate already created these tables in is left untouched.

CREATE TABLE IF NOT EXISTS follows (
    id           BIGSERIAL PRIMARY KEY,
    created_at   TIMESTAMP(6) NOT NULL,
    follower_id  BIGINT NOT NULL REFERENCES users (id),
    following_id BIGINT NOT NULL REFERENCES users (id),
    CONSTRAINT uk_follows_follower_following UNIQUE (follower_id, following_id)
);

CREATE TABLE IF NOT EXISTS posts (
    id         BIGSERIAL PRIMARY KEY,
    content    TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    author_id  BIGINT NOT NULL REFERENCES users (id),
    skill_id   BIGINT NOT NULL REFERENCES skills (id)
);

CREATE TABLE IF NOT EXISTS post_likes (
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL,
    post_id    BIGINT NOT NULL REFERENCES posts (id),
    user_id    BIGINT NOT NULL REFERENCES users (id),
    CONSTRAINT uk_post_likes_post_user UNIQUE (post_id, user_id)
);

CREATE TABLE IF NOT EXISTS post_comments (
    id         BIGSERIAL PRIMARY KEY,
    content    TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    author_id  BIGINT NOT NULL REFERENCES users (id),
    post_id    BIGINT NOT NULL REFERENCES posts (id)
);

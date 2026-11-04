-- Baseline: the schema as created by Hibernate before Flyway was introduced
-- (core exchange / session / rating / chat tables).
--
-- Existing databases are baselined at version 1 (spring.flyway.baseline-on-migrate=true),
-- so this script only ever runs on a brand-new, empty database.

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    bio         TEXT,
    city        VARCHAR(255),
    created_at  TIMESTAMP(6) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMP(6),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE skills (
    id          BIGSERIAL PRIMARY KEY,
    category    VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    description TEXT,
    name        VARCHAR(255) NOT NULL,
    CONSTRAINT uk_skills_name UNIQUE (name),
    CONSTRAINT skills_category_check CHECK (category IN
        ('MUSIC', 'TECH', 'COOKING', 'LANGUAGE', 'FITNESS', 'ART', 'OTHER'))
);

CREATE TABLE user_skills (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMP(6) NOT NULL,
    level       VARCHAR(255) NOT NULL,
    type        VARCHAR(255) NOT NULL,
    skill_id    BIGINT NOT NULL REFERENCES skills (id),
    user_id     BIGINT NOT NULL REFERENCES users (id),
    CONSTRAINT user_skills_level_check CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT user_skills_type_check CHECK (type IN ('TEACH', 'LEARN'))
);

CREATE TABLE availabilities (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMP(6) NOT NULL,
    day_of_week VARCHAR(255) NOT NULL,
    end_time    TIME(6) NOT NULL,
    start_time  TIME(6) NOT NULL,
    user_id     BIGINT NOT NULL REFERENCES users (id),
    CONSTRAINT availabilities_day_of_week_check CHECK (day_of_week IN
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'))
);

CREATE TABLE skill_exchanges (
    id               BIGSERIAL PRIMARY KEY,
    created_at       TIMESTAMP(6) NOT NULL,
    message          TEXT,
    status           VARCHAR(255) NOT NULL,
    updated_at       TIMESTAMP(6),
    offered_skill_id BIGINT NOT NULL REFERENCES skills (id),
    receiver_id      BIGINT NOT NULL REFERENCES users (id),
    requester_id     BIGINT NOT NULL REFERENCES users (id),
    wanted_skill_id  BIGINT NOT NULL REFERENCES skills (id),
    CONSTRAINT skill_exchanges_status_check CHECK (status IN
        ('PENDING', 'ACCEPTED', 'REJECTED', 'COMPLETED'))
);

CREATE TABLE sessions (
    id               BIGSERIAL PRIMARY KEY,
    created_at       TIMESTAMP(6) NOT NULL,
    date_time        TIMESTAMP(6) NOT NULL,
    duration_minutes INTEGER,
    location         VARCHAR(255),
    meet_link        VARCHAR(255),
    mode             VARCHAR(255) NOT NULL,
    notes            TEXT,
    status           VARCHAR(255) NOT NULL,
    updated_at       TIMESTAMP(6),
    exchange_id      BIGINT NOT NULL REFERENCES skill_exchanges (id),
    scheduled_by_id  BIGINT NOT NULL REFERENCES users (id),
    CONSTRAINT sessions_mode_check CHECK (mode IN ('ONLINE', 'OFFLINE')),
    CONSTRAINT sessions_status_check CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE reschedule_requests (
    id                 BIGSERIAL PRIMARY KEY,
    created_at         TIMESTAMP(6) NOT NULL,
    proposed_date_time TIMESTAMP(6) NOT NULL,
    reason             TEXT,
    status             VARCHAR(255) NOT NULL,
    updated_at         TIMESTAMP(6),
    requested_by_id    BIGINT NOT NULL REFERENCES users (id),
    session_id         BIGINT NOT NULL REFERENCES sessions (id),
    CONSTRAINT reschedule_requests_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

CREATE TABLE ratings (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMP(6) NOT NULL,
    review      TEXT,
    stars       INTEGER NOT NULL,
    rated_by_id BIGINT NOT NULL REFERENCES users (id),
    rated_to_id BIGINT NOT NULL REFERENCES users (id),
    session_id  BIGINT NOT NULL REFERENCES sessions (id)
);

CREATE TABLE messages (
    id          BIGSERIAL PRIMARY KEY,
    content     TEXT NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    read        BOOLEAN NOT NULL,
    receiver_id BIGINT NOT NULL REFERENCES users (id),
    sender_id   BIGINT NOT NULL REFERENCES users (id)
);

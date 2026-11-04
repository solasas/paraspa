# paraspa

[![CI](https://github.com/solasas/paraspa/actions/workflows/ci.yml/badge.svg)](https://github.com/solasas/paraspa/actions/workflows/ci.yml)

A community skill exchange platform: people list what they can teach and what they want to learn, get matched, swap skills, schedule sessions, chat, rate each other, follow people, and share skill posts.

This repository is the **backend** (REST API + WebSocket chat), built with Spring Boot 3.2 and PostgreSQL.

| Part | Repository |
|---|---|
| Backend (this repo) | [solasas/paraspa](https://github.com/solasas/paraspa) |
| Flutter mobile app | [solasas/paraspa-mobileapp](https://github.com/solasas/paraspa-mobileapp) |

To use the app, clone the mobile app and point it at this backend:

```bash
git clone https://github.com/solasas/paraspa-mobileapp.git
```

## Features

- **Accounts**: register and login with a 15-minute JWT access token plus a rotating refresh token (`/api/auth/refresh`, `/api/auth/logout`), edit profile (name, bio, city), change password
- **Skills**: shared skill catalog; each user lists skills to **teach** and **learn** with a level
- **Availability**: weekly time slots, visible on any user's profile
- **Matching & search**: find users who teach what you want to learn (mutual matches first); filter by skill, city, level, rating and availability
- **Exchanges**: send a swap request; the receiver accepts or rejects; either side completes it
- **Sessions**: schedule online or offline sessions, complete or cancel, propose a new time (the other person accepts or rejects)
- **Ratings**: rate your partner 1-5 with a review after a completed session; per-user rating summary
- **Chat**: real-time messaging over WebSocket (STOMP) with persisted history and unread counts
- **Public profiles & avatars**: `GET /api/users/{id}` (never exposes email); upload a JPG/PNG/WebP profile picture (max 2 MB), served at `/files/**`
- **Pagination**: `page`/`size` (default 20, max 100) on search, exchanges, message history, follower lists and the explore/feed lists; cursor pagination (`items` + `nextCursor`, keyed on `createdAt` + `id`) for a user's posts and for new endpoints
- **Follows**: follow and unfollow users (both idempotent; no self-follows), paginated follower and following lists, follow status, and follower/following counts on the public profile
- **Notifications**: follows, likes, comments, replies, exchange requests/acceptances and messages notify the person concerned (never yourself; repeated likes/follows collapse into one). A REST list with an unread badge and read/read-all, plus a real-time push to `/user/queue/notifications` over the existing WebSocket
- **Feeds**: a home feed (`/api/feed`: people you follow plus you, newest first), trending discovery (`/api/explore`: last 7 days, engagement with time decay, people you do not follow) and suggested people (`/api/explore/people`), all without N+1 queries (5 SQL statements per feed or explore request, 6 for suggestions, measured)
- **Safety**: block users (hidden both ways everywhere, follows removed, no follow/message/exchange), report users, posts and comments (one report per reporter and target), private accounts (a follow becomes a request the owner accepts or rejects; only accepted followers see the posts), and an admin role to review reports and delete any post
- **Posts & feed**: text and/or up to 4 images (JPG/PNG/WebP, 2 MB each) with up to 3 skill tags; browse all posts or a feed of people you follow (both filterable by skill tag), a user's posts with cursor pagination, idempotent likes with a liker list, comments with one level of replies (cursor-paginated, nested), and stored counters kept exact under concurrent requests

## Architecture

```
Client --HTTPS/JSON--> /api/**  --+
Client --WebSocket---> /ws      --+--> Security (JWT) -> Controllers -> Services -> Repositories -> PostgreSQL
```

| Layer | Package | Role |
|---|---|---|
| Security | `security`, `config` | JWT validation, CORS, BCrypt, WebSocket auth |
| Controllers | `controller` | REST and STOMP endpoints, request validation |
| Services | `service`, `service.impl` | Business rules and permission checks |
| Repositories | `repository` | Spring Data JPA queries |
| Model | `entity`, `enums`, `dto`, `util` | JPA entities, API objects, mapping |
| Storage | `storage`, `config` | `StorageService` interface with a local-disk implementation (swap in S3 later); serves `/files/**` |
| Migrations | `src/main/resources/db/migration` | Flyway SQL migrations own the schema; Hibernate only validates it |
| Errors | `exception` | One JSON error format via a global handler |

Package root: `com.sashank.paraspa`. The acting user always comes from the JWT, never from the request body.

```
User ---< UserSkill >--- Skill          User ---< Availability
User --< SkillExchange >-- Skill (offered, wanted)
SkillExchange ---< Session ---< RescheduleRequest
Session ---< Rating (ratedBy -> ratedTo)        User --< Message >-- User
User --< Notification >-- User (recipient, actor)
User --< Follow >-- User                        User ---< Post >--- PostMedia (max 4)
Post >---< Skill (max 3 tags, post_skills)     Post ---< PostLike >--- User
Post ---< Comment >--- User    Comment ---< Comment (replies, one level)
```

Flow: register, add skills, find a match, send an exchange, accept, schedule sessions, complete, rate.

### Notifications: domain events, after commit, off the request thread

Services do not know that notifications exist. When something happens they publish a plain domain event through Spring's `ApplicationEventPublisher`: `UserFollowedEvent`, `PostLikedEvent`, `CommentAddedEvent`, `ExchangeRequestedEvent`, `ExchangeAcceptedEvent`, `MessageSentEvent` (package `event`). `NotificationEventListener` turns each into a notification:

| Event | Notifies | Type | Points at |
|---|---|---|---|
| a follow is created | the person followed | `FOLLOW` | `USER` = the follower |
| a follow request on a private account | the account owner | `FOLLOW_REQUEST` | `USER` = the requester |
| a follow request is accepted | the requester | `FOLLOW_ACCEPTED` | `USER` = the account owner |
| a like is added | the post's author | `LIKE` | `POST` |
| a top-level comment | the post's author | `COMMENT` | `POST` |
| a reply | the author of the comment replied to | `REPLY` | `POST` |
| an exchange is requested | the receiver | `EXCHANGE_REQUEST` | `EXCHANGE` |
| an exchange is accepted | the requester | `EXCHANGE_ACCEPTED` | `EXCHANGE` |
| a message is sent | the receiver | `MESSAGE` | `MESSAGE` = the newest message |

Rules and how they are enforced:

- **Only when something really happened.** The services publish an event only for a new follow, a like that was actually inserted, and so on. A repeated `PUT /follow` or `PUT /like` publishes nothing.
- **After commit.** The listener is `@TransactionalEventListener(AFTER_COMMIT)`: a rolled-back action creates no notification, and a notification can never be seen before the action it describes. It also sets `fallbackExecution = true`, because some services (follow, message, exchange create/accept) are deliberately not transactional and an after-commit listener silently drops events published outside a transaction otherwise. Both behaviours are covered by `NotificationEventIntegrationTest`.
- **Never yourself.** If the actor is the recipient (liking or commenting on your own post, replying to yourself) nothing is created. This is checked in the service, and the table has a `CHECK (recipient_id <> actor_id)`.
- **Duplicates collapse.** A like or a follow can be repeated by toggling (like, unlike, like...). Those two types carry a `dedupe_key` (`actor:type:targetType:targetId`) under a unique constraint `(recipient_id, dedupe_key)`, so a user is told once even when requests are simultaneous. Comments and replies are always new. Messages collapse differently: while a person has an unread `MESSAGE` notification from someone, further messages from that person update it (it points at the newest message and moves back to the top) instead of adding a notification per message.
- **Off the request thread.** Handlers are `@Async` on a small dedicated executor (`AsyncConfig`: 2-4 threads, queue of 2000, overflow dropped with a log line). This is not optional: an after-commit handler on the request thread still holds that request's database connection, and storing the notification (a transaction of its own, `REQUIRES_NEW`) needs a second one. With as many simultaneous requests as the pool has connections (10 by default), every request holds one and waits for another, so every request times out after 30 s and every notification is lost. That happened in testing with 10 concurrent likes; `NotificationConnectionPoolTest` reproduces it with a 3-connection pool. After the fix 40 simultaneous likes, comments and follows each produced exactly 40 notifications with no pool errors. The price is that a notification appears a few milliseconds after the request returns, not inside it.
- **Failures stay contained.** A failure while creating or pushing a notification is logged and swallowed; it can never fail the request that caused it.
- **Real time.** After the notification is stored (committed), `NotificationPushService` sends it with `SimpMessagingTemplate.convertAndSendToUser(email, "/queue/notifications", payload)`, which the client sees on `/user/queue/notifications`. The user is addressed by email because that is the STOMP principal name (the chat does the same). `NotificationWebSocketEndToEndTest` opens real STOMP sessions with real JWTs against the running application and checks the whole path.

Measured on PostgreSQL with 300,000 notifications (20,000 for one user): the first page of a user's list takes 0.2 ms, the unread badge 3 ms (a partial index on unread rows only, so old read ones cost nothing), marking everything read 28 ms.

### Feed design: fan-out on read

There are two classic ways to build a "posts from the people I follow" feed:

| | Fan-out on **write** | Fan-out on **read** (what paraspa does) |
|---|---|---|
| When a post is created | copy a reference into every follower's inbox table (`feed_items`) | just insert the post |
| When the feed is requested | read your own inbox (very cheap) | query the posts of the people you follow, newest first |
| Cost grows with | followers of the author (one write each) | people *you* follow (one index probe each) |
| Follow / unfollow / delete post | must back-fill or purge inboxes | takes effect immediately, nothing to repair |
| Extra storage / background work | a big table plus an async fan-out job | none |

paraspa fans out **on read**. At this size it is both the simplest option and fast, because the work is bounded by a keyset-paginated query that stops after one page rather than scanning anyone's whole history:

- **One selection query** (`FeedQuery`): `author_id IN (follows UNION ALL me)` ordered by `(created_at DESC, id DESC)`, continued from the cursor with a row-value comparison `(created_at, id) < (:t, :id)`. The `(created_at DESC, id DESC)` index added in migration `V7` lets PostgreSQL read posts newest-first and stop as soon as it has a page, so a page costs about the same on page 1 and page 400. Two details were found by measuring, not guessing: the `author = me OR author IN (...)` form is a sequential scan, and the `created_at < t OR (created_at = t AND id < i)` form of the cursor cannot use the index as a range (up to 193 ms vs about 3 ms).
- **No N+1.** The selection returns only ids. The page's posts are then loaded in one statement with their authors (`JOIN FETCH`), the skill tags and the images of the whole page are loaded by one `IN (...)` query each (`@BatchSize` on `Post.skills` / `Post.media`), and "which of these did I like" is one more query. That is **5 statements per request whether the page holds 5 posts or 100**, from any number of different authors. `FeedServiceImplIntegrationTest` counts every JDBC statement and fails if that number changes.
- **Cursor, not page numbers.** `nextCursor` is the `(createdAt, id)` of the last post returned, so posts created or deleted while someone scrolls never cause repeats or gaps (ties on the same timestamp are broken by id).

Measured on a laptop with PostgreSQL, 300,000 posts, 3,000 users, one user following 300 people (about 95,000 posts in their scope), through the HTTP API (median of 9, JSON included):

| Request | Time | SQL statements |
|---|---|---|
| `GET /api/feed?size=20` | 16 ms | 5 |
| `GET /api/feed?size=100` | 43 ms | 5 |
| feed, page 400 (8,000 posts deep) | 31 ms | 5 |
| `GET /api/explore?size=20` | 62 ms | 5 |
| `GET /api/explore?size=20&skillId=7` | 35 ms | 5 |

**When to change it.** Read fan-out costs work proportional to how many people a user follows and how active they are. Move to fan-out on write (or a hybrid: write to inboxes for normal accounts, merge celebrities' posts at read time) when feed latency stays high for users who follow thousands of accounts, or when feed reads vastly outnumber writes. The change would be local: a `feed_items(user_id, post_id, created_at)` table filled by an async job, with `FeedQuery` reading it instead of `posts`. The API, cursor format and response do not change.

**Explore** (`GET /api/explore`) is computed per request, not stored: it ranks the posts of the last 7 days from people you do not follow (and not your own) by

```
score = (likeCount + 2 * commentCount) / (ageInHours + 2) ^ 1.5
```

so a comment counts as two likes and engagement fades with age (a 2-hour-old post with 3 likes outranks a 6-day-old post with 100). Posts without any engagement stay in the list at the bottom, newest first, so a young community still has something to show. The cost is proportional to the number of posts in the 7-day window, not to the page size: 72 ms for about 48,000 candidate posts. The arithmetic must stay in `DOUBLE PRECISION` (see `TrendingPostQuery`): the same query with untyped literals took 689 ms. Its cursor carries the instant the ranking was computed at (`asOf`), so every page of one scroll is ranked with the same clock; if counts change while scrolling, a post can occasionally move across a page boundary, so clients should skip ids they already have. If the window grows large, compute the scores on a schedule into a table or cache instead.

**Suggested people** (`GET /api/explore/people`) reuses `MatchService` (users whose skills complement yours, mutual matches first, then by user id) and removes everyone you already follow. `MatchService` used to run two queries for every user in the system (6,007 statements and about 1.3 s with 3,000 users); it now loads only the rows that can produce a match, one query per direction (people who teach something you want to learn, people who want to learn something you teach) with the user and skill fetched in the same statement. That is **5 statements however many users exist** (6 for this endpoint with the follow list): 35 ms for the same 3,000 users, and the same speed-up applies to `GET /api/matches` and `/api/matches/mutual`. `MatchServiceImplIntegrationTest` counts the statements and also checks the results against a brute-force version of the old algorithm on random data.

## API

Full request and response details: [docs/FRONTEND-HANDOFF.md](docs/FRONTEND-HANDOFF.md). All routes need `Authorization: Bearer <token>` except `/api/auth/**`.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh` (`{refreshToken}`), `POST /api/auth/logout` (`{refreshToken}`, 204) |
| Profile | `GET/PUT /api/profile/me`, `PUT /api/profile/me/avatar` (multipart `file`), `PUT /api/profile/me/password` |
| Public profile | `GET /api/users/{id}` |
| Files | `GET /files/**` (public) |
| My skills | `GET/POST /api/profile/skills`, `DELETE /api/profile/skills/{id}` |
| Availability | `GET/POST /api/profile/availability`, `DELETE /api/profile/availability/{id}`, `GET /api/users/{id}/availability` |
| Skills | `GET /api/skills[?category=]`, `GET /api/skills/categories`, `GET /api/skills/{id}`, `POST /api/skills` |
| Matches | `GET /api/matches`, `/api/matches/mutual`, `/api/matches/{userId}` |
| Search | `GET /api/users/search?skillName&city&level&minRating&availableOnly&page&size` |
| Exchanges | `POST /api/exchanges`, `GET /api/exchanges[?page&size]`, `GET .../sent`, `.../received` (same params), `GET .../{id}`, `PUT .../{id}/accept\|reject\|complete` |
| Sessions | `POST/GET /api/exchanges/{id}/sessions`, `GET /api/sessions/my-sessions`, `GET /api/sessions/{id}`, `PUT .../{id}/complete\|cancel` |
| Reschedule | `POST /api/sessions/{id}/reschedule`, `GET .../reschedule-requests`, `PUT .../reschedule-requests/{rid}/accept\|reject` |
| Ratings | `POST /api/sessions/{id}/ratings`, `GET /api/users/{id}/ratings`, `GET /api/users/{id}/rating-summary`, `GET /api/profile/me/ratings` |
| Messages | `POST /api/messages`, `GET /api/messages/conversations`, `GET .../conversations/{userId}[?page&size]`, `GET .../unread-count` |
| Follows | `PUT/DELETE /api/users/{id}/follow`, `GET /api/users/{id}/followers[?page&size]`, `GET /api/users/{id}/following[?page&size]`, `GET /api/users/{id}/follow-status` |
| Follow requests | `GET /api/follow-requests[?page&size]` (pending requests to my private account), `PUT /api/follow-requests/{id}/accept\|reject` |
| Blocks | `PUT/DELETE /api/users/{id}/block` (both idempotent, 204), `GET /api/blocks[?page&size]` (people I blocked) |
| Reports | `POST /api/reports` (`{targetType: USER\|POST\|COMMENT, targetId, reason, details?}`, 201; 409 if already reported) |
| Admin | `GET /api/admin/reports[?status=OPEN\|RESOLVED&cursor&size]`, `PUT /api/admin/reports/{id}/resolve` (`{note?}`); admins can also `DELETE` any post. All require the `ADMIN` role (403 otherwise) |
| Notifications | `GET /api/notifications[?cursor&size]`, `GET /api/notifications/unread-count`, `PUT /api/notifications/{id}/read`, `PUT /api/notifications/read-all`; WebSocket: subscribe to `/user/queue/notifications` |
| Feeds | `GET /api/feed[?cursor&size]` (home feed), `GET /api/explore[?skillId&cursor&size]` (trending), `GET /api/explore/people[?page&size]` (people to follow) |
| Posts | `POST /api/posts` (JSON `{content, skillIds}` or multipart `content`, `skillIds`, `images`), `GET /api/posts[?skillId&page&size]`, `GET /api/posts/feed[?skillId&page&size]`, `GET/DELETE /api/posts/{id}`, `GET /api/users/{id}/posts[?cursor&size]` (cursor-paginated) |
| Likes | `PUT/DELETE /api/posts/{id}/like` (both idempotent), `GET /api/posts/{id}/likes[?page&size]` |
| Comments | `POST /api/posts/{id}/comments` (`{content, parentId?}`, max 500 chars), `GET /api/posts/{id}/comments[?cursor&size]` (cursor-paginated, replies nested one level), `DELETE /api/comments/{id}` (comment author or post author) |

**Notifications (WebSocket):** with the same STOMP connection (same `Authorization: Bearer <token>` connect header), subscribe to `/user/queue/notifications`. Every new notification for that user arrives there as one JSON `NotificationResponse` (the same object the REST list returns); see `docs/FRONTEND-HANDOFF.md` for the payload. A user who is not connected simply gets it from the REST list later.

**Chat (WebSocket):** connect to `ws://localhost:8080/ws` with a STOMP client, sending the JWT as the `Authorization: Bearer <token>` connect header. Publish `{"receiverId": 2, "content": "Hi"}` to `/app/chat.send`; subscribe to `/user/queue/messages` for messages and `/user/queue/errors` for errors.

**Pagination:** `?page=0&size=20` (zero-based; default size 20, maximum 100; anything else is a 400). List endpoints still return a plain JSON array. Message history counts back from the newest message. Cursor-paginated lists (`GET /api/users/{id}/posts`, and new list endpoints) return `{ "items": [...], "nextCursor": "..." }` (`CursorPageResponse`); pass `nextCursor` back as `?cursor=`. `CursorUtil` builds the opaque cursor, a `(createdAt, id)` position for posts, so items that share a timestamp are never skipped or repeated.

## Safety: blocks, reports, private accounts, admin

- **Blocks are symmetric.** If A blocks B, neither sees the other's posts, comments, likes, profile, search results, matches, suggestions, explore or feed; a direct request for the other's profile or post is a plain 404 (the blocked person cannot tell a block from a missing user). Blocking deletes follows (and follow requests) in both directions and the notifications between the two; unblocking does not bring them back. Neither can follow, message or start/accept an exchange with the other (the blocker is told to unblock first, 400). The filter is part of each SQL query (`QueryFragments`, an anti-join on `blocks`), not applied afterwards, so pages are never short and counts are right.
- **Private accounts.** `isPrivate` on the profile (`PUT /api/profile/me`). Following a private account creates a `PENDING` follow; the owner accepts or rejects. Only `ACCEPTED` followers (and the owner) see the posts: a single post is a 404, `GET /api/users/{id}/posts` and the follower lists are a 403 `This account is private`, and the posts leave the general list, the feed and explore. Counts, suggestions and feeds use accepted follows only. Switching the account back to public accepts every waiting request.
- **Reports.** One report per reporter and target (`UNIQUE (reporter_id, target_type, target_id)`, so a repeat is a 409 even when simultaneous). You cannot report yourself or your own content; `OTHER` needs `details`; the target must exist and be visible to you (blocking someone and then reporting them is allowed).
- **Admin.** `users.role = 'ADMIN'` (there is deliberately no endpoint to grant it: `UPDATE users SET role = 'ADMIN' WHERE email = '...'`). `JwtAuthenticationFilter` reads the role from the database on every request and puts `ROLE_<role>` in the `Authentication`, so a demotion works immediately even with an old token, and a token for a deleted user is rejected. Method security is on; the admin controller uses `@PreAuthorize("hasRole('ADMIN')")` (and `/api/admin/**` is also restricted in the filter chain). Admins can delete any post (logged).
- Tests: `SafetyQueriesIntegrationTest` (the real queries), `SafetyEndToEndTest` (HTTP: a blocked user's content is missing from search, matches, suggestions, explore, feed, post lists, comments and likes in both directions; private accounts; reports; admin), plus unit tests for each service and the JWT filter.

## Security

- Stateless JWT access tokens (15 min) and BCrypt passwords. **Refresh tokens**: 256-bit random values, stored only as a SHA-256 hash, single use (every `POST /api/auth/refresh` revokes the one presented and issues a new pair, atomically, so two simultaneous refreshes cannot both win). Presenting an already-used token is treated as theft and revokes every session of that user. `POST /api/auth/logout` revokes one; changing the password revokes all of them; expired rows are purged daily. An access token already issued stays valid until it expires (at most 15 minutes) after a logout or password change
- **Rate limiting** (`RateLimitFilter`, in-memory token buckets): login 10/min and refresh 30/min per client IP, register 10/hour per IP, message sending 30/min per user (REST and the WebSocket chat share one budget). Over the limit: `429` with `Retry-After` and the JSON error body. Limits are per instance; with several instances put a shared limiter (Redis, or the proxy) in front. Behind a proxy set `FORWARD_HEADERS_STRATEGY=native`
- A request without a valid token is a `401` with the JSON error body and `WWW-Authenticate: Bearer` (`RestAuthenticationEntryPoint`); a wrong role is a `403` (`RestAccessDeniedHandler`)
- Secrets come only from the environment (see Setup); `.env` is git-ignored and the app will not start without them. Only `health` (no details) is exposed by Actuator and it is the only public actuator route
- Public: `/api/auth/**`, the `/ws` handshake, `GET /files/**`, `/actuator/health/**`, Swagger UI and `/v3/api-docs`
- Exchanges, sessions and reschedule requests are visible only to their participants (others get 404); only participants can rate a session
- Avatar uploads are checked by file content (JPG/PNG/WebP only, max 2 MB), not by the client's declared type, and stored under a random name. `GET /files/**` is public so image widgets can load it without a token
- Email addresses are never returned by the public profile, search, follow or post endpoints
- Database constraints back the business rules: unique `(user_id, skill_id, type)` on `user_skills`, `(session_id, rated_by_id)` on `ratings`, and `(follower_id, following_id)` plus a no-self-follow check on `follows`; a unique violation returns 409 (following twice is not an error, it is idempotent)
- Only a post's author can delete it; a comment can be deleted by its author or by the author of the post it is on (everyone else gets 404); deleting a post removes its image files and rows (`ON DELETE CASCADE` backs this up in the database)
- CORS origins come from `CORS_ALLOWED_ORIGINS` for REST; the WebSocket endpoint has its own list in `WebSocketConfig`
- Roles: `USER` and `ADMIN`; the role is read from the database on every request (see Safety). Anything under `/api/admin/**` is 403 for non-admins

## Setup

Requires Java 17 and Maven (or `./mvnw`) plus PostgreSQL, or just Docker.

**1. Configure secrets.** Nothing secret lives in the repository. Copy the template and fill in the two required values:

```bash
cp .env.example .env
openssl rand -base64 64     # paste the output as JWT_SECRET (at least 64 characters)
```

`.env` is git-ignored. `docker compose` reads it for variable substitution, and a local `./mvnw spring-boot:run` reads it too (`spring.config.import=optional:file:.env[.properties]`; real environment variables win). The app **refuses to start** without `DB_PASSWORD` and `JWT_SECRET`, or with a `JWT_SECRET` shorter than 64 characters.

**2a. Docker** (API on `:8080`, PostgreSQL on host port `5434`):

```bash
docker compose up --build
```

**2b. Manual:**

```bash
psql -U postgres -c "CREATE DATABASE paraspa_db;"
./mvnw spring-boot:run
```

Once it runs: Swagger UI at <http://localhost:8080/swagger-ui.html> (use **Authorize** and paste an access token), the OpenAPI document at `/v3/api-docs`, health at `/actuator/health` (`/liveness` and `/readiness` for orchestrators).

### Configuration (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `DB_PASSWORD` | **required** | PostgreSQL password |
| `JWT_SECRET` | **required** | HS512 signing key, 64+ characters |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` | `localhost` / `5432` / `paraspa_db` / `postgres` | database connection |
| `JWT_ACCESS_EXPIRATION_MS` | `900000` (15 min) | access token lifetime |
| `JWT_REFRESH_EXPIRATION_DAYS` | `30` | refresh token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:8080` | browser origins, comma separated |
| `FORWARD_HEADERS_STRATEGY` | `none` | set `native` behind a reverse proxy so rate limits see the real client IP |
| `RATE_LIMIT_ENABLED` | `true` | see Rate limiting |
| `RATE_LIMIT_{LOGIN,REGISTER,REFRESH,MESSAGE}_{CAPACITY,PERIOD}` | see below | per-rule limits |
| `SWAGGER_ENABLED` | `true` | `false` hides Swagger UI and `/v3/api-docs` |
| `LOG_LEVEL` / `SHOW_SQL` | `INFO` / `false` | logging of the app / of SQL |
| `PORT`, `STORAGE_ROOT` | `8080`, `uploads` | server port, upload folder |

**Database migrations (Flyway):** the schema is created and changed only by the SQL files in `src/main/resources/db/migration` (`V1` baseline, `V2` social tables, `V3` phase 0, `V4` follow constraints, `V5` post media/tags/counters, `V6` comment replies, `V7` feed indexes, `V8` notifications, `V9` blocks, reports, private accounts and follow requests, `V10` refresh tokens). Hibernate runs with `ddl-auto=validate`, so the app refuses to start if an entity and the database disagree. To change the schema, add a new `V<n>__description.sql`; never edit an applied one. Databases created before Flyway (by Hibernate) are baselined at version 1 automatically (`baseline-on-migrate=true`) and then receive V2 onwards; those scripts are written to be safe on a database that already has some of the objects. Worth knowing: `V3` removes any duplicate `user_skills` / `ratings` rows (keeping the oldest) before adding the unique constraints, `V6` renames `post_comments` to `comments` and adds `parent_id`, and `V5` turns each existing post's single skill into a skill tag, backfills the like/comment counters, and then drops the old `posts.skill_id` column.

**Engagement counters:** `posts.like_count` / `comment_count` are changed only by single atomic `UPDATE`s. A like is inserted with `INSERT ... ON CONFLICT DO NOTHING` and the counter moves only when that statement really inserted a row, so repeated or simultaneous likes cannot double count. Deleting a comment first takes a real `FOR UPDATE` lock on its row (a native query, see `CommentRepositoryContractTest` for why `@Lock` would not be enough on PostgreSQL), so replies added at the same moment cannot be missed by the count.

**Uploads:** avatars and post images (2 MB each; a request may carry up to 10 MB) are written to `STORAGE_ROOT` (git-ignored). To use S3 later, add another `StorageService` implementation and set `storage.type`.

**Tests and CI:** `./mvnw verify` runs everything (the GitHub Actions workflow in `.github/workflows/ci.yml` does exactly that on every push and pull request, with a PostgreSQL service container). Layers: Mockito unit tests; `@DataJpaTest` query tests on in-memory H2 (the feed and explore tests also count SQL statements); MockMvc security tests (`SecurityMockMvcTest`: no or bad token is 401, wrong role 403, public routes); end-to-end HTTP tests on H2; and **Testcontainers tests on real PostgreSQL** (`PostgresFeedQueryTest`, `PostgresLikeUniquenessTest`, `AuthFlowPostgresTest`, which also runs the real Flyway migrations). Those need a Docker daemon; without Docker they are skipped, or you can point them at an empty scratch database: `TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/paraspa_it TEST_DATABASE_USER=postgres TEST_DATABASE_PASSWORD=... ./mvnw test`. `ParaspaApplicationTests` boots the app against the database named by `DB_*` (in CI the service container; locally your `.env`). Test settings (a throw-away signing key, rate limiting off) are in `src/test/resources/config/application.properties`.

## Errors

All errors share one format. Validation and business-rule failures (including wrong login) return `400`, a missing/expired access token or an invalid refresh token `401`, a wrong role `403`, missing or foreign resources `404`, duplicates and uniqueness conflicts `409`, rate limit `429`.

```json
{ "timestamp": "2026-10-12T10:30:00", "status": 400, "error": "Bad Request", "message": "Details", "path": "/api/endpoint" }
```

## More docs

[docs/FRONTEND-HANDOFF.md](docs/FRONTEND-HANDOFF.md) (full API guide) · [docs/QUICKSTART.md](docs/QUICKSTART.md) · [docs/API-TESTING.md](docs/API-TESTING.md)

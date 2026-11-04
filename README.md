# paraspa

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

- **Accounts**: register and login with a JWT, edit profile (name, bio, city), change password
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
User --< Follow >-- User                        User ---< Post >--- PostMedia (max 4)
Post >---< Skill (max 3 tags, post_skills)     Post ---< PostLike >--- User
Post ---< Comment >--- User    Comment ---< Comment (replies, one level)
```

Flow: register, add skills, find a match, send an exchange, accept, schedule sessions, complete, rate.

## API

Full request and response details: [docs/FRONTEND-HANDOFF.md](docs/FRONTEND-HANDOFF.md). All routes need `Authorization: Bearer <token>` except `/api/auth/**`.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login` |
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
| Posts | `POST /api/posts` (JSON `{content, skillIds}` or multipart `content`, `skillIds`, `images`), `GET /api/posts[?skillId&page&size]`, `GET /api/posts/feed[?skillId&page&size]`, `GET/DELETE /api/posts/{id}`, `GET /api/users/{id}/posts[?cursor&size]` (cursor-paginated) |
| Likes | `PUT/DELETE /api/posts/{id}/like` (both idempotent), `GET /api/posts/{id}/likes[?page&size]` |
| Comments | `POST /api/posts/{id}/comments` (`{content, parentId?}`, max 500 chars), `GET /api/posts/{id}/comments[?cursor&size]` (cursor-paginated, replies nested one level), `DELETE /api/comments/{id}` (comment author or post author) |

**Chat (WebSocket):** connect to `ws://localhost:8080/ws` with a STOMP client, sending the JWT as the `Authorization: Bearer <token>` connect header. Publish `{"receiverId": 2, "content": "Hi"}` to `/app/chat.send`; subscribe to `/user/queue/messages` for messages and `/user/queue/errors` for errors.

**Pagination:** `?page=0&size=20` (zero-based; default size 20, maximum 100; anything else is a 400). List endpoints still return a plain JSON array. Message history counts back from the newest message. Cursor-paginated lists (`GET /api/users/{id}/posts`, and new list endpoints) return `{ "items": [...], "nextCursor": "..." }` (`CursorPageResponse`); pass `nextCursor` back as `?cursor=`. `CursorUtil` builds the opaque cursor, a `(createdAt, id)` position for posts, so items that share a timestamp are never skipped or repeated.

## Security

- Stateless JWT auth (24 h, configurable) and BCrypt passwords; no refresh endpoint
- Only `/api/auth/**` and the `/ws` handshake are public
- Exchanges, sessions and reschedule requests are visible only to their participants (others get 404); only participants can rate a session
- Avatar uploads are checked by file content (JPG/PNG/WebP only, max 2 MB), not by the client's declared type, and stored under a random name. `GET /files/**` is public so image widgets can load it without a token
- Email addresses are never returned by the public profile, search, follow or post endpoints
- Database constraints back the business rules: unique `(user_id, skill_id, type)` on `user_skills`, `(session_id, rated_by_id)` on `ratings`, and `(follower_id, following_id)` plus a no-self-follow check on `follows`; a unique violation returns 409 (following twice is not an error, it is idempotent)
- Only a post's author can delete it; a comment can be deleted by its author or by the author of the post it is on (everyone else gets 404); deleting a post removes its image files and rows (`ON DELETE CASCADE` backs this up in the database)
- CORS allows `http://localhost:3000` and `http://localhost:8080`; edit `SecurityConfig` and `WebSocketConfig` for other origins
- Change `jwt.secret` before deploying

## Setup

Requires Java 17 and Maven (or `./mvnw`) plus PostgreSQL, or just Docker.

**Docker** (API on `:8080`, PostgreSQL on host port `5434`):

```bash
docker compose up --build
```

**Manual:**

```bash
psql -U postgres -c "CREATE DATABASE paraspa_db;"
./mvnw spring-boot:run
```

Defaults live in `src/main/resources/application.properties` and can be overridden with environment variables: `DB_HOST`, `DB_PORT`, `DB_NAME` (`paraspa_db`), `DB_USER` (`postgres`), `DB_PASSWORD` (`password`), `PORT` (`8080`), `JWT_SECRET`. `STORAGE_ROOT` (default `uploads`, where avatars are written). 23 starter skills are seeded on first start.

**Database migrations (Flyway):** the schema is created and changed only by the SQL files in `src/main/resources/db/migration` (`V1` baseline, `V2` social tables, `V3` phase 0, `V4` follow constraints, `V5` post media/tags/counters, `V6` comment replies). Hibernate runs with `ddl-auto=validate`, so the app refuses to start if an entity and the database disagree. To change the schema, add a new `V<n>__description.sql`; never edit an applied one. Databases created before Flyway (by Hibernate) are baselined at version 1 automatically (`baseline-on-migrate=true`) and then receive V2 onwards; those scripts are written to be safe on a database that already has some of the objects. Worth knowing: `V3` removes any duplicate `user_skills` / `ratings` rows (keeping the oldest) before adding the unique constraints, `V6` renames `post_comments` to `comments` and adds `parent_id`, and `V5` turns each existing post's single skill into a skill tag, backfills the like/comment counters, and then drops the old `posts.skill_id` column.

**Engagement counters:** `posts.like_count` / `comment_count` are changed only by single atomic `UPDATE`s. A like is inserted with `INSERT ... ON CONFLICT DO NOTHING` and the counter moves only when that statement really inserted a row, so repeated or simultaneous likes cannot double count. Deleting a comment first takes a real `FOR UPDATE` lock on its row (a native query, see `CommentRepositoryContractTest` for why `@Lock` would not be enough on PostgreSQL), so replies added at the same moment cannot be missed by the count.

**Uploads:** avatars and post images (2 MB each; a request may carry up to 10 MB) are written to `STORAGE_ROOT` (git-ignored). To use S3 later, add another `StorageService` implementation and set `storage.type`.

**Tests:** `./mvnw test`. Unit tests need no database; `ParaspaApplicationTests` needs PostgreSQL with `paraspa_db`.

## Errors

All errors share one format. Validation and business-rule failures (including wrong login) return `400`, missing or foreign resources `404`, duplicates and uniqueness conflicts `409`.

```json
{ "timestamp": "2026-10-12T10:30:00", "status": 400, "error": "Bad Request", "message": "Details", "path": "/api/endpoint" }
```

## More docs

[docs/FRONTEND-HANDOFF.md](docs/FRONTEND-HANDOFF.md) (full API guide) · [docs/QUICKSTART.md](docs/QUICKSTART.md) · [docs/API-TESTING.md](docs/API-TESTING.md)

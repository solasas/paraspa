# paraspa

A community skill exchange platform: people list what they can teach and what they want to learn, get matched, swap skills, schedule sessions, chat, and rate each other.

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
| Errors | `exception` | One JSON error format via a global handler |

Package root: `com.sashank.paraspa`. The acting user always comes from the JWT, never from the request body.

```
User ---< UserSkill >--- Skill          User ---< Availability
User --< SkillExchange >-- Skill (offered, wanted)
SkillExchange ---< Session ---< RescheduleRequest
Session ---< Rating (ratedBy -> ratedTo)        User --< Message >-- User
```

Flow: register, add skills, find a match, send an exchange, accept, schedule sessions, complete, rate.

## API

Full request and response details: [docs/FRONTEND-HANDOFF.md](docs/FRONTEND-HANDOFF.md). All routes need `Authorization: Bearer <token>` except `/api/auth/**`.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login` |
| Profile | `GET/PUT /api/profile/me`, `PUT /api/profile/me/password` |
| My skills | `GET/POST /api/profile/skills`, `DELETE /api/profile/skills/{id}` |
| Availability | `GET/POST /api/profile/availability`, `DELETE /api/profile/availability/{id}`, `GET /api/users/{id}/availability` |
| Skills | `GET /api/skills[?category=]`, `GET /api/skills/categories`, `GET /api/skills/{id}`, `POST /api/skills` |
| Matches | `GET /api/matches`, `/api/matches/mutual`, `/api/matches/{userId}` |
| Search | `GET /api/users/search?skillName&city&level&minRating&availableOnly` |
| Exchanges | `POST/GET /api/exchanges`, `GET .../sent`, `.../received`, `.../{id}`, `PUT .../{id}/accept\|reject\|complete` |
| Sessions | `POST/GET /api/exchanges/{id}/sessions`, `GET /api/sessions/my-sessions`, `GET /api/sessions/{id}`, `PUT .../{id}/complete\|cancel` |
| Reschedule | `POST /api/sessions/{id}/reschedule`, `GET .../reschedule-requests`, `PUT .../reschedule-requests/{rid}/accept\|reject` |
| Ratings | `POST /api/sessions/{id}/ratings`, `GET /api/users/{id}/ratings`, `GET /api/users/{id}/rating-summary`, `GET /api/profile/me/ratings` |
| Messages | `POST /api/messages`, `GET /api/messages/conversations`, `GET .../conversations/{userId}`, `GET .../unread-count` |

**Chat (WebSocket):** connect to `ws://localhost:8080/ws` with a STOMP client, sending the JWT as the `Authorization: Bearer <token>` connect header. Publish `{"receiverId": 2, "content": "Hi"}` to `/app/chat.send`; subscribe to `/user/queue/messages` for messages and `/user/queue/errors` for errors.

## Security

- Stateless JWT auth (24 h, configurable) and BCrypt passwords; no refresh endpoint
- Only `/api/auth/**` and the `/ws` handshake are public
- Exchanges, sessions and reschedule requests are visible only to their participants (others get 404)
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

Defaults live in `src/main/resources/application.properties` and can be overridden with environment variables: `DB_HOST`, `DB_PORT`, `DB_NAME` (`paraspa_db`), `DB_USER` (`postgres`), `DB_PASSWORD` (`password`), `PORT` (`8080`), `JWT_SECRET`. Tables are created automatically on startup, and 23 starter skills are seeded.

**Tests:** `./mvnw test`. Unit tests need no database; `ParaspaApplicationTests` needs PostgreSQL with `paraspa_db`.

## Errors

All errors share one format. Validation and business-rule failures (including wrong login) return `400`, missing or foreign resources `404`, duplicates `409`.

```json
{ "timestamp": "2026-10-12T10:30:00", "status": 400, "error": "Bad Request", "message": "Details", "path": "/api/endpoint" }
```

## More docs

[docs/FRONTEND-HANDOFF.md](docs/FRONTEND-HANDOFF.md) (full API guide) · [docs/QUICKSTART.md](docs/QUICKSTART.md) · [docs/API-TESTING.md](docs/API-TESTING.md)

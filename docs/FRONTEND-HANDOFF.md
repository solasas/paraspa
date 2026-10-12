# paraspa — Frontend Handoff

Everything the frontend needs: features, REST endpoints, WebSocket chat, enums, payloads, and gotchas.
Generated from the code on `main` (commit `3e0afab`). Source of truth: `src/main/java/com/sashank/paraspa/controller/`.

---

## 1. What the app does

paraspa lets people trade skills: "I teach guitar, I want to learn Python."

| # | Feature | Summary |
|---|---------|---------|
| 1 | **Auth** | Register / login with email + password, get a JWT. |
| 2 | **Profile** | Name, bio, city. Change password. |
| 3 | **My skills** | List skills I can **TEACH** and skills I want to **LEARN**, each with a level. |
| 4 | **Skill catalog** | Browse all skills, filter by category, add a new skill to the catalog. |
| 5 | **Availability** | Weekly time slots (day + start/end time). Viewable for any user. |
| 6 | **Matching** | Find users who teach what I want to learn and/or want to learn what I teach. Mutual matches ranked first. |
| 7 | **User search** | Filter users by skill name, city, level, min rating, "has availability". |
| 8 | **Exchanges** | Send a swap request (my skill for theirs). Receiver accepts/rejects. Either side marks complete. |
| 9 | **Sessions** | Once an exchange is accepted, schedule one or more sessions (online/offline). Complete or cancel. |
| 10 | **Reschedule** | Propose a new time for a session; the *other* person accepts or rejects. |
| 11 | **Ratings** | After a session is completed, each participant rates the other 1–5 with optional review. Rating summary + star breakdown per user. |
| 12 | **Chat** | 1-to-1 messaging: REST for history/conversation list/unread count, STOMP WebSocket for live delivery. |

---

## 2. Connection basics

| Item | Value |
|------|-------|
| Base URL (dev) | `http://localhost:8080` |
| All REST routes | prefixed `/api` |
| Auth | `Authorization: Bearer <jwt>` on every request except `/api/auth/**` |
| Token lifetime | 24 h (`jwt.expiration=86400000`). **No refresh endpoint** — on expiry the user must log in again. |
| Content type | `application/json` |
| CORS allowed origins | `http://localhost:3000`, `http://localhost:8080` only. Other dev ports/hosts will be blocked until `SecurityConfig` is changed. |
| CORS methods | GET, POST, PUT, DELETE, OPTIONS (no PATCH) |
| Logout | Client-side only: discard the token. No server endpoint. |

### Date / time formats
| Java type | JSON format | Example |
|-----------|-------------|---------|
| `LocalDateTime` | ISO-8601, **no timezone** | `"2026-10-15T14:30:00"` |
| `LocalTime` | `HH:mm` or `HH:mm:ss` | `"18:00:00"` |
| `DayOfWeek` | uppercase English name | `"MONDAY"` … `"SUNDAY"` |

Datetimes carry no timezone. Decide on one convention in the client (e.g. display in the user's local zone, send local wall-clock time) and stick to it.

### Error shape (all REST errors)
```json
{
  "timestamp": "2026-10-10T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Exchange is not in pending status",
  "path": "/api/exchanges/5/accept"
}
```
Validation errors: `status 400`, `error: "Validation Error"`, `message` = `"field: msg; field2: msg2; "`.

| Status | When |
|--------|------|
| 400 | Validation failure, **and** all business-rule violations (wrong user, wrong state), **and wrong login credentials** |
| 404 | Resource not found, **or** you aren't a participant of the exchange/session you asked for (the server doesn't reveal that it exists) |
| 409 | Email already registered |
| 401 | Declared in handler but business code mostly uses 400 — don't rely on it |
| 403 / 401 | Missing/invalid/expired JWT on a protected route. No custom entry point is configured, so Spring's default applies (expect **403 with an empty body**; not verified by running the server). Treat 401 **and** 403 as "log in again". |
| 500 | Unhandled |

---

## 3. Enums

| Enum | Values |
|------|--------|
| `SkillCategory` | `MUSIC`, `TECH`, `COOKING`, `LANGUAGE`, `FITNESS`, `ART`, `OTHER` |
| `SkillLevel` | `BEGINNER`, `INTERMEDIATE`, `ADVANCED` |
| `SkillType` | `TEACH`, `LEARN` |
| `ExchangeStatus` | `PENDING`, `ACCEPTED`, `REJECTED`, `COMPLETED` |
| `SessionStatus` | `SCHEDULED`, `COMPLETED`, `CANCELLED` |
| `SessionMode` | `ONLINE`, `OFFLINE` |
| `RescheduleStatus` | `PENDING`, `ACCEPTED`, `REJECTED` |
| `DayOfWeek` | `MONDAY` … `SUNDAY` |

---

## 4. REST endpoints

Legend: 🔓 public · 🔒 needs JWT.

### 4.1 Auth

| Method | Path | Auth | Body | Success |
|--------|------|------|------|---------|
| POST | `/api/auth/register` | 🔓 | `{ name, email, password }` all required, email must be valid | **201** `AuthResponse` |
| POST | `/api/auth/login` | 🔓 | `{ email, password }` | **200** `AuthResponse` |

```json
// AuthResponse
{ "token": "eyJ...", "type": "Bearer", "id": 1, "name": "Asha", "email": "asha@x.com" }
```
- Duplicate email → **409** `Email already registered`.
- Wrong email *or* password → **400** `Invalid email or password` (same message for both).
- Password must be **≥ 6 characters** on both register and change-password (400 `Validation Error` otherwise).
- Register response has no bio/city; call `GET /api/profile/me` afterwards.

### 4.2 Profile (current user)

| Method | Path | Body | Success |
|--------|------|------|---------|
| GET | `/api/profile/me` | – | 200 `ProfileResponse` |
| PUT | `/api/profile/me` | `{ name?, bio?, city? }` (all optional) | 200 `ProfileResponse` |
| PUT | `/api/profile/me/password` | `{ currentPassword, newPassword }` (new ≥ 6 chars) | **204** no body |

```json
// ProfileResponse
{
  "id": 1, "name": "Asha", "email": "asha@x.com", "bio": "…", "city": "Pune",
  "teachSkills": [ /* UserSkillResponse */ ],
  "learnSkills": [ /* UserSkillResponse */ ],
  "averageRating": 4.5, "totalRatings": 12
}
```

### 4.3 My skills

| Method | Path | Body | Success |
|--------|------|------|---------|
| GET | `/api/profile/skills` | – | 200 `UserSkillResponse[]` (both TEACH and LEARN) |
| POST | `/api/profile/skills` | `{ skillId, type: "TEACH"\|"LEARN", level }` all required | **201** `UserSkillResponse` |
| DELETE | `/api/profile/skills/{userSkillId}` | – | **204** |

```json
// UserSkillResponse
{ "id": 7, "skill": { "id": 3, "name": "Guitar", "category": "MUSIC", "description": "…" },
  "type": "TEACH", "level": "INTERMEDIATE" }
```
- `{userSkillId}` is the **UserSkill row id** (`UserSkillResponse.id`), *not* the skill id.
- Deleting someone else's row returns 404. Adding a skill you already have with the same `type` returns **409** (`Skill already added to your teach list`). The same skill may appear once under TEACH and once under LEARN.

### 4.4 Skill catalog

| Method | Path | Notes | Success |
|--------|------|-------|---------|
| GET | `/api/skills` | optional `?category=TECH` | 200 `SkillResponse[]` |
| GET | `/api/skills/categories` | list of category enum values (for dropdowns) | 200 `string[]` |
| GET | `/api/skills/{id}` | | 200 `SkillResponse` |
| POST | `/api/skills` | `{ name, category, description? }` | **201** `SkillResponse` |

`SkillResponse` = `{ id, name, category, description }`.
Any logged-in user can create catalog skills (no admin check). Consider a "search before create" UX to avoid duplicates.

### 4.5 Availability

| Method | Path | Body | Success |
|--------|------|------|---------|
| GET | `/api/profile/availability` | – | 200 `AvailabilityResponse[]` (mine) |
| POST | `/api/profile/availability` | `{ dayOfWeek, startTime, endTime }` all required; start must be before end | **201** `AvailabilityResponse` |
| DELETE | `/api/profile/availability/{availabilityId}` | – | **204** |
| GET | `/api/users/{userId}/availability` | any user's slots | 200 `AvailabilityResponse[]` |

`AvailabilityResponse` = `{ id, dayOfWeek, startTime, endTime }`, sorted by day then start time.
Overlapping slots are not rejected.

### 4.6 Matching

| Method | Path | Success |
|--------|------|---------|
| GET | `/api/matches` | 200 `MatchResponse[]` — users with any overlap, mutual first |
| GET | `/api/matches/mutual` | 200 `MatchResponse[]` — only `isMutual: true` |
| GET | `/api/matches/{userId}` | 200 `MatchResponse` — compatibility with one specific user (may have both lists empty) |

```json
// MatchResponse
{
  "user": { /* UserResponse */ },
  "theyCanTeachMe": [ /* SkillResponse — they TEACH what I LEARN */ ],
  "iCanTeachThem":  [ /* SkillResponse — I TEACH what they LEARN */ ],
  "isMutual": true
}
```
Both lists non-empty ⇒ mutual. The user must have added TEACH/LEARN skills first or this returns `[]`.
Not paginated; the server scans all users.

### 4.7 User search

`GET /api/users/search` — all params optional, combine freely. Current user is excluded.

| Param | Type | Behavior |
|-------|------|----------|
| `skillName` | string | Case-insensitive *contains* match on skill name; returns users who **TEACH** it |
| `level` | `SkillLevel` | Filters the TEACH skill level (with or without `skillName`) |
| `city` | string | Case-insensitive *contains* on user's city; users without a city are excluded |
| `minRating` | number | Users with average ≥ value. Unrated users count as 0 |
| `availableOnly` | boolean | Only users with at least one availability slot |

Returns `UserResponse[]`:
```json
{ "id": 4, "name": "Ravi", "email": "ravi@x.com", "bio": "…", "city": "Pune",
  "averageRating": 4.2, "totalRatings": 5 }
```
No params returns every other user. Not paginated.

> `email` is **`null`** on users returned by search and match results (privacy). It is still present on users inside exchanges, sessions, ratings and messages, where both parties are already connected.

### 4.8 Exchanges (swap requests)

| Method | Path | Body | Success |
|--------|------|------|---------|
| POST | `/api/exchanges` | `{ receiverId, offeredSkillId, wantedSkillId, message? }` | **201** `ExchangeResponse` |
| GET | `/api/exchanges` | all mine (sent + received) | 200 `ExchangeResponse[]` |
| GET | `/api/exchanges/sent` | | 200 |
| GET | `/api/exchanges/received` | | 200 |
| GET | `/api/exchanges/{id}` | | 200 `ExchangeResponse` |
| PUT | `/api/exchanges/{id}/accept` | – | 200 — **receiver only**, must be `PENDING` |
| PUT | `/api/exchanges/{id}/reject` | – | 200 — **receiver only**, must be `PENDING` |
| PUT | `/api/exchanges/{id}/complete` | – | 200 — either participant |

```json
// ExchangeResponse
{
  "id": 9, "requester": { /* UserResponse */ }, "receiver": { /* UserResponse */ },
  "offeredSkill": { /* SkillResponse */ }, "wantedSkill": { /* SkillResponse */ },
  "status": "PENDING", "message": "Let's swap!",
  "createdAt": "…", "updatedAt": "…"
}
```
`GET /exchanges/{id}` and `GET /exchanges/{id}/sessions` return **404** if you are not the requester or receiver.

Create rules (each failure is a 400):
- cannot send to yourself
- *you* must have `offeredSkillId` listed as **TEACH**
- *receiver* must have `wantedSkillId` listed as **TEACH**

State flow: `PENDING → ACCEPTED | REJECTED`, `ACCEPTED → COMPLETED`. `complete` on any status other than `ACCEPTED` returns 400 `Only accepted exchanges can be completed`.

### 4.9 Sessions

| Method | Path | Body | Success |
|--------|------|------|---------|
| POST | `/api/exchanges/{exchangeId}/sessions` | `{ dateTime, durationMinutes, mode, meetLink?, location?, notes? }` (first three required) | **201** `SessionResponse` |
| GET | `/api/exchanges/{exchangeId}/sessions` | | 200 `SessionResponse[]` |
| GET | `/api/sessions/my-sessions` | upcoming sessions for me | 200 `SessionResponse[]` |
| GET | `/api/sessions/{id}` | | 200 `SessionResponse` |
| PUT | `/api/sessions/{id}/complete` | – | 200 — either participant |
| PUT | `/api/sessions/{id}/cancel` | – | 200 — **only the person who scheduled it** |

```json
// SessionResponse
{
  "id": 21, "exchange": { /* ExchangeResponse */ }, "scheduledBy": { /* UserResponse */ },
  "dateTime": "2026-10-15T14:30:00", "durationMinutes": 60,
  "mode": "ONLINE", "meetLink": "https://…", "location": null, "notes": "…",
  "status": "SCHEDULED", "createdAt": "…"
}
```
- Create requires the exchange to be `ACCEPTED` and the caller to be a participant.
- `GET /sessions/{id}` returns **404** if you are not a participant of the session's exchange.
- `complete` and `cancel` require status `SCHEDULED` (400 otherwise): a completed or cancelled session can't be changed again.
- `meetLink` / `location` are **not** validated against `mode`. Frontend should show `meetLink` for ONLINE and `location` for OFFLINE.

### 4.10 Reschedule

| Method | Path | Body | Success |
|--------|------|------|---------|
| POST | `/api/sessions/{id}/reschedule` | `{ proposedDateTime, reason? }` | **201** `RescheduleRequestResponse` |
| GET | `/api/sessions/{id}/reschedule-requests` | | 200 `RescheduleRequestResponse[]` |
| PUT | `/api/sessions/{id}/reschedule-requests/{requestId}/accept` | – | 200 **`SessionResponse`** (with the new `dateTime`) |
| PUT | `/api/sessions/{id}/reschedule-requests/{requestId}/reject` | – | 200 `RescheduleRequestResponse` |

```json
// RescheduleRequestResponse
{ "id": 3, "sessionId": 21, "requestedBy": { /* UserResponse */ },
  "proposedDateTime": "2026-10-17T10:00:00", "reason": "…",
  "status": "PENDING", "createdAt": "…" }
```
`GET …/reschedule-requests` returns **404** if you are not a participant.

Rules (all 400):
- caller must be a participant; session must be `SCHEDULED`
- only **one `PENDING` request** per session at a time
- you **cannot accept/reject your own** proposal — only the other participant can
- request must still be `PENDING`

UI: show Accept/Reject only when `status === "PENDING"` and `requestedBy.id !== me.id`; show "waiting for response" otherwise.

### 4.11 Ratings

| Method | Path | Body | Success |
|--------|------|------|---------|
| POST | `/api/sessions/{sessionId}/ratings` | `{ stars: 1–5, review? }` | **201** `RatingResponse` |
| GET | `/api/users/{userId}/ratings` | | 200 `RatingResponse[]` |
| GET | `/api/users/{userId}/rating-summary` | | 200 `RatingSummaryResponse` |
| GET | `/api/profile/me/ratings` | ratings I received | 200 `RatingResponse[]` |

```json
// RatingResponse
{ "id": 1, "session": { /* SessionResponse */ }, "ratedBy": { /* UserResponse */ },
  "ratedTo": { /* UserResponse */ }, "stars": 5, "review": "Great teacher", "createdAt": "…" }

// RatingSummaryResponse
{ "averageStars": 4.5, "totalRatings": 10,
  "breakdown": { "oneStar": 0, "twoStar": 1, "threeStar": 1, "fourStar": 3, "fiveStar": 5 } }
```
- Session must be `COMPLETED`. `ratedTo` is automatically the *other* participant.
- One rating per user per session → second attempt is a 400 `You have already rated this session`.
- The server does not check that the caller is a participant of the session (it picks "the other one" by elimination). Hide the rate button unless the user is a participant.

### 4.12 Messages (REST)

| Method | Path | Body | Success |
|--------|------|------|---------|
| POST | `/api/messages` | `{ receiverId, content (≤ 2000 chars) }` | **201** `MessageResponse` |
| GET | `/api/messages/conversations` | inbox list | 200 `ConversationResponse[]` |
| GET | `/api/messages/conversations/{otherUserId}` | full history, oldest→newest (as returned by repo) | 200 `MessageResponse[]` |
| GET | `/api/messages/unread-count` | for a badge | 200 bare number, e.g. `3` |

```json
// MessageResponse
{ "id": 100, "sender": { /* UserResponse */ }, "receiver": { /* UserResponse */ },
  "content": "Hi!", "read": false, "createdAt": "…" }

// ConversationResponse
{ "otherUser": { /* UserResponse */ }, "lastMessage": { /* MessageResponse */ }, "unreadCount": 2 }
```
- **Side effect:** `GET /conversations/{otherUserId}` marks that user's messages to me as read. Don't call it just to peek.
- Can't message yourself (400).
- There is no "mark as read" endpoint, no delete, no typing indicator, no pagination.

---

## 5. Real-time chat (WebSocket / STOMP)

Use REST for history, WebSocket for live updates and sending.

| Item | Value |
|------|-------|
| Endpoint | `ws://localhost:8080/ws` — **plain WebSocket, SockJS is not enabled** |
| Protocol | STOMP |
| Allowed origins | `http://localhost:3000`, `http://localhost:8080` |
| Auth | STOMP `CONNECT` frame header `Authorization: Bearer <jwt>`. Missing/invalid → connection rejected |
| Send to | `/app/chat.send` with body `{ "receiverId": 4, "content": "hello" }` |
| Subscribe (messages) | `/user/queue/messages` → receives `MessageResponse` |
| Subscribe (errors) | `/user/queue/errors` → receives a plain error **string** |

Behavior:
- A sent message is delivered to **both** the receiver and the sender on `/user/queue/messages`. So the sender's UI should append from the subscription rather than optimistically adding it, or de-dupe by `id`.
- Validation/business errors (self-message, unknown receiver, empty content) come back on `/user/queue/errors`.
- The `Authorization` header is only checked at `CONNECT`. After token expiry the open socket keeps working until it drops; on reconnect you need a fresh token.

Minimal `@stomp/stompjs` example:
```js
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: { Authorization: `Bearer ${token}` },
  onConnect: () => {
    client.subscribe('/user/queue/messages', f => onMessage(JSON.parse(f.body)));
    client.subscribe('/user/queue/errors',   f => onError(f.body));
  },
});
client.activate();

// send
client.publish({
  destination: '/app/chat.send',
  body: JSON.stringify({ receiverId: 4, content: 'hello' }),
});
```
Keep unread badges in sync: increment on incoming messages where `sender.id !== me.id` and the thread isn't open; refetch `GET /api/messages/unread-count` on reconnect.

---

## 6. Suggested screens → endpoints

| Screen | Calls |
|--------|-------|
| Sign up / Log in | `POST /auth/register`, `POST /auth/login` |
| Onboarding (pick skills) | `GET /skills`, `GET /skills/categories`, `POST /profile/skills`, `POST /profile/availability` |
| My profile / edit | `GET/PUT /profile/me`, `PUT /profile/me/password`, `GET /profile/me/ratings` |
| My skills | `GET /profile/skills`, `POST /profile/skills`, `DELETE /profile/skills/{id}` |
| Availability editor | `GET/POST /profile/availability`, `DELETE /profile/availability/{id}` |
| Discover / Matches | `GET /matches`, `GET /matches/mutual` |
| Search | `GET /users/search?...` |
| Other user's page | data from match/search `UserResponse` + `GET /users/{id}/availability`, `/users/{id}/ratings`, `/users/{id}/rating-summary`, `GET /matches/{id}` |
| Send swap request | `POST /exchanges` |
| Requests inbox (sent/received) | `GET /exchanges/sent`, `/exchanges/received`, `PUT /exchanges/{id}/accept\|reject` |
| Exchange detail | `GET /exchanges/{id}`, `GET /exchanges/{id}/sessions`, `POST /exchanges/{id}/sessions`, `PUT /exchanges/{id}/complete` |
| Upcoming sessions | `GET /sessions/my-sessions` |
| Session detail | `GET /sessions/{id}`, `PUT …/complete`, `PUT …/cancel`, reschedule endpoints, `POST /sessions/{id}/ratings` |
| Inbox | `GET /messages/conversations`, `GET /messages/unread-count`, WebSocket |
| Chat thread | `GET /messages/conversations/{id}`, WebSocket `/app/chat.send` |

---

## 7. Gotchas & gaps the frontend should know about

**Missing endpoints (you may hit these while building screens)**
- **No "get another user's profile" endpoint.** `/api/profile/me` is self-only. For a public profile page, assemble from `UserResponse` (match/search) + availability + ratings. Another user's full teach/learn skill list is not exposed, only the overlap via `/matches/{userId}`.
- No token refresh, no logout, no password reset / email verification.
- No notifications endpoint; poll or use the chat socket for message badges.
- No pagination or sorting params anywhere; all list endpoints return everything.
- No delete/edit for exchanges, sessions, messages, ratings.

**Behaviors that surprise**
- Wrong credentials and every business-rule failure are **400**, not 401/403. Show `message` from the body.
- Reading an exchange/session you don't belong to is **404**, not 403.
- Still unchecked on the server: `POST /sessions/{id}/ratings` doesn't verify the caller took part in the session. Hide the rate button unless `me` is the exchange's requester or receiver.
- `isMutual` / `iCanTeachThem` are explicit `@JsonProperty` names; use exactly `isMutual` and `iCanTeachThem`.
- `GET /messages/unread-count` returns a raw number, not an object.
- DELETEs and password change return **204 with no body**; don't call `.json()` on them.
- CORS is locked to ports 3000/8080. If the app runs on Vite's default 5173, add it to `SecurityConfig.corsConfigurationSource` and `WebSocketConfig.registerStompEndpoints`.

---

## 8. Quick smoke test (curl)

```bash
BASE=http://localhost:8080
# register
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Asha","email":"asha@example.com","password":"secret123"}'
# login -> copy token
TOKEN=...   # paste token
curl -s $BASE/api/profile/me -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/api/users/search?skillName=gui&city=pune" -H "Authorization: Bearer $TOKEN"
```

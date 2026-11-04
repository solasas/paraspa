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
| 13 | **Follows** | Follow / unfollow users; follower and following lists; counts and "do I follow them" flag. |
| 14 | **Skill posts & feed** | Post text about a skill. Explore all posts or a feed of people I follow (+ my own), optionally filtered by skill. Like and comment. |
| 15 | **Public profiles & avatars** | `GET /users/{id}` returns another user's public profile (never the email). Users upload a profile picture (JPG/PNG/WebP, ≤ 2 MB). |

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

### Pagination

Most list endpoints take `?page=0&size=20` (`page` is zero-based, **default size 20, max 100**). Rules are the same everywhere:

| Case | Result |
|------|--------|
| omitted | `page=0`, `size=20` |
| `page < 0`, `size < 1` or `size > 100` | **400** `page must be >= 0 and size must be between 1 and 100` |
| `page`/`size` not a number | **400** `Invalid value for parameter 'size'` |
| page past the end | `200 []` |

The response is still a plain JSON array. There is no total count: keep requesting `page + 1` until a page comes back with **fewer than `size`** items (or empty).

Paginated today: `GET /users/search`, `GET /exchanges`, `/exchanges/sent`, `/exchanges/received`, `GET /messages/conversations/{userId}`, `GET /posts`, `/posts/feed`, `/users/{id}/posts`.
**Not** paginated yet: matches, skills, followers/following, comments, ratings, `GET /messages/conversations` (inbox list), sessions.

**Cursor pagination (for future list endpoints).** New endpoints will return this envelope instead of an array:
```json
{ "items": [ /* … */ ], "nextCursor": "MTIz" }
```
Request the next page with `?cursor=<nextCursor>&size=20`. `nextCursor` is `null` on the last page. Treat the cursor as an opaque string; a malformed one is a 400 `Invalid cursor`. No current endpoint uses it yet.

### Files (avatars)

Uploaded files are served at `{baseUrl}/files/...`. Fields such as `avatarUrl` hold a **root-relative path** (`/files/avatars/1-3f2a….png`): prefix it with the base URL. `GET /files/**` needs **no** `Authorization` header, so a plain `Image.network(url)` works. File names contain a random id and never change, so they can be cached aggressively; a new upload gets a new URL (remember to refresh `avatarUrl` after uploading).

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
| 409 | Email already registered; already following / already liked / skill already added; or any database uniqueness conflict (e.g. a double-tapped request) |
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
| PUT | `/api/profile/me/avatar` | `multipart/form-data`, one part named **`file`** | 200 `ProfileResponse` (with the new `avatarUrl`) |
| PUT | `/api/profile/me/password` | `{ currentPassword, newPassword }` (new ≥ 6 chars) | **204** no body |

```json
// ProfileResponse
{
  "id": 1, "name": "Asha", "email": "asha@x.com", "bio": "…", "city": "Pune",
  "avatarUrl": "/files/avatars/1-3f2a….png",
  "teachSkills": [ /* UserSkillResponse */ ],
  "learnSkills": [ /* UserSkillResponse */ ],
  "averageRating": 4.5, "totalRatings": 12
}
```

Avatar upload rules (all failures are **400** with the usual error body):
- send `multipart/form-data` with the image in a part named `file` (e.g. Flutter `MultipartFile.fromPath('file', path)` on a `MultipartRequest('PUT', …)`); do **not** set the `Content-Type` header yourself, let the client add the boundary
- JPG, PNG or WebP only. The server checks the file's actual bytes, not the extension or declared type, so a renamed GIF/SVG/HTML file is rejected (`Avatar must be a JPG, PNG or WebP image`)
- max **2 MB** (`Avatar must be at most 2 MB`); resize/compress on the device first
- missing `file` part → `Required part 'file' is missing`
- uploading again replaces the old picture (the old file is deleted); `avatarUrl` is `null` until the first upload

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
| `page`, `size` | int | See [Pagination](#pagination). Results are ordered by user id (stable between pages) |

Returns `UserResponse[]`:
```json
{ "id": 4, "name": "Ravi", "email": null, "bio": "…", "city": "Pune", "avatarUrl": null,
  "averageRating": 4.2, "totalRatings": 5 }
```
No filters returns every other user, one page at a time.

> `email` is **`null`** on users returned by search and match results (privacy). It is still present on users inside exchanges, sessions, ratings and messages, where both parties are already connected.

### 4.8 Exchanges (swap requests)

| Method | Path | Body | Success |
|--------|------|------|---------|
| POST | `/api/exchanges` | `{ receiverId, offeredSkillId, wantedSkillId, message? }` | **201** `ExchangeResponse` |
| GET | `/api/exchanges?page&size` | all mine (sent + received), **newest first** | 200 `ExchangeResponse[]` |
| GET | `/api/exchanges/sent?page&size` | newest first | 200 |
| GET | `/api/exchanges/received?page&size` | newest first | 200 |
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
- `dateTime` must be **in the future** (400 `dateTime: Session date and time must be in the future; `) and `durationMinutes` must be **> 0** (400 `durationMinutes: Duration must be a positive number of minutes; `). Validate both in the form before sending.
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
- `proposedDateTime` must be **in the future** (400 `Validation Error`)
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
- Only the exchange's requester or receiver can rate; anyone else gets **404** `Session not found`. Session must then be `COMPLETED`. `ratedTo` is automatically the *other* participant.
- One rating per user per session → second attempt is a 400 `You have already rated this session`.

### 4.12 Messages (REST)

| Method | Path | Body | Success |
|--------|------|------|---------|
| POST | `/api/messages` | `{ receiverId, content (≤ 2000 chars) }` | **201** `MessageResponse` |
| GET | `/api/messages/conversations` | inbox list | 200 `ConversationResponse[]` |
| GET | `/api/messages/conversations/{otherUserId}?page&size` | one page of history (see below) | 200 `MessageResponse[]` |
| GET | `/api/messages/unread-count` | for a badge | 200 bare number, e.g. `3` |

```json
// MessageResponse
{ "id": 100, "sender": { /* UserResponse */ }, "receiver": { /* UserResponse */ },
  "content": "Hi!", "read": false, "createdAt": "…" }

// ConversationResponse
{ "otherUser": { /* UserResponse */ }, "lastMessage": { /* MessageResponse */ }, "unreadCount": 2 }
```
- **Paging a chat:** `page=0` is the **newest** `size` messages, `page=1` the `size` before those, and so on (scroll up to load older). Inside each page the messages are ordered **oldest → newest**, so render a page as-is and prepend older pages above it. An empty page means you reached the start of the conversation.
- **Side effect:** `GET /conversations/{otherUserId}` marks the messages *in the returned page* that were sent to me as read. Don't call it just to peek.
- Can't message yourself (400).
- There is no "mark as read" endpoint, no delete, no typing indicator. `GET /messages/conversations` (the inbox list) is not paginated.

### 4.13 Follows

| Method | Path | Body | Success |
|--------|------|------|---------|
| POST | `/api/users/{userId}/follow` | none | **201** `FollowResponse` |
| DELETE | `/api/users/{userId}/follow` | | **204** |
| GET | `/api/users/{userId}/followers` | people who follow `userId` (newest first) | 200 `UserResponse[]` |
| GET | `/api/users/{userId}/following` | people `userId` follows (newest first) | 200 `UserResponse[]` |
| GET | `/api/users/{userId}/follow-stats` | | 200 `FollowStatsResponse` |

```json
// FollowResponse
{ "id": 1, "follower": { /* UserResponse */ }, "following": { /* UserResponse */ }, "createdAt": "…" }

// FollowStatsResponse
{ "userId": 2, "followersCount": 5, "followingCount": 3, "followedByMe": true }
```
- The follower is always the logged-in user (from the JWT).
- Following yourself → 400 `You cannot follow yourself`. Already following → **409** `You are already following this user`. Unknown user → 404.
- Unfollowing someone you don't follow → 404 `You are not following this user`.
- Use `follow-stats` for the profile header and to decide between a Follow and an Unfollow button (`followedByMe`).

### 4.14 Posts, likes and comments

| Method | Path | Body / query | Success |
|--------|------|--------------|---------|
| POST | `/api/posts` | `{ skillId, content (required, ≤ 2000 chars) }` | **201** `PostResponse` |
| GET | `/api/posts` | explore: all posts, newest first. Query: `skillId?`, `page` (default 0), `size` (default 20, max 50) | 200 `PostResponse[]` |
| GET | `/api/posts/feed` | posts by people I follow **plus my own**. Same query params | 200 `PostResponse[]` |
| GET | `/api/posts/{postId}` | | 200 `PostResponse` |
| DELETE | `/api/posts/{postId}` | author only | **204** |
| GET | `/api/users/{userId}/posts` | one user's posts. Query: `page`, `size` | 200 `PostResponse[]` |
| POST | `/api/posts/{postId}/like` | none | **201** `PostResponse` (updated counts) |
| DELETE | `/api/posts/{postId}/like` | | **204** |
| POST | `/api/posts/{postId}/comments` | `{ content (required, ≤ 1000 chars) }` | **201** `CommentResponse` |
| GET | `/api/posts/{postId}/comments` | oldest first, not paginated | 200 `CommentResponse[]` |
| DELETE | `/api/posts/{postId}/comments/{commentId}` | comment author only | **204** |

```json
// PostResponse
{ "id": 10, "author": { /* UserResponse */ }, "skill": { /* SkillResponse */ },
  "content": "Day 30 of learning guitar scales", "likeCount": 4, "commentCount": 2,
  "likedByMe": false, "createdAt": "…" }

// CommentResponse
{ "id": 7, "postId": 10, "author": { /* UserResponse */ }, "content": "Nice!", "createdAt": "…" }
```
- Pagination: `page` is zero-based; `page < 0`, `size < 1` or `size > 100` → 400. Stop paging when a page returns fewer than `size` items. See [Pagination](#pagination).
- `content` is trimmed server-side; blank content → 400 `content: Content is required; `. Unknown `skillId` on create → 404 `Skill not found`.
- Liking twice → **409** `You have already liked this post`. Unliking a post you haven't liked → 404.
- Deleting someone else's post or comment returns **404**, not 403. Show the delete button only when `author.id === me.id`.
- Deleting a post also deletes its likes and comments.
- Posts are visible to every logged-in user (there are no private posts). `skillId` for the filter comes from `GET /api/skills`.
- A `skillId` that matches no posts (or doesn't exist) just returns `[]`.

### 4.15 Public profile

| Method | Path | Success |
|--------|------|---------|
| GET | `/api/users/{userId}` | 200 `PublicProfileResponse` (404 `User not found`) |

```json
// PublicProfileResponse
{
  "id": 4, "name": "Ravi", "bio": "…", "city": "Pune", "avatarUrl": "/files/avatars/4-….png",
  "ratingSummary": { "averageStars": 4.2, "totalRatings": 5,
                     "breakdown": { "oneStar": 0, "twoStar": 0, "threeStar": 1, "fourStar": 2, "fiveStar": 2 } },
  "teachSkills": [ /* UserSkillResponse */ ],
  "learnSkills": [ /* UserSkillResponse */ ],
  "followersCount": 12, "followingCount": 7, "followedByMe": false
}
```
- There is **no `email`** (and never will be) in this response. It also works for your own id.
- This replaces the old workaround of assembling a profile from search + availability + ratings. Availability is still a separate call: `GET /users/{id}/availability`.
- Follower/following counts are real (they come from the follow system), and `followedByMe` tells you which of Follow / Unfollow to show. For the people lists use `GET /users/{id}/followers` and `/following`.
- `UserResponse` now also carries `avatarUrl` everywhere it appears (exchanges, messages, search, …). Users shown through follows, posts and comments have `email: null`.

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
| My profile / edit | `GET/PUT /profile/me`, `PUT /profile/me/avatar`, `PUT /profile/me/password`, `GET /profile/me/ratings` |
| My skills | `GET /profile/skills`, `POST /profile/skills`, `DELETE /profile/skills/{id}` |
| Availability editor | `GET/POST /profile/availability`, `DELETE /profile/availability/{id}` |
| Discover / Matches | `GET /matches`, `GET /matches/mutual` |
| Search | `GET /users/search?...` |
| Other user's page | `GET /users/{id}` (profile, skills, rating summary, follow counts), `GET /users/{id}/availability`, `/users/{id}/ratings`, `GET /matches/{id}` |
| Send swap request | `POST /exchanges` |
| Requests inbox (sent/received) | `GET /exchanges/sent`, `/exchanges/received`, `PUT /exchanges/{id}/accept\|reject` |
| Exchange detail | `GET /exchanges/{id}`, `GET /exchanges/{id}/sessions`, `POST /exchanges/{id}/sessions`, `PUT /exchanges/{id}/complete` |
| Upcoming sessions | `GET /sessions/my-sessions` |
| Session detail | `GET /sessions/{id}`, `PUT …/complete`, `PUT …/cancel`, reschedule endpoints, `POST /sessions/{id}/ratings` |
| Inbox | `GET /messages/conversations`, `GET /messages/unread-count`, WebSocket |
| Chat thread | `GET /messages/conversations/{id}`, WebSocket `/app/chat.send` |
| Home feed | `GET /posts/feed[?skillId&page&size]`, `POST /posts`, `POST/DELETE /posts/{id}/like` |
| Explore / by skill | `GET /posts[?skillId&page&size]` |
| Post detail | `GET /posts/{id}`, `GET/POST /posts/{id}/comments`, `DELETE /posts/{id}/comments/{commentId}`, `DELETE /posts/{id}` |
| Follow lists / profile header | `GET /users/{id}/follow-stats`, `/users/{id}/followers`, `/users/{id}/following`, `POST/DELETE /users/{id}/follow`, `GET /users/{id}/posts` |

---

## 7. Gotchas & gaps the frontend should know about

**Missing endpoints (you may hit these while building screens)**
- Another user's profile is `GET /api/users/{id}` (see 4.15). `/api/profile/me` stays self-only and is the only one that includes your email.
- No token refresh, no logout, no password reset / email verification.
- No notifications endpoint; poll or use the chat socket for message badges.
- Pagination is not universal: see the list in [Pagination](#pagination). Endpoints not on that list return everything.
- No delete/edit for exchanges, sessions, messages, ratings. Posts can be deleted but not edited; comments likewise.
- No notifications for follows, likes or comments.

**Behaviors that surprise**
- Wrong credentials and every business-rule failure are **400**, not 401/403. Show `message` from the body.
- Reading an exchange/session you don't belong to is **404**, not 403.
- `POST /sessions/{id}/ratings` returns 404 for non-participants. Still hide the rate button unless `me` is the exchange's requester or receiver.
- Past dates are rejected (400) for new sessions and reschedule proposals; `durationMinutes` must be > 0.
- Avatars are `multipart/form-data` (don't send JSON), and `GET /files/**` is public.
- Message history pages count back from the newest message (page 0 = latest), unlike the other lists.
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

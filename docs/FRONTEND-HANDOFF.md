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
| 13 | **Follows** | Follow / unfollow users (idempotent); paginated follower and following lists; follow status in both directions; follower/following counts on the public profile. |
| 14 | **Posts & feed** | Post text and/or up to 4 images, tagged with up to 3 skills. Explore all posts or a feed of people I follow (+ my own), optionally filtered by skill tag. A user's posts are cursor-paginated. Like (idempotent) and comment, with one level of replies. |
| 15 | **Public profiles & avatars** | `GET /users/{id}` returns another user's public profile (never the email). Users upload a profile picture (JPG/PNG/WebP, ≤ 2 MB). |
| 16 | **Feeds** | A home feed (people I follow + me, newest first), a trending Explore tab (last 7 days, people I don't follow) and suggested people to follow. |

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

Paginated today: `GET /users/search`, `GET /exchanges`, `/exchanges/sent`, `/exchanges/received`, `GET /messages/conversations/{userId}`, `GET /users/{id}/followers`, `GET /users/{id}/following`, `GET /posts`, `/posts/feed`, `GET /posts/{id}/likes`.
**Cursor-paginated** (envelope below): `GET /feed`, `GET /explore`, `GET /users/{id}/posts`, `GET /posts/{id}/comments`.
`GET /explore/people` uses `page` / `size`.
**Not** paginated yet: matches, skills, ratings, `GET /messages/conversations` (inbox list), sessions.

**Cursor pagination.** New list endpoints, `GET /feed`, `GET /explore`, `GET /users/{id}/posts` and `GET /posts/{id}/comments` return this envelope instead of an array:
```json
{ "items": [ /* … */ ], "nextCursor": "MTIz" }
```
Request the next page with `?cursor=<nextCursor>&size=20`. `nextCursor` is `null` on the last page. Treat the cursor as an opaque string and never build one yourself; a malformed one is a 400 `Invalid cursor`. There is no `page` parameter: you can only move forward from the cursor you were given. Unlike page numbers, a cursor stays correct when new items arrive while you are scrolling (nothing is repeated or skipped). `size` follows the usual rules (default 20, max 100; otherwise 400).

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
| 409 | Email already registered; skill already added; or any database conflict (e.g. replying to a comment at the moment it is deleted). Liking, unliking and following are idempotent and never return 409 |
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

| Method | Path | Body / query | Success |
|--------|------|--------------|---------|
| PUT | `/api/users/{userId}/follow` | none. **Idempotent** | 200 `FollowResponse` |
| DELETE | `/api/users/{userId}/follow` | none. **Idempotent** | **204** |
| GET | `/api/users/{userId}/followers` | people who follow `userId`, newest first. `page`, `size` | 200 `UserResponse[]` |
| GET | `/api/users/{userId}/following` | people `userId` follows, newest first. `page`, `size` | 200 `UserResponse[]` |
| GET | `/api/users/{userId}/follow-status` | | 200 `FollowStatusResponse` |

```json
// FollowResponse
{ "id": 1, "follower": { /* UserResponse */ }, "following": { /* UserResponse */ }, "createdAt": "…" }

// FollowStatusResponse
{ "userId": 2, "isFollowedByMe": true, "isFollowingMe": false, "followersCount": 5, "followingCount": 3 }
```
- The follower is always the logged-in user (from the JWT).
- `PUT` is idempotent: following someone you already follow is **not an error**. It returns **200** with the existing follow, so a double tap or a retry after a timeout is safe. `DELETE` is idempotent too: unfollowing someone you don't follow is a **204** no-op. Both therefore never need a 409/404 special case in the UI.
- Following yourself → **400** `You cannot follow yourself`. A user id that doesn't exist → **404** `User not found` (for `PUT`, `DELETE`, both lists and `follow-status`).
- Lists follow the standard [pagination](#pagination) rules (default `size` 20, max 100; a page past the end is `[]`). Users in these lists have `email: null`.
- `isFollowedByMe` = I follow them; `isFollowingMe` = they follow me. Show **Follow back** when `isFollowingMe && !isFollowedByMe`, and a mutual badge when both are true. The JSON keys really are `isFollowedByMe` / `isFollowingMe` (with the `is` prefix), the same style as `isMutual` on matches.
- **Changed from the first version of this API:** `POST /follow` is gone (it now returns **405**; use `PUT`), `follow-stats` is renamed `follow-status` (the old URL is a 404), `followedByMe` is now `isFollowedByMe`, and the lists were not paginated before.

### 4.14 Posts, likes and comments

| Method | Path | Body / query | Success |
|--------|------|--------------|---------|
| POST | `/api/posts` | **JSON** `{ content?, skillIds? }` (text-only) **or multipart** (with images), see below | **201** `PostResponse` |
| GET | `/api/posts` | explore: all posts, newest first. Query: `skillId?`, `page`, `size` | 200 `PostResponse[]` |
| GET | `/api/posts/feed` | posts by people I follow **plus my own**. Same query params | 200 `PostResponse[]` |
| GET | `/api/posts/{postId}` | | 200 `PostResponse` |
| DELETE | `/api/posts/{postId}` | author only | **204** |
| GET | `/api/users/{userId}/posts` | one user's posts, newest first. Query: `cursor?`, `size` | 200 `{ items: PostResponse[], nextCursor }` |
| PUT | `/api/posts/{postId}/like` | none. **Idempotent** | **200** `PostResponse` (current counts, `likedByMe: true`) |
| DELETE | `/api/posts/{postId}/like` | none. **Idempotent** | **204** |
| GET | `/api/posts/{postId}/likes` | who liked it, most recent like first. Query: `page`, `size` | 200 `UserResponse[]` |
| POST | `/api/posts/{postId}/comments` | `{ content (required, ≤ 500 chars), parentId? }` | **201** `CommentResponse` |
| GET | `/api/posts/{postId}/comments` | top-level comments, oldest first, replies nested. Query: `cursor?`, `size` | 200 `{ items: CommentResponse[], nextCursor }` |
| DELETE | `/api/comments/{commentId}` | comment author **or** post author | **204** |

```json
// PostResponse
{ "id": 10, "author": { /* UserResponse, email is null */ },
  "content": "Day 30 of learning guitar scales",
  "skills": [ { "id": 1, "name": "Guitar", "category": "MUSIC", "description": "…" } ],
  "media": [ { "url": "/files/posts/3-9c1e….jpg", "position": 0 },
             { "url": "/files/posts/3-41ab….png", "position": 1 } ],
  "likeCount": 4, "commentCount": 2, "likedByMe": false, "createdAt": "2026-11-04T12:00:00" }

// GET /api/users/{id}/posts
{ "items": [ /* PostResponse, newest first */ ], "nextCursor": "MjAyNi0xMS0wNFQxMjowMHw3" }

// CommentResponse (top-level comment with its replies nested one level)
{ "id": 7, "postId": 10, "parentId": null, "author": { /* UserResponse, email is null */ },
  "content": "Nice!", "createdAt": "…",
  "replies": [
    { "id": 9, "postId": 10, "parentId": 7, "author": { /* … */ }, "content": "Agreed", "createdAt": "…", "replies": [] }
  ] }
```

**Creating a post.** A post needs **text and/or at least one image**; skill tags are optional.

| Rule | Limit | Error (all 400 unless noted) |
|------|-------|------------------------------|
| text or image | at least one (blank text counts as none) | `A post needs text or at least one image` |
| text length | ≤ 2000 characters (trimmed) | `Content must be at most 2000 characters` |
| images | ≤ **4** | `A post can have at most 4 images` |
| image size | ≤ **2 MB each** (whole request ≤ 10 MB) | `Each image must be at most 2 MB` / `Upload too large: …` |
| image type | JPG, PNG or WebP, checked by the file's bytes, not its name | `Images must be JPG, PNG or WebP` |
| skill tags | ≤ **3** distinct ids; repeats are ignored | `A post can have at most 3 skill tags` |
| unknown skill id | | **404** `Skill not found` |

- **Text only → JSON:** `Content-Type: application/json`, body `{ "content": "…", "skillIds": [1, 2] }`.
- **With images → multipart:** `Content-Type: multipart/form-data` (let your HTTP client set the boundary), with the text in a part `content`, one `skillIds` part **per tag** (repeat the field), and one `images` part **per image** (repeat the field; the order you send them is the display order). Any other content type → **415**. Flutter example: `request.fields['content'] = text; request.files.add(await http.MultipartFile.fromPath('images', path));` for each image; add `skillIds` as repeated fields (build the multipart body with a list of `MapEntry`s, since `fields` is a plain map).
- Validation is all-or-nothing: if any image is rejected, nothing is saved or stored. Empty file parts (some clients send one when no file is chosen) are ignored.
- Image-only posts have `"content": null`: render the post without a text block.
- `media[].position` is 0-based and already sorted; `url` is a root-relative path, prefix it with the base URL (see [Files](#files-avatars)). Image files are public, like avatars.

**Behaviour**
- `likeCount` and `commentCount` come straight from the post (no extra work for the server per request). They are exact even when many people act at the same time.
- `likedByMe` is in every `PostResponse` (single post, feed, explore, a user's posts and the response of `PUT /like`): it is `true` when the logged-in user liked that post.
- Deleting someone else's post returns **404**, not 403. Show the post's delete button only when `author.id === me.id`.

**Likes** (`PUT` / `DELETE /api/posts/{id}/like`)
- Both are **idempotent**, so a double tap or a retry after a timeout is safe and never needs error handling: `PUT` on a post you already like returns **200** with unchanged counts (it is *not* a 409 any more), and `DELETE` on a post you don't like is a **204** no-op. The counter moves by exactly one only when a like is really added or removed, however many identical requests arrive together.
- The usual pattern: flip `likedByMe` and `likeCount` locally, send `PUT` or `DELETE`, and replace your local post with the `PUT` response (or ignore the `DELETE` response, it has no body).
- A post that doesn't exist → **404** for `PUT`, `DELETE` and `GET /likes`.
- `GET /posts/{id}/likes` lists the users (no emails), most recent like first, with the usual `page` / `size` rules.

**Comments and replies**
- One level of replies only. To reply, send the **top-level** comment's id as `parentId`. Replying to a reply is **400** `Replies can only be one level deep: reply to the top-level comment` (send the *top-level* comment's id instead; show "replying to @name" in the UI if you want). A `parentId` that doesn't exist, or belongs to another post, is **404** `Parent comment not found`.
- `content` is trimmed and must be 1 to **500** characters (400 otherwise). Older comments written before this limit can be longer.
- `GET /posts/{id}/comments` returns the post's **top-level** comments oldest first, **cursor-paginated** (see [Pagination](#pagination); `size` default 20, max 100), and each one carries **all** its replies in `replies`, oldest first. The paging counts top-level comments only. Replies on a reply object are always `[]`. An unknown post → 404.
- Comment ids only ever grow, so new comments appear at the end of the last page; to show a comment you just posted, append the `POST` response yourself (to its parent's `replies` for a reply).
- `commentCount` on the post counts **every** comment, replies included.
- `DELETE /api/comments/{id}` (note: no post id in the URL) is allowed for the **comment's author** and for the **post's author** (so a post owner can moderate their own post). Anyone else gets **404** `Comment not found`, the same as for a comment that doesn't exist. Deleting a top-level comment **also deletes all its replies**, and `commentCount` drops by the comment plus its replies. Show the delete button when `comment.author.id === me.id || post.author.id === me.id`.
- Replying to a thread at the very moment it is deleted can return **409**; just reload the comments.
- Deleting a post also deletes its likes, comments, skill tags, image rows **and the image files** (those URLs then return 404).
- `GET /api/users/{id}/posts`: first page without `cursor`; keep sending the returned `nextCursor` until it is `null`. An unknown user → 404. Because the cursor is a position, not a page number, posts created or deleted while you scroll never cause duplicates or gaps.
- `GET /api/posts` and `/api/posts/feed` still use `page`/`size`; their `skillId` filter now matches **any** of a post's tags. For the home and explore screens use `GET /api/feed` and `GET /api/explore` instead (4.16): cursor-paginated and cheaper.
- Posts are visible to every logged-in user (there are no private posts). `skillId` values come from `GET /api/skills`.
- **Changed from the first version of this API:** the single required `skillId` is now an optional `skillIds` list (and `PostResponse.skill` is now `skills[]`); `content` is optional when images are attached and can be `null` in responses; `PostResponse` gained `media`; and `GET /users/{id}/posts` now returns the cursor envelope instead of an array and ignores `page`. Existing posts keep their old skill as a single tag.
- **Changed in the engagement update:** `POST /posts/{id}/like` is gone (**405**; use `PUT`, which is idempotent and returns **200** instead of 201 / 409). `DELETE /posts/{postId}/comments/{commentId}` is replaced by `DELETE /comments/{commentId}` (the old URL is a 404). `GET /posts/{id}/comments` now returns the cursor envelope with nested `replies` instead of a flat array, comments are limited to 500 characters (was 1000), and `CommentResponse` gained `parentId` and `replies`.

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
  "followersCount": 12, "followingCount": 7, "isFollowedByMe": false
}
```
- There is **no `email`** (and never will be) in this response. It also works for your own id.
- This replaces the old workaround of assembling a profile from search + availability + ratings. Availability is still a separate call: `GET /users/{id}/availability`.
- Follower/following counts are real (they come from the follow system), and `isFollowedByMe` tells you which of Follow / Unfollow to show (the key is `isFollowedByMe`, not `followedByMe`). For the people lists use `GET /users/{id}/followers` and `/following`.
- `UserResponse` now also carries `avatarUrl` everywhere it appears (exchanges, messages, search, …). Users shown through follows, posts and comments have `email: null`.

### 4.16 Feeds: home, explore, suggested people

| Method | Path | Query | Success |
|--------|------|-------|---------|
| GET | `/api/feed` | `cursor?`, `size` (default 20, max 100) | 200 `{ items: PostResponse[], nextCursor }` |
| GET | `/api/explore` | `skillId?`, `cursor?`, `size` (default 20, max 100) | 200 `{ items: PostResponse[], nextCursor }` |
| GET | `/api/explore/people` | `page` (default 0), `size` (default 20, max 100) | 200 `MatchResponse[]` |

Every item in `/feed` and `/explore` is a full `PostResponse` (see 4.14): `author` summary (name, avatar, bio, city; email is `null`), `content` (can be `null`), `skills[]`, `media[]`, `likeCount`, `commentCount`, `likedByMe`, `createdAt`. You never need a second request per post.

**Home feed (`GET /api/feed`)**
- Posts by **people I follow plus my own**, newest first. Following someone makes their posts appear immediately; unfollowing removes them immediately. Your own new post is in the feed right away.
- Infinite scroll: first request without `cursor`, then keep sending the previous response's `nextCursor` until it is `null`. Pull-to-refresh: request again **without** a cursor and replace the list. Posts that arrive while the user is scrolling never cause duplicates or gaps in the pages that follow.
- This replaces `GET /api/posts/feed` for the home screen. The old endpoint still works (it uses `page` / `size`, so new posts shift its pages), but `/api/feed` is the one to build on.
- An empty `items` with `nextCursor: null` means "nothing yet": suggest people to follow (below).

**Explore (`GET /api/explore`)**
- **Trending posts of the last 7 days**, best first, from **people I do not follow** (and not my own). Posts older than 7 days never appear.
- Ranking: `(likes + 2 × comments)` divided by a power of the post's age, so a comment counts as two likes and newer posts beat older ones with similar engagement. Posts with no likes or comments are still included, at the bottom (newest first), so a new community is not empty.
- `skillId` (from `GET /api/skills`) keeps only posts tagged with that skill. An unknown skill just gives an empty list.
- Paging uses a cursor that remembers when the ranking started, so all pages of one scroll are ranked consistently. Counts can change while you scroll, so **skip any post id you already have** (a post can occasionally move across a page boundary). Start a new scroll (no cursor) to re-rank from now.
- Following someone removes their posts from Explore on the next request.
- A cursor from one endpoint is not valid on another (`/feed`, `/explore`, `/users/{id}/posts` and `/posts/{id}/comments` each make their own): wrong or garbled cursors are **400** `Invalid cursor`.

**Suggested people (`GET /api/explore/people`)**
- The same matching as `GET /api/matches` (people whose skills complement mine: `theyCanTeachMe` / `iCanTeachThem`, `isMutual` first), **without anyone I already follow**. The item shape is exactly `MatchResponse`, so the match card UI can be reused; show a **Follow** button (`PUT /users/{id}/follow`) and drop the card on success.
- Empty when the user has not added any skills yet: show a hint to add skills (`POST /profile/skills`) instead.
- Not cursor-based: it is a ranked list, paged with `page` / `size` (a page past the end is `[]`).

```json
// GET /api/feed?size=2  (shortened)
{
  "items": [
    { "id": 812, "author": { "id": 4, "name": "Ravi", "avatarUrl": "/files/avatars/4-….png", "email": null, "…": "…" },
      "content": "Day 30 of guitar scales", "skills": [ { "id": 1, "name": "Guitar", "…": "…" } ],
      "media": [ { "url": "/files/posts/4-….jpg", "position": 0 } ],
      "likeCount": 12, "commentCount": 3, "likedByMe": true, "createdAt": "2026-11-04T17:20:11.482913" },
    { "id": 805, "…": "…" }
  ],
  "nextCursor": "MjAyNi0xMS0wNFQxNzoyMDoxMS40ODI5MTN8ODEy"
}
```

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
| Home feed | `GET /feed[?cursor&size]`, `POST /posts` (JSON, or multipart with images), `PUT/DELETE /posts/{id}/like` |
| Explore (trending) | `GET /explore[?skillId&cursor&size]`, skill chips from `GET /skills` |
| Who to follow | `GET /explore/people[?page&size]`, `PUT /users/{id}/follow` |
| Post detail | `GET /posts/{id}`, `GET /posts/{id}/comments[?cursor&size]`, `POST /posts/{id}/comments` (with `parentId` to reply), `DELETE /comments/{id}`, `DELETE /posts/{id}`, `GET /posts/{id}/likes` (who liked) |
| Follow lists / profile header | `GET /users/{id}/follow-status`, `/users/{id}/followers`, `/users/{id}/following`, `PUT/DELETE /users/{id}/follow`, `GET /users/{id}/posts[?cursor&size]` (cursor-paginated: profile post grid / infinite scroll) |

---

## 7. Gotchas & gaps the frontend should know about

**Missing endpoints (you may hit these while building screens)**
- Another user's profile is `GET /api/users/{id}` (see 4.15). `/api/profile/me` stays self-only and is the only one that includes your email.
- No token refresh, no logout, no password reset / email verification.
- No notifications endpoint; poll or use the chat socket for message badges.
- Pagination is not universal: see the list in [Pagination](#pagination). Endpoints not on that list return everything.
- No delete/edit for exchanges, sessions, messages, ratings. Posts can be deleted but not edited (no changing text, tags or images); comments likewise.
- No notifications for follows, likes, comments or replies.

**Behaviors that surprise**
- Wrong credentials and every business-rule failure are **400**, not 401/403. Show `message` from the body.
- Reading an exchange/session you don't belong to is **404**, not 403.
- `POST /sessions/{id}/ratings` returns 404 for non-participants. Still hide the rate button unless `me` is the exchange's requester or receiver.
- Past dates are rejected (400) for new sessions and reschedule proposals; `durationMinutes` must be > 0.
- Avatars and post images are `multipart/form-data` (don't send JSON for those), and `GET /files/**` is public.
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

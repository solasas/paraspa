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
| 17 | **Notifications** | Someone followed / liked / commented / replied / requested or accepted an exchange / messaged me. A list with an unread badge, mark as read, and a real-time push over the WebSocket. |

---

## 2. Connection basics

| Item | Value |
|------|-------|
| Base URL (dev) | `http://localhost:8080` |
| All REST routes | prefixed `/api` |
| Auth | `Authorization: Bearer <access token>` on every request except `/api/auth/**` |
| Token lifetime | **Access token 15 min** (`expiresIn: 900` seconds in `AuthResponse`). Renew it with the **refresh token** (30 days, single use) at `POST /api/auth/refresh`; see 4.1. |
| Content type | `application/json` |
| CORS allowed origins | `http://localhost:3000`, `http://localhost:8080` only. Other dev ports/hosts will be blocked until `SecurityConfig` is changed. |
| CORS methods | GET, POST, PUT, DELETE, OPTIONS (no PATCH) |
| Logout | `POST /api/auth/logout` with the refresh token, then discard both tokens (4.1). |

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
**Cursor-paginated** (envelope below): `GET /notifications`, `GET /feed`, `GET /explore`, `GET /users/{id}/posts`, `GET /posts/{id}/comments`.
`GET /explore/people` uses `page` / `size`.
**Not** paginated yet: matches, skills, ratings, `GET /messages/conversations` (inbox list), sessions.

**Cursor pagination.** New list endpoints, `GET /notifications`, `GET /feed`, `GET /explore`, `GET /users/{id}/posts` and `GET /posts/{id}/comments` return this envelope instead of an array:
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
| 401 | **Missing, invalid or expired access token** on a protected route (JSON body, `WWW-Authenticate: Bearer`) → refresh once, then log in again. Also a bad/expired/used refresh token. Wrong login credentials are still **400** |
| 403 | Logged in but not allowed (for example a non-admin on `/api/admin/**`); JSON body |
| 429 | Rate limit hit (login, register, refresh, sending messages). `Retry-After` header = seconds to wait; JSON body |
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
| `FollowStatus` | `PENDING`, `ACCEPTED` |
| `ReportTargetType` | `USER`, `POST`, `COMMENT` |
| `ReportReason` | `SPAM`, `HARASSMENT`, `HATE_SPEECH`, `SEXUAL_CONTENT`, `VIOLENCE`, `MISINFORMATION`, `IMPERSONATION`, `OTHER` (`OTHER` needs `details`) |
| `ReportStatus` | `OPEN`, `RESOLVED` |

---

## 4. REST endpoints

Legend: 🔓 public · 🔒 needs JWT.

### 4.1 Auth

| Method | Path | Auth | Body | Success |
|--------|------|------|------|---------|
| POST | `/api/auth/register` | 🔓 | `{ name, email, password }` all required, email must be valid | **201** `AuthResponse` |
| POST | `/api/auth/login` | 🔓 | `{ email, password }` | **200** `AuthResponse` |
| POST | `/api/auth/refresh` | 🔓 | `{ refreshToken }` | **200** `AuthResponse` (a new pair) |
| POST | `/api/auth/logout` | 🔓 | `{ refreshToken }` | **204**, idempotent |

```json
// AuthResponse
{ "token": "eyJ...", "refreshToken": "q3Zx...", "expiresIn": 900, "type": "Bearer",
  "id": 1, "name": "Asha", "email": "asha@x.com" }
```
- `token` is the **access token** (JWT, 15 minutes): send it as `Authorization: Bearer <token>`. `refreshToken` is an opaque string, valid 30 days.
- **Refresh tokens are single use and rotate.** Every `POST /refresh` returns a **new** `token` **and** a new `refreshToken`; the old refresh token is dead the moment it is used. Store the new one immediately (secure storage), replacing the old.
- **Recommended client flow:** on a `401` from any protected call, call `/refresh` once (serialise it: if several requests fail at the same time, send ONE refresh and retry them all with the result), then retry the original request. If `/refresh` itself returns `401`, the session is over: clear the tokens and show the login screen. Do not retry a refresh that failed.
- **Never send the same refresh token twice** (for example from two screens or after a crash before you saved the new one): a second use is treated as theft and the server **signs out every device of that user**.
- **Logout:** `POST /logout` with the refresh token, then drop both tokens. It signs out that device only. Unknown or already-used tokens also return 204.
- **Changing the password** (`PUT /api/profile/me/password`) signs out every device: all refresh tokens stop working (access tokens already issued live on for up to 15 minutes). Keep the current device logged in by logging in again with the new password.
- The WebSocket checks the access token only at `CONNECT` (see section 5): reconnect with a fresh access token after a refresh.
- Rate limits: login 10/min and refresh 30/min per IP, register 10/hour per IP. A `429` carries `Retry-After`.
- Duplicate email → **409** `Email already registered`.
- Wrong email *or* password → **400** `Invalid email or password` (same message for both).
- Bad, expired or already-used refresh token → **401** `Invalid or expired refresh token`.

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
| DELETE | `/api/users/{userId}/follow` | none. **Idempotent**; also cancels a pending request | **204** |
| GET | `/api/users/{userId}/followers` | accepted followers, newest first. `page`, `size` | 200 `UserResponse[]` |
| GET | `/api/users/{userId}/following` | accepted, newest first. `page`, `size` | 200 `UserResponse[]` |
| GET | `/api/users/{userId}/follow-status` | | 200 `FollowStatusResponse` |
| GET | `/api/follow-requests` | pending requests to **my** private account. `page`, `size` | 200 `FollowRequestResponse[]` |
| PUT | `/api/follow-requests/{id}/accept` | only the account owner | **204** |
| PUT | `/api/follow-requests/{id}/reject` | only the account owner | **204** |

```json
// FollowResponse
{ "id": 1, "follower": { /* UserResponse */ }, "following": { /* UserResponse */ }, "status": "ACCEPTED", "createdAt": "…" }

// FollowStatusResponse
{ "userId": 2, "isFollowedByMe": true, "isFollowRequestedByMe": false, "isFollowingMe": false, "followersCount": 5, "followingCount": 3 }

// FollowRequestResponse
{ "id": 9, "requester": { /* UserResponse */ }, "createdAt": "…" }
```
- The follower is always the logged-in user (from the JWT).
- **Private accounts:** if the target has `isPrivate: true`, `PUT /follow` returns `status: "PENDING"` and the owner is notified (`FOLLOW_REQUEST`). Show **Requested** (tap to cancel with `DELETE`) when `isFollowRequestedByMe`; `isFollowedByMe` is true only once `ACCEPTED`. Accepting notifies the requester (`FOLLOW_ACCEPTED`). Counts and both lists contain accepted follows only.
- `PUT` is idempotent: repeating it returns **200** with the existing follow (whatever its status). `DELETE` is a **204** no-op when there is nothing to remove.
- Following yourself → **400**. Unknown user, or a user who blocked you or whom you blocked → **404** `User not found` (if *you* blocked them: **400** `You have blocked this user. Unblock them first.`).
- Lists follow the standard [pagination](#pagination) rules. Users in these lists have `email: null`. The followers/following lists of a private account you do not follow → **403** `This account is private`.
- `isFollowedByMe` = I follow them; `isFollowingMe` = they follow me. Show **Follow back** when `isFollowingMe && !isFollowedByMe`. The JSON keys really are `isFollowedByMe` / `isFollowRequestedByMe` / `isFollowingMe`.
- **Changed:** `FollowResponse` gained `status`; `FollowStatusResponse` gained `isFollowRequestedByMe`.

### 4.13a Safety: block, report, private accounts, admin

| Method | Path | Notes | Success |
|--------|------|-------|---------|
| PUT | `/api/users/{userId}/block` | **Idempotent.** Also removes follows/requests both ways and clears notifications between you two | **204** |
| DELETE | `/api/users/{userId}/block` | **Idempotent.** Does not restore follows | **204** |
| GET | `/api/blocks` | people **I** blocked. `page`, `size` | 200 `UserResponse[]` |
| POST | `/api/reports` | `{ "targetType": "USER\|POST\|COMMENT", "targetId": 5, "reason": "SPAM", "details": "optional, max 1000" }` | **201** `ReportResponse` |
| GET | `/api/admin/reports` | **admin only.** `status=OPEN` (default) or `RESOLVED`, `cursor`, `size` | 200 `{ items: ReportResponse[], nextCursor }` |
| PUT | `/api/admin/reports/{id}/resolve` | **admin only.** Optional body `{ "note": "…" }` | 200 `ReportResponse` |

- **Blocking is two-way and silent.** After A blocks B, neither sees the other in search, matches, suggestions, explore, feed, post lists, comments, likes or follower lists; `GET /users/{id}`, `/users/{id}/posts` and `/posts/{id}` for the other person are **404**. Blocked people cannot follow, message or start an exchange with each other (the blocked person gets 404, the blocker 400 "unblock first"). Do not tell the blocked person they were blocked; to them the user just vanished.
- **Reports:** 409 `You have already reported this` on a repeat; 400 for reporting yourself or your own post/comment, or `OTHER` without `details`; 404 if the target does not exist. Always show a confirmation, there is no undo.
- **Private accounts:** `isPrivate` (boolean) is on `UserResponse`, `ProfileResponse` and the public profile; change it with `PUT /api/profile/me` `{ "isPrivate": true }`. The public profile also has `isFollowRequestedByMe`. For someone you do not follow (accepted): `GET /posts/{id}` → **404**, `GET /users/{id}/posts` → **403** `This account is private` (show a lock screen), and their posts are absent from every list. Going public accepts all waiting requests.
- **Admin:** `ReportResponse` = `{ id, targetType, targetId, reason, details, status, createdAt, reporter, targetPreview, reportsOnTarget, resolvedAt, resolvedBy, resolutionNote }`. `targetPreview` is the user's name, a post/comment excerpt, `[image post]` or `[deleted]`. A non-admin gets **403** `You do not have permission to do this`. Resolving twice → 400. Admins can also `DELETE /api/posts/{id}` for any post. Admin role is set in the database (`UPDATE users SET role = 'ADMIN' …`), not through the API; your own role is not in the profile response, so a client should simply try the admin screens and handle 403.

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

### 4.17 Notifications

| Method | Path | Query | Success |
|--------|------|-------|---------|
| GET | `/api/notifications` | `cursor?`, `size` (default 20, max 100) | 200 `{ items: NotificationResponse[], nextCursor }`, newest first |
| GET | `/api/notifications/unread-count` | | 200 bare number, e.g. `3` (like `/messages/unread-count`) |
| PUT | `/api/notifications/{id}/read` | | **204**. Idempotent. Not yours or missing: **404** `Notification not found` |
| PUT | `/api/notifications/read-all` | | **204**. Marks all of *my* notifications read |

```json
// NotificationResponse (the same object is pushed over the WebSocket, see section 5)
{ "id": 40, "type": "LIKE", "targetType": "POST", "targetId": 812,
  "actor": { "id": 5, "name": "Ben", "avatarUrl": "/files/avatars/5-….png", "email": null, "…": "…" },
  "read": false, "createdAt": "2026-11-04T17:20:11.482913" }
```

**What each type means and where to send the user when it is tapped**

| `type` | Who gets it | Text for the UI | `targetType` / `targetId` | Open |
|--------|-------------|-----------------|---------------------------|------|
| `FOLLOW` | the person followed | "{actor} started following you" | `USER` / the follower's id | profile `GET /users/{id}` |
| `FOLLOW_REQUEST` | the private account's owner | "{actor} wants to follow you" | `USER` / the requester's id | follow requests screen (`GET /follow-requests`) |
| `FOLLOW_ACCEPTED` | the requester | "{actor} accepted your follow request" | `USER` / the owner's id | profile `GET /users/{id}` |
| `LIKE` | the post's author | "{actor} liked your post" | `POST` / post id | post `GET /posts/{id}` |
| `COMMENT` | the post's author | "{actor} commented on your post" | `POST` / post id | the post's comments |
| `REPLY` | the author of the comment replied to | "{actor} replied to your comment" | `POST` / post id | the post's comments |
| `EXCHANGE_REQUEST` | the receiver | "{actor} wants to swap skills with you" | `EXCHANGE` / exchange id | `GET /exchanges/{id}` |
| `EXCHANGE_ACCEPTED` | the requester | "{actor} accepted your exchange" | `EXCHANGE` / exchange id | `GET /exchanges/{id}` |
| `MESSAGE` | the receiver | "{actor} sent you a message" | `MESSAGE` / newest message id | chat with `actor.id` |

**Behaviour you must know**
- **Never about yourself.** Liking, commenting or replying on your own things creates nothing.
- **Only the first like (and the first follow) counts.** If someone likes, unlikes and likes again, or follows, unfollows and follows again, you get one notification, not three.
- **Messages collapse.** While you have an unread `MESSAGE` notification from a person, more messages from them do **not** add rows: the same notification (same `id`) now points at their newest message and moves to the top. Once you have read it, their next message creates a new one. So a chat shows as one entry per person, not one per message.
- **Replies notify only the person replied to**, not the post's author (they get `COMMENT` only for top-level comments).
- **Newest first** by `createdAt` (a collapsed message notification counts as new again). Paginate with the cursor exactly like the feed: first request without `cursor`, then send `nextCursor` until it is `null`; garbled cursor is **400** `Invalid cursor`.
- **Appears a moment after the action.** The notification is created a few milliseconds after the request that caused it returns (it is handled on a background thread), so do not expect the very next request to already list it, and refresh the badge from the WebSocket push or after a short delay.
- **The target may be gone.** A post can be deleted after someone liked it; opening the notification can then return **404**. Show a "no longer available" message and mark it read.
- **Read state is yours.** You can only mark your own notifications. Marking an already read one is fine (204).
- Badge: `GET /notifications/unread-count`, refresh on app start, on reconnect, and whenever a push arrives (or increment locally: +1 for a push whose `id` you do not have yet, nothing for one you do; see section 5).

---

## 5. Real-time chat and notifications (WebSocket / STOMP)

Use REST for history, WebSocket for live updates and sending. The same connection carries chat messages and notifications.

| Item | Value |
|------|-------|
| Endpoint | `ws://localhost:8080/ws` — **plain WebSocket, SockJS is not enabled** |
| Protocol | STOMP |
| Allowed origins | `http://localhost:3000`, `http://localhost:8080` |
| Auth | STOMP `CONNECT` frame header `Authorization: Bearer <jwt>`. Missing/invalid → connection rejected |
| Send to | `/app/chat.send` with body `{ "receiverId": 4, "content": "hello" }` |
| Subscribe (messages) | `/user/queue/messages` → receives `MessageResponse` |
| Subscribe (notifications) | `/user/queue/notifications` → receives a `NotificationResponse` (see 4.17) for every new notification of the logged-in user |
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

### Notifications over the WebSocket

Subscribe once after connecting:
```js
client.subscribe('/user/queue/notifications', f => onNotification(JSON.parse(f.body)));
```
Each frame body is one JSON object, the **same shape as an item of `GET /api/notifications`** (dates are ISO strings, `actor` has no email):
```json
{ "id": 40, "type": "FOLLOW", "targetType": "USER", "targetId": 5,
  "actor": { "id": 5, "name": "Ben", "bio": null, "city": "Pune", "avatarUrl": null, "email": null,
             "averageRating": null, "totalRatings": null },
  "read": false, "createdAt": "2026-11-04T17:20:11.482913" }
```
How to handle it:
- **Upsert by `id`.** A `MESSAGE` notification is re-sent with the same `id` each time another message arrives (it points at the newest message), so replace the existing item and move it to the top instead of adding a second one.
- **Only your own notifications arrive**, and never the ones for things you did yourself.
- **The push is a convenience, not a guarantee.** If the socket is down (app in background, offline, token expired) nothing is queued. On every (re)connect, call `GET /api/notifications/unread-count` and refresh the list from the REST endpoint.
- A user with several open sessions (phone and web) gets the frame on each.
- The destination is `/user/queue/notifications` (the `/user` prefix is added by STOMP; the server sends to `/queue/notifications` for that user).

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
| Notifications | `GET /notifications[?cursor&size]`, `GET /notifications/unread-count`, `PUT /notifications/{id}/read`, `PUT /notifications/read-all`; live: WebSocket `/user/queue/notifications` |
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
- No password reset / email verification.
- Pagination is not universal: see the list in [Pagination](#pagination). Endpoints not on that list return everything.
- No delete/edit for exchanges, sessions, messages, ratings. Posts can be deleted but not edited (no changing text, tags or images); comments likewise.
- No notification settings (mute a type, push notifications to the phone when the app is closed): notifications are in-app only, delivered over the open WebSocket or fetched from the list.

**Behaviors that surprise**
- Lists of posts, comments, likes, matches, search and suggestions silently leave out blocked people (both ways) and private accounts you are not accepted by, so a page can hold fewer items than expected only at the very end; `commentCount` / `likeCount` on a post are totals and still include hidden people.
- A malformed JSON body or an unknown enum value (for example a wrong report `reason`) is **400**, not 500.
- Wrong credentials and every business-rule failure are **400**, not 401/403. Show `message` from the body. A **401** always means "the token is missing/expired": refresh, or log in again.
- Sending messages is rate limited (30/min per user, shared by REST and the chat socket); on the socket the error text arrives on `/user/queue/errors`.
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

# SkillSwap - Community Skill Exchange Platform

A complete REST API backend for a community skill exchange platform built with Spring Boot 3.2.0 and PostgreSQL.

## Features

SkillSwap lets people trade skills ("I teach guitar, I want to learn Python"). The platform covers the whole journey: sign up, list skills, find a partner, agree on a swap, schedule and run sessions, chat, and rate each other.

### Accounts & profile
- **Registration & login**: email + password, BCrypt-hashed, returns a JWT (valid 24 hours, configurable)
- **Profile**: name, bio and city, with an average rating and rating count shown on the profile
- **Change password**: requires the current password; new password must be at least 6 characters and differ from the current one

### Skills
- **Skill catalog**: master list of skills grouped by category (MUSIC, TECH, COOKING, LANGUAGE, FITNESS, ART, OTHER). The catalog is seeded with 23 starter skills on first startup, and any logged-in user can add more
- **My skills**: each user lists skills they can **TEACH** and skills they want to **LEARN**, each with a level (BEGINNER, INTERMEDIATE, ADVANCED). The same skill can't be added twice with the same type
- **Availability calendar**: weekly recurring slots (day of week, start time, end time), visible on any user's public profile

### Discovery
- **Skill matching**: finds users who teach what you want to learn and/or want to learn what you teach. Results show exactly which skills overlap in each direction, and **mutual matches** (both directions) are ranked first
- **Mutual-only view** and a **single-user compatibility check**
- **User search & filters**: by skill name (partial, case-insensitive), city, skill level, minimum average rating, and "has availability". All filters are optional and combinable, and you never appear in your own results
- **Privacy**: other users' email addresses are not returned from search and match results

### Skill exchanges
- **Swap requests**: offer one of your TEACH skills in return for one of theirs, with an optional message. The server checks that you really teach the offered skill and they really teach the wanted one
- **Accept / reject** by the receiver; **complete** by either participant once accepted
- **Inbox views**: all, sent, received, and by id. Only the two participants can open an exchange

### Sessions
- **Scheduling**: once an exchange is accepted, either participant schedules one or more sessions with date/time, duration, mode (**ONLINE** with a meeting link, or **OFFLINE** with a location) and notes
- **Complete / cancel**: either participant completes a scheduled session; only the person who scheduled it can cancel. Finished or cancelled sessions can't be changed again
- **Upcoming sessions**: one call returns everything coming up across all your exchanges
- **Rescheduling**: either participant proposes a new time with an optional reason. Only one proposal can be pending per session, and only the *other* participant can accept (the session's time updates) or reject it. Full proposal history is kept

### Ratings & reputation
- **Rate your partner** 1-5 stars with an optional written review, once a session is completed. One rating per person per session
- **Per-user reviews list**, **rating summary** (average, total, and a 1-star to 5-star breakdown), and a "reviews I've received" view
- Ratings feed back into profiles and the minimum-rating search filter

### Messaging
- **Real-time chat**: STOMP over WebSocket, authenticated with the same JWT; messages are delivered instantly to both sender and receiver
- **Persisted history** over REST: inbox with last message and unread count per conversation, full thread per user, and a total unread count for badges
- Opening a thread marks that person's messages as read

### Platform
- **Stateless JWT security** on every endpoint except register/login
- **Consistent error format** with clear messages (validation errors list each failing field)
- **Dockerised**: multi-stage `Dockerfile` and a `docker-compose.yml` that starts the API with PostgreSQL
- **Unit-tested** services (JUnit 5 + Mockito) and JWT security components

## Mobile App

SkillSwap has a companion mobile app that talks to this API. You can download the app, or clone the source and build it yourself:

- **Source code**: [github.com/solasas/skillswap-mobile-app](https://github.com/solasas/skillswap-mobile-app)

```bash
git clone https://github.com/solasas/skillswap-mobile-app.git
```

Start this backend first (see below) so the app has an API to connect to.

## Tech Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Spring Security 6.x** (JWT Authentication)
- **Spring Data JPA**
- **Spring WebSocket** (STOMP messaging, for real-time chat)
- **PostgreSQL**
- **Maven**
- **Lombok**
- **jjwt 0.11.5** (JWT Token Provider)

## Database Configuration

Defaults (override with the environment variables shown):

```properties
Database: skillswap_db     # DB_NAME
Username: postgres         # DB_USER
Password: password         # DB_PASSWORD
Host:     localhost        # DB_HOST
Port:     5432             # DB_PORT
```

Tables are created automatically on startup (`spring.jpa.hibernate.ddl-auto=update`).

## Architecture

SkillSwap is a layered Spring Boot monolith exposing a REST API and a WebSocket (STOMP) channel, backed by a single PostgreSQL database. The mobile app (and any other client) talks only to these two interfaces.

```
        Mobile app / web client
          |                  |
   HTTPS + JSON         WebSocket (STOMP)
   /api/**              /ws  ->  /app/chat.send
          |                  |  <-  /user/queue/messages
          v                  v
  +---------------------------------------------+
  |  Security layer                             |
  |  - JwtAuthenticationFilter  (REST)          |
  |  - JwtChannelInterceptor    (STOMP CONNECT) |
  |  - CORS, stateless sessions, BCrypt         |
  +---------------------------------------------+
  |  Controllers   (REST + WebSocket)           |
  |  validate DTOs, read userId from the JWT    |
  +---------------------------------------------+
  |  Services      (business rules)             |
  |  interface + impl, @Autowired               |
  +---------------------------------------------+
  |  Repositories  (Spring Data JPA)            |
  +---------------------------------------------+
  |  PostgreSQL                                 |
  +---------------------------------------------+
```

### Layers
| Layer | Package | Responsibility |
|---|---|---|
| **Security** | `security`, `config` | Validates the JWT, puts the user's id into the request, configures CORS, password hashing and WebSocket authentication |
| **Controllers** | `controller` | Thin HTTP/STOMP adapters: bind and validate request DTOs, call one service, return a response DTO with the right status code |
| **Services** | `service`, `service.impl` | All business rules: ownership and participant checks, status transitions, matching, search, rating aggregation |
| **Repositories** | `repository` | Spring Data JPA interfaces and derived/custom queries |
| **Entities** | `entity`, `enums` | JPA model and the status/type enumerations |
| **DTOs & mapping** | `dto`, `util.DtoMapper` | Request/response objects, so entities never leak into the API (passwords included) |
| **Error handling** | `exception` | Custom exceptions translated into one JSON error shape by a global `@RestControllerAdvice` |

### Request flow (REST)
1. Client sends `Authorization: Bearer <jwt>`.
2. `JwtAuthenticationFilter` validates the token and stores `userId` and `email` on the request.
3. The controller receives `@RequestAttribute("userId")`, so a client can never act as another user by passing an id.
4. The service enforces the rules (for example "only the receiver can accept", "exchange must be ACCEPTED to schedule") and throws a typed exception on violation.
5. `GlobalExceptionHandler` maps exceptions to `400/404/409/500` with the standard error body.

### Real-time chat flow
1. Client opens a WebSocket to `/ws` and sends the JWT in the STOMP `CONNECT` headers; `JwtChannelInterceptor` authenticates it.
2. Client publishes `{receiverId, content}` to `/app/chat.send`.
3. `ChatWebSocketController` persists the message through the same `MessageService` used by `POST /api/messages`.
4. The saved message is pushed to `/user/queue/messages` of both the sender and the receiver; failures go to `/user/queue/errors`.
5. History and unread counts are always read from the database over REST, so a reconnecting client loses nothing.

### Data model
```
User ---< UserSkill >--- Skill              (teach/learn + level)
User ---< Availability                      (weekly slots)

User (requester) --+
User (receiver) ---+--< SkillExchange >-- Skill (offered), Skill (wanted)
                           |
                           +--< Session ---< RescheduleRequest
                                    |
                                    +--< Rating   (ratedBy -> ratedTo)

User (sender) --+
User (receiver)-+--< Message                (content, read flag)
```

### Core business flow
```
Register -> add skills + availability -> find matches / search
   -> POST exchange (PENDING) -> receiver accepts (ACCEPTED)
   -> schedule session (SCHEDULED) <-> reschedule proposals
   -> complete session (COMPLETED) -> both rate -> complete exchange (COMPLETED)
                     (chat available at any point)
```

### Design decisions
- **Stateless**: no server-side session; the JWT carries identity, so the API scales horizontally.
- **One source of truth for messaging**: REST and WebSocket share `MessageService`.
- **Ownership by construction**: acting user always comes from the token, never from the request body.
- **Existence is not leaked**: reading an exchange or session you are not part of returns 404.
- **Configuration through environment**: database, port and JWT settings are overridable (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `PORT`, `JWT_SECRET`), which is what Docker uses.

### Deployment
`Dockerfile` builds the jar in a JDK 17 stage and runs it on a JRE 17 image as a non-root user. `docker-compose.yml` starts `postgres:16` (with a health check and a persistent volume) and the API, which waits for the database to be healthy.

## Project Structure

```
com.sashank.skillswap
├── config/                 # Spring configuration classes
├── controller/            # REST API endpoints
├── dto/
│   ├── request/          # Request DTOs
│   └── response/         # Response DTOs
├── entity/               # JPA entities
├── enums/                # Enumerations
├── exception/            # Custom exceptions & global handler
├── repository/           # Data access layer
├── security/             # JWT & Security related
├── service/              # Business logic (interfaces)
│   └── impl/             # Service implementations
└── util/                 # Utility classes (DTOMapper)
```

## Key Entities

### User
- Basic user information (name, email, password)
- Profile data (bio, city)
- Role (USER, ADMIN)

### Skill
- Master list of all available skills
- Category, name, description

### UserSkill
- Associates users with skills (TEACH or LEARN)
- Proficiency level (BEGINNER, INTERMEDIATE, ADVANCED)

### SkillExchange
- Exchange request between two users
- Offered skill (what requester teaches)
- Wanted skill (what requester wants to learn)
- Status (PENDING, ACCEPTED, REJECTED, COMPLETED)

### Session
- Individual teaching session within an exchange
- DateTime, duration, mode (ONLINE/OFFLINE)
- Meeting link or location

### Rating
- User rating after session completion
- 1-5 star rating with optional review

### Message
- Direct message between two users (sender, receiver, content, read flag)
- Persisted for both the REST history endpoints and the WebSocket chat

### Availability
- A user's recurring weekly time slot (day of week, start time, end time)
- Self-managed; publicly viewable on another user's profile and used by the `availableOnly` search filter

### RescheduleRequest
- A proposal to move a session to a new date/time
- Requester, proposed date/time, optional reason, status (PENDING, ACCEPTED, REJECTED)
- Only one PENDING request allowed per session at a time; only the *other* exchange participant can accept/reject it

## Enums

- **SkillLevel**: BEGINNER, INTERMEDIATE, ADVANCED
- **SkillCategory**: MUSIC, TECH, COOKING, LANGUAGE, FITNESS, ART, OTHER
- **SkillType**: TEACH, LEARN
- **ExchangeStatus**: PENDING, ACCEPTED, REJECTED, COMPLETED
- **SessionStatus**: SCHEDULED, COMPLETED, CANCELLED
- **SessionMode**: ONLINE, OFFLINE
- **UserRole**: USER, ADMIN
- **RescheduleStatus**: PENDING, ACCEPTED, REJECTED
- **DayOfWeek** (availability): MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY (Java's standard `java.time.DayOfWeek`, not a custom enum)

## API Endpoints

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - User login

### Profile
- `GET /api/profile/me` - Get current user profile
- `PUT /api/profile/me` - Update profile
- `PUT /api/profile/me/password` - Change password (requires current password)
- `POST /api/profile/skills` - Add a skill
- `GET /api/profile/skills` - Get user skills
- `DELETE /api/profile/skills/{userSkillId}` - Remove a skill
- `POST /api/profile/availability` - Add a weekly availability slot
- `GET /api/profile/availability` - Get my availability slots
- `DELETE /api/profile/availability/{availabilityId}` - Remove an availability slot

### Users
- `GET /api/users/search?skillName=&city=&level=&minRating=&availableOnly=` - Search/filter users (all params optional and combinable; excludes yourself)
- `GET /api/users/{userId}/availability` - Get another user's public availability

### Skills
- `GET /api/skills` - Get all skills
- `GET /api/skills?category=MUSIC` - Filter by category
- `GET /api/skills/categories` - Get all categories
- `POST /api/skills` - Create a new skill

### Matching
- `GET /api/matches` - Get all matching users
- `GET /api/matches/mutual` - Get mutual matches only
- `GET /api/matches/{userId}` - Check if specific user matches

### Skill Exchanges
- `POST /api/exchanges` - Create exchange request
- `GET /api/exchanges` - Get all exchanges (sent & received)
- `GET /api/exchanges/sent` - Get sent exchange requests
- `GET /api/exchanges/received` - Get received exchange requests
- `GET /api/exchanges/{id}` - Get exchange details
- `PUT /api/exchanges/{id}/accept` - Accept exchange
- `PUT /api/exchanges/{id}/reject` - Reject exchange
- `PUT /api/exchanges/{id}/complete` - Mark as completed

### Sessions
- `POST /api/exchanges/{exchangeId}/sessions` - Schedule a session
- `GET /api/exchanges/{exchangeId}/sessions` - Get sessions in exchange
- `GET /api/sessions/my-sessions` - Get upcoming sessions
- `GET /api/sessions/{id}` - Get session details
- `PUT /api/sessions/{id}/complete` - Complete session
- `PUT /api/sessions/{id}/cancel` - Cancel session
- `POST /api/sessions/{id}/reschedule` - Propose a new date/time `{proposedDateTime, reason?}`
- `GET /api/sessions/{id}/reschedule-requests` - Get reschedule history for a session
- `PUT /api/sessions/{id}/reschedule-requests/{requestId}/accept` - Accept a proposal (updates the session's dateTime)
- `PUT /api/sessions/{id}/reschedule-requests/{requestId}/reject` - Reject a proposal

### Ratings
- `POST /api/sessions/{sessionId}/ratings` - Rate a session
- `GET /api/users/{userId}/ratings` - Get user's ratings
- `GET /api/users/{userId}/rating-summary` - Get rating summary
- `GET /api/profile/me/ratings` - Get my received ratings

### Messages (REST - history)
- `POST /api/messages` - Send a message `{receiverId, content}`
- `GET /api/messages/conversations` - Inbox: last message + unread count per conversation
- `GET /api/messages/conversations/{otherUserId}` - Full message thread with a user (marks their messages as read)
- `GET /api/messages/unread-count` - Total unread message count

### Chat (WebSocket - real-time)
- Connect: `ws://localhost:8080/ws` using a STOMP client, with the JWT sent as a STOMP CONNECT header: `Authorization: Bearer <token>`
  (native browser `WebSocket` can't set this header on the handshake, so use a STOMP client library such as `@stomp/stompjs` that supports `connectHeaders`)
- Send a message: publish to `/app/chat.send` with body `{"receiverId": 2, "content": "Hi!"}`
- Receive messages: subscribe to `/user/queue/messages` (delivered to both sender and receiver)
- Receive errors: subscribe to `/user/queue/errors`
- Every message sent over the socket is persisted the same way as `POST /api/messages`, so `/api/messages/conversations/{otherUserId}` always reflects full history

## Security

- JWT token-based authentication, signed with `jwt.secret` (**change it in production**)
- All endpoints except `/api/auth/**` (and the `/ws` handshake, which authenticates at STOMP `CONNECT`) require a token
- Token format: `Authorization: Bearer <token>`
- Token expiration: 24 hours (configurable); there is no refresh endpoint, so the client logs in again
- Passwords are stored as BCrypt hashes
- Sessions are stateless and CSRF is disabled (token-based API)
- CORS is limited to `http://localhost:3000` and `http://localhost:8080`; add your own origin in `SecurityConfig` and `WebSocketConfig` if the client runs elsewhere
- Participant checks: exchanges, sessions and reschedule requests can only be read or changed by the people involved

## Setup Instructions

### Quick start with Docker

```bash
docker compose up --build
```

The API is available at `http://localhost:8080` and PostgreSQL is exposed on host port `5434`.

### Manual setup

### 1. Create PostgreSQL Database

```sql
CREATE DATABASE skillswap_db;
```

### 2. Install Dependencies

```bash
mvn clean install -DskipTests
```

### 3. Run the Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### 4. Refresh IDE Dependencies

If using IntelliJ IDEA:
- Right-click on `pom.xml` → Maven → Reload Project

## Configuration

Edit `src/main/resources/application.properties` to customize:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/skillswap_db
spring.datasource.username=postgres
spring.datasource.password=password

# JWT
jwt.secret=your-secret-key-change-this-in-production
jwt.expiration=86400000

# Server
server.port=8080
```

## Sample Usage

### 1. Register User
```bash
POST /api/auth/register
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "password123"
}
```

### 2. Login
```bash
POST /api/auth/login
{
  "email": "john@example.com",
  "password": "password123"
}
```

### 3. Add Skills to Profile
```bash
POST /api/profile/skills
Authorization: Bearer <token>
{
  "skillId": 1,
  "type": "TEACH",
  "level": "ADVANCED"
}
```

### 4. Create Skill Exchange
```bash
POST /api/exchanges
Authorization: Bearer <token>
{
  "receiverId": 2,
  "offeredSkillId": 1,
  "wantedSkillId": 3,
  "message": "I'd like to trade"
}
```

## Error Handling

All errors follow a consistent format:

```json
{
  "timestamp": "2024-04-05T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Error details",
  "path": "/api/endpoint"
}
```

HTTP Status Codes:
- `200` - OK
- `201` - Created
- `204` - No Content
- `400` - Bad Request (validation errors and business-rule violations, including wrong login credentials)
- `401` / `403` - Missing, invalid or expired token on a protected route
- `404` - Not found, or the exchange/session belongs to other users
- `409` - Conflict (email already registered, skill already on your list)

## Validation Rules

- Email must be unique and valid format
- Password must be at least 6 characters (register and change password)
- Cannot send exchange to yourself
- Can only rate completed sessions
- Cannot rate same session twice
- Cannot accept/reject non-pending exchanges
- You must teach the skill you offer, and the receiver must teach the skill you want
- A skill can only be added once per type (TEACH / LEARN) per user
- Only exchange participants can read an exchange or its sessions
- Only the receiver can accept or reject an exchange
- Only ACCEPTED exchanges can be completed or have sessions scheduled
- Only exchange participants can schedule sessions
- Only SCHEDULED sessions can be completed, cancelled or rescheduled
- Only the person who scheduled a session can cancel it
- Availability slot start time must be before its end time
- Only exchange participants can propose a session reschedule, and only on a SCHEDULED session
- Only one PENDING reschedule request allowed per session at a time
- Only the *other* exchange participant can accept/reject a reschedule request (not the one who proposed it)
- Cannot message yourself
- New password must differ from the current password, and must be at least 6 characters

## Development Notes

- JPA entities use Lombok for getters/setters
- DTOs use builder pattern for object creation
- Services use dependency injection via @Autowired
- JWT tokens extracted from Authorization header
- User context available via @RequestAttribute("userId")

## Future Enhancements

- Email notifications
- Skill reviews/testimonials
- Skills endorsements
- Advanced matching algorithm
- Payment integration
- Activity feed (auto-generated: exchanges accepted, sessions completed, ratings received)

## License

This project is for educational purposes.

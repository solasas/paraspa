# paraspa - Community Skill Exchange Platform

A complete REST API backend built with Spring Boot for a community skill exchange platform where users can trade skills for free.

## Overview

paraspa is a platform that enables users to:
- List skills they can teach and skills they want to learn
- Find matches with other users for mutual skill exchange
- Schedule sessions for teaching/learning
- Track progress and rate each other

## Tech Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Spring Data JPA**
- **Spring Security 6.x**
- **PostgreSQL**
- **JWT Authentication (jjwt 0.11.5)**
- **Lombok**
- **Maven**

## Project Structure

```
paraspa/
├── src/main/java/com/sashank/paraspa/
│   ├── paraspaApplication.java
│   ├── config/
│   │   └── SecurityConfig.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── ProfileController.java
│   │   ├── SkillController.java
│   │   ├── MatchController.java
│   │   ├── ExchangeController.java
│   │   ├── SessionController.java
│   │   └── RatingController.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   ├── UpdateProfileRequest.java
│   │   │   ├── AddUserSkillRequest.java
│   │   │   ├── CreateSkillRequest.java
│   │   │   ├── CreateExchangeRequest.java
│   │   │   ├── CreateSessionRequest.java
│   │   │   └── CreateRatingRequest.java
│   │   └── response/
│   │       ├── AuthResponse.java
│   │       ├── UserResponse.java
│   │       ├── SkillResponse.java
│   │       ├── UserSkillResponse.java
│   │       ├── ProfileResponse.java
│   │       ├── MatchResponse.java
│   │       ├── ExchangeResponse.java
│   │       ├── SessionResponse.java
│   │       ├── RatingResponse.java
│   │       ├── RatingSummaryResponse.java
│   │       └── ErrorResponse.java
│   ├── entity/
│   │   ├── User.java
│   │   ├── Skill.java
│   │   ├── UserSkill.java
│   │   ├── SkillExchange.java
│   │   ├── Session.java
│   │   └── Rating.java
│   ├── enums/
│   │   ├── UserRole.java
│   │   ├── SkillLevel.java
│   │   ├── SkillCategory.java
│   │   ├── SkillType.java
│   │   ├── ExchangeStatus.java
│   │   ├── SessionStatus.java
│   │   └── SessionMode.java
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── ResourceNotFoundException.java
│   │   ├── BadRequestException.java
│   │   ├── UnauthorizedException.java
│   │   └── AlreadyExistsException.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── SkillRepository.java
│   │   ├── UserSkillRepository.java
│   │   ├── SkillExchangeRepository.java
│   │   ├── SessionRepository.java
│   │   └── RatingRepository.java
│   ├── security/
│   │   ├── JwtTokenProvider.java
│   │   └── JwtAuthenticationFilter.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── UserService.java
│   │   ├── SkillService.java
│   │   ├── UserSkillService.java
│   │   ├── MatchService.java
│   │   ├── ExchangeService.java
│   │   ├── SessionService.java
│   │   ├── RatingService.java
│   │   └── impl/
│   │       ├── AuthServiceImpl.java
│   │       ├── UserServiceImpl.java
│   │       ├── SkillServiceImpl.java
│   │       ├── UserSkillServiceImpl.java
│   │       ├── MatchServiceImpl.java
│   │       ├── ExchangeServiceImpl.java
│   │       ├── SessionServiceImpl.java
│   │       └── RatingServiceImpl.java
│   └── util/
│       └── DtoMapper.java
├── src/main/resources/
│   ├── application.properties
│   └── init-data.sql
└── pom.xml
```

## Prerequisites

1. **Java 17+** installed
2. **PostgreSQL 12+** installed and running
3. **Maven 3.6+** installed

## Setup Instructions

### 1. Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE paraspa_db;
```

### 2. Application Configuration

Edit `src/main/resources/application.properties`:

```properties
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/paraspa_db
spring.datasource.username=postgres
spring.datasource.password=postgres

# JWT Configuration
jwt.secret=your-secret-key-change-this-in-production-use-a-long-secure-string-at-least-256-bits
jwt.expiration=86400000

# Server Configuration
server.port=8080
```

### 3. Build the Project

```bash
cd paraspa
mvn clean install -DskipTests
```

### 4. Run the Application

```bash
mvn spring-boot:run
```

Or using Java directly:

```bash
java -jar target/paraspa-0.0.1-SNAPSHOT.jar
```

The API will be available at `http://localhost:8080`

### 5. (Optional) Load Sample Data

After the application starts, the database tables will be created automatically. You can optionally load sample data:

```bash
psql -U postgres -d paraspa_db -f src/main/resources/init-data.sql
```

## API Endpoints

### Authentication (Public)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login and get JWT token |

### Profile (Protected)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/profile/me` | Get current user profile |
| PUT | `/api/profile/me` | Update user profile |
| GET | `/api/profile/skills` | Get user's skills |
| POST | `/api/profile/skills` | Add a skill to user |
| DELETE | `/api/profile/skills/{userSkillId}` | Remove a skill |

### Skills (Protected)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/skills` | Get all skills (with optional ?category filter) |
| GET | `/api/skills/categories` | Get all skill categories |
| POST | `/api/skills` | Create new skill |
| GET | `/api/skills/{id}` | Get skill by ID |

### Matching (Protected)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/matches` | Find all matches for current user |
| GET | `/api/matches/mutual` | Find mutual matches only |
| GET | `/api/matches/{userId}` | Check if specific user matches |

### Skill Exchange (Protected)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/exchanges` | Create exchange request |
| GET | `/api/exchanges` | Get all my exchanges |
| GET | `/api/exchanges/sent` | Get exchanges I sent |
| GET | `/api/exchanges/received` | Get exchanges I received |
| GET | `/api/exchanges/{id}` | Get exchange details |
| PUT | `/api/exchanges/{id}/accept` | Accept exchange |
| PUT | `/api/exchanges/{id}/reject` | Reject exchange |
| PUT | `/api/exchanges/{id}/complete` | Mark exchange as completed |

### Sessions (Protected)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/exchanges/{exchangeId}/sessions` | Schedule a session |
| GET | `/api/exchanges/{exchangeId}/sessions` | Get all sessions in exchange |
| GET | `/api/sessions/my-sessions` | Get my upcoming sessions |
| GET | `/api/sessions/{id}` | Get session details |
| PUT | `/api/sessions/{id}/complete` | Mark session as completed |
| PUT | `/api/sessions/{id}/cancel` | Cancel a session |

### Ratings (Protected)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/sessions/{sessionId}/ratings` | Rate a completed session |
| GET | `/api/users/{userId}/ratings` | Get all ratings of a user |
| GET | `/api/users/{userId}/rating-summary` | Get rating summary |
| GET | `/api/profile/me/ratings` | Get my received ratings |

## Authentication

All endpoints except `/api/auth/**` require a JWT token in the Authorization header:

```
Authorization: Bearer <your_jwt_token>
```

## Data Models

### User
- Unique email
- Password (BCrypt hashed)
- Bio and City (optional)
- Role (USER or ADMIN)
- Timestamps

### Skill
- Name (unique)
- Category (MUSIC, TECH, COOKING, LANGUAGE, FITNESS, ART, OTHER)
- Description (optional)

### UserSkill
- Links User to Skill
- Type (TEACH or LEARN)
- Level (BEGINNER, INTERMEDIATE, ADVANCED)

### SkillExchange
- Requester and Receiver users
- Offered and Wanted skills
- Status (PENDING, ACCEPTED, REJECTED, COMPLETED)
- Optional message

### Session
- Part of a SkillExchange
- Scheduled date/time
- Duration in minutes
- Mode (ONLINE or OFFLINE)
- Meet link (for online) or location (for offline)
- Status (SCHEDULED, COMPLETED, CANCELLED)

### Rating
- For a completed Session
- 1-5 stars
- Optional review
- Tracks who rated whom

## Security Features

- JWT token-based authentication
- BCrypt password hashing
- Request validation with Jakarta Validation
- Global exception handling with consistent error responses
- CORS support for frontend integration
- Stateless session management

## Error Handling

The API returns consistent error responses:

```json
{
  "timestamp": "2026-04-05T23:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Error message here",
  "path": "/api/endpoint"
}
```

### HTTP Status Codes

- **200** - OK (successful GET, PUT)
- **201** - Created (successful POST)
- **204** - No Content (successful DELETE)
- **400** - Bad Request (validation errors)
- **401** - Unauthorized (missing/invalid token)
- **403** - Forbidden (no permission)
- **404** - Not Found (resource not found)
- **409** - Conflict (already exists)
- **500** - Internal Server Error

## Example Usage

### 1. Register a User

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john@example.com",
    "password": "password123"
  }'
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "type": "Bearer",
  "id": 1,
  "name": "John Doe",
  "email": "john@example.com"
}
```

### 2. Get Current Profile

```bash
curl -X GET http://localhost:8080/api/profile/me \
  -H "Authorization: Bearer <your_token>"
```

### 3. Create a Skill Exchange

```bash
curl -X POST http://localhost:8080/api/exchanges \
  -H "Authorization: Bearer <your_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "receiverId": 2,
    "offeredSkillId": 1,
    "wantedSkillId": 3,
    "message": "Would love to learn from you!"
  }'
```

## Development

### Running Tests

```bash
mvn test
```

### Building Without Tests

```bash
mvn clean install -DskipTests
```

### IDE Configuration

The project has been configured for IntelliJ IDEA. If using another IDE:

1. Import as Maven project
2. Ensure JDK 17 is selected
3. Enable Lombok annotation processing in IDE settings

## Troubleshooting

### Compilation Issues with Lombok

Ensure your IDE has Lombok annotation processing enabled:
- **IntelliJ**: Settings → Build, Execution, Deployment → Compiler → Annotation Processors → Enable annotation processing
- **Eclipse**: Install Lombok plugin or run `java -jar lombok.jar`

### Database Connection Issues

Check that PostgreSQL is running and credentials are correct in `application.properties`.

### JWT Token Issues

Make sure tokens are sent in the correct format:
```
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

Not just the token alone.

## Future Enhancements

- Email verification for new users
- Notification system
- User search and filtering
- Skill recommendations using ML
- Payment integration for premium features
- Mobile app support
- Real-time chat between users

## License

This project is provided as-is for educational and community purposes.

## Support

For issues or questions, please refer to the project documentation or contact the development team.


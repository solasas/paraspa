# paraspa - Complete Project Summary

## 📦 Project Overview

**paraspa** is a complete REST API backend for a community skill exchange platform where people can trade skills for free. Built with Spring Boot 3.2.0, PostgreSQL, and JWT authentication.

### Tech Stack
- Java 17
- Spring Boot 3.2.0
- Spring Security 6.x
- Spring Data JPA
- PostgreSQL
- JWT (jjwt 0.11.5)
- Lombok
- Maven

---

## 📂 Project Structure

### Root Level Files
```
paraspa/
├── pom.xml                          # Maven configuration with all dependencies
├── README.md                        # Main documentation
├── QUICKSTART.md                    # Quick start guide (5 minutes)
├── SETUP.md                         # Detailed environment setup
├── API-TESTING.md                   # API testing guide with curl examples
├── PROJECT-STRUCTURE.md             # Code organization documentation
├── setup.sh                         # Automated setup script
└── src/
```

---

## 🏗️ Source Code Organization

### Configuration (`src/main/java/.../config/`)
- **SecurityConfig.java** - Spring Security configuration, JWT filters, CORS

### Controllers (`src/main/java/.../controller/`)
- **AuthController.java** - Register, Login endpoints
- **ProfileController.java** - User profile, Skills management
- **SkillController.java** - Skill CRUD, Categories
- **MatchController.java** - Find matches, Mutual matches
- **ExchangeController.java** - Create/manage skill exchanges
- **SessionController.java** - Schedule/manage teaching sessions
- **RatingController.java** - Rate users, View ratings

### DTOs Request (`src/main/java/.../dto/request/`)
- **RegisterRequest.java** - User registration
- **LoginRequest.java** - User login
- **UpdateProfileRequest.java** - Profile update
- **AddUserSkillRequest.java** - Add skill to profile
- **CreateSkillRequest.java** - Create new skill
- **CreateExchangeRequest.java** - Request skill exchange
- **CreateSessionRequest.java** - Schedule session
- **CreateRatingRequest.java** - Rate session

### DTOs Response (`src/main/java/.../dto/response/`)
- **AuthResponse.java** - Authentication response
- **UserResponse.java** - User information
- **SkillResponse.java** - Skill details
- **UserSkillResponse.java** - User's skill
- **ProfileResponse.java** - Complete user profile
- **MatchResponse.java** - Match with another user
- **ExchangeResponse.java** - Exchange request details
- **SessionResponse.java** - Session details
- **RatingResponse.java** - Rating details
- **RatingSummaryResponse.java** - User rating summary
- **ErrorResponse.java** - Error details

### Entities (`src/main/java/.../entity/`)
- **User.java** - User account entity
- **Skill.java** - Skill catalog entity
- **UserSkill.java** - User's skills (TEACH/LEARN)
- **SkillExchange.java** - Exchange request entity
- **Session.java** - Teaching session entity
- **Rating.java** - User rating entity

### Enums (`src/main/java/.../enums/`)
- **SkillLevel.java** - BEGINNER, INTERMEDIATE, ADVANCED
- **SkillCategory.java** - MUSIC, TECH, COOKING, LANGUAGE, FITNESS, ART, OTHER
- **SkillType.java** - TEACH, LEARN
- **ExchangeStatus.java** - PENDING, ACCEPTED, REJECTED, COMPLETED
- **SessionStatus.java** - SCHEDULED, COMPLETED, CANCELLED
- **SessionMode.java** - ONLINE, OFFLINE
- **UserRole.java** - USER, ADMIN

### Repositories (`src/main/java/.../repository/`)
- **UserRepository.java** - User data access
- **SkillRepository.java** - Skill data access
- **UserSkillRepository.java** - User skill data access
- **SkillExchangeRepository.java** - Exchange data access
- **SessionRepository.java** - Session data access
- **RatingRepository.java** - Rating data access

### Security (`src/main/java/.../security/`)
- **JwtTokenProvider.java** - JWT token generation and validation
- **JwtAuthenticationFilter.java** - Request authentication filter

### Services (`src/main/java/.../service/`)
**Interfaces:**
- **AuthService.java** - Authentication logic
- **UserService.java** - User management
- **SkillService.java** - Skill management
- **UserSkillService.java** - User skill management
- **MatchService.java** - Skill matching
- **ExchangeService.java** - Exchange management
- **SessionService.java** - Session management
- **RatingService.java** - Rating management

**Implementations (`service/impl/`):**
- **AuthServiceImpl.java** - Register, login, password hashing
- **UserServiceImpl.java** - Profile retrieval and updates
- **SkillServiceImpl.java** - Skill CRUD operations
- **UserSkillServiceImpl.java** - Add/remove user skills
- **MatchServiceImpl.java** - Find compatible users
- **ExchangeServiceImpl.java** - Create/accept/reject exchanges
- **SessionServiceImpl.java** - Schedule/manage sessions
- **RatingServiceImpl.java** - Rate users, calculate averages

### Utilities (`src/main/java/.../util/`)
- **DtoMapper.java** - Entity to DTO mapping utility

### Exception Handling (`src/main/java/.../exception/`)
- **ResourceNotFoundException.java** - 404 errors
- **UnauthorizedException.java** - 401 errors
- **BadRequestException.java** - 400 validation errors
- **AlreadyExistsException.java** - 409 conflict errors
- **GlobalExceptionHandler.java** - Centralized error handling

### Main Application
- **paraspaApplication.java** - Spring Boot entry point

### Resources (`src/main/resources/`)
- **application.properties** - Application configuration
- **init-data.sql** - Sample database initialization

---

## 🌐 API Endpoints (30+ Endpoints)

### Authentication (2)
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - User login

### Profile (3)
- `GET /api/profile/me` - Get current user profile
- `PUT /api/profile/me` - Update profile
- `GET /api/profile/me/ratings` - Get received ratings

### Skills (4)
- `GET /api/skills` - Get all skills
- `GET /api/skills?category=MUSIC` - Filter by category
- `GET /api/skills/categories` - Get all categories
- `POST /api/skills` - Create new skill

### User Skills (3)
- `POST /api/profile/skills` - Add skill to profile
- `GET /api/profile/skills` - Get user skills
- `DELETE /api/profile/skills/{id}` - Remove skill

### Matching (3)
- `GET /api/matches` - Find all matches
- `GET /api/matches/mutual` - Find mutual matches
- `GET /api/matches/{userId}` - Check specific match

### Exchanges (7)
- `POST /api/exchanges` - Create exchange request
- `GET /api/exchanges` - Get all exchanges
- `GET /api/exchanges/sent` - Get sent exchanges
- `GET /api/exchanges/received` - Get received exchanges
- `GET /api/exchanges/{id}` - Get exchange details
- `PUT /api/exchanges/{id}/accept` - Accept exchange
- `PUT /api/exchanges/{id}/reject` - Reject exchange
- `PUT /api/exchanges/{id}/complete` - Complete exchange

### Sessions (6)
- `POST /api/exchanges/{exchangeId}/sessions` - Schedule session
- `GET /api/exchanges/{exchangeId}/sessions` - Get sessions
- `GET /api/sessions/my-sessions` - Get upcoming sessions
- `GET /api/sessions/{id}` - Get session details
- `PUT /api/sessions/{id}/complete` - Complete session
- `PUT /api/sessions/{id}/cancel` - Cancel session

### Ratings (4)
- `POST /api/sessions/{sessionId}/ratings` - Rate session
- `GET /api/users/{userId}/ratings` - Get user ratings
- `GET /api/users/{userId}/rating-summary` - Get rating summary

---

## 🗄️ Database Schema

### Tables (6)
1. **users** - User accounts and profiles
2. **skills** - Master skill catalog
3. **user_skills** - User's teaching/learning skills
4. **skill_exchanges** - Exchange requests
5. **sessions** - Teaching sessions
6. **ratings** - User ratings and reviews

### Relationships
```
User (1) ─── (M) UserSkill
User (1) ─── (M) SkillExchange (as requester)
User (1) ─── (M) SkillExchange (as receiver)
User (1) ─── (M) Session
User (1) ─── (M) Rating (as rater)
User (1) ─── (M) Rating (as ratee)

Skill (1) ─── (M) UserSkill
Skill (1) ─── (M) SkillExchange (offered)
Skill (1) ─── (M) SkillExchange (wanted)

SkillExchange (1) ─── (M) Session
Session (1) ─── (M) Rating
```

---

## 🔐 Security Features

- JWT-based authentication (jjwt 0.11.5)
- BCrypt password hashing
- Spring Security 6.x configuration
- Request authentication filter
- CORS configuration
- Protected endpoints (all except /api/auth/**)
- Role-based access control (USER, ADMIN)
- Validation for all inputs
- SQL injection protection (parameterized queries)

---

## ✨ Key Features

### User Management
- User registration with email validation
- Secure login with JWT tokens
- Profile management (bio, city, location)
- Role-based system (USER, ADMIN)

### Skill System
- Master catalog of skills by category
- Users can add multiple skills
- Mark skills as TEACH or LEARN
- Proficiency levels (BEGINNER, INTERMEDIATE, ADVANCED)
- Skill categories (MUSIC, TECH, COOKING, LANGUAGE, FITNESS, ART, OTHER)

### Smart Matching
- Find users with complementary skills
- Identify mutual matches (both teach/learn each other's skills)
- Sort by mutual compatibility
- User-specific match details

### Skill Exchange
- Request skill exchanges with messages
- Accept/reject exchange requests
- Status tracking (PENDING, ACCEPTED, REJECTED, COMPLETED)
- Multiple sessions per exchange

### Session Management
- Schedule teaching sessions
- ONLINE (with meeting link) or OFFLINE (with location)
- Duration and notes tracking
- Session status management (SCHEDULED, COMPLETED, CANCELLED)

### Rating System
- 1-5 star ratings after session completion
- Optional review text
- Prevent duplicate ratings
- Rating statistics (average, breakdown by stars)
- User reputation system

---

## 📋 Validation Rules

- Email must be unique and valid format
- Password validation required
- Cannot send exchange to yourself
- Can only rate COMPLETED sessions
- Cannot rate same session twice
- Cannot accept/reject non-PENDING exchanges
- Only exchange participants can schedule sessions
- Only session scheduler can cancel
- Stars rating: 1-5 range

---

## 📚 Documentation Files

1. **README.md** - Complete feature overview and API reference
2. **QUICKSTART.md** - Get started in 5 minutes
3. **SETUP.md** - Detailed environment setup instructions
4. **API-TESTING.md** - Comprehensive curl examples for all endpoints
5. **PROJECT-STRUCTURE.md** - Code organization and architecture
6. **HELP.md** - Additional help and resources
7. **init-data.sql** - Sample data for testing

---

## 🚀 Getting Started

### Quick Start (5 minutes)
```bash
# Create database
psql -U postgres -c "CREATE DATABASE paraspa_db;"

# Build project
mvn clean install -DskipTests

# Run application
mvn spring-boot:run

# Test
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","email":"test@example.com","password":"pass123"}'
```

See **QUICKSTART.md** for detailed steps.

---

## 📊 Project Statistics

- **Total Files**: 60+
- **Java Classes**: 60+
- **Lines of Code**: 5000+
- **Endpoints**: 30+
- **Database Tables**: 6
- **DTOs**: 18
- **Services**: 8
- **Repositories**: 6
- **Controllers**: 7
- **Enums**: 7

---

## 🔧 Technology Breakdown

| Component | Technology |
|-----------|-----------|
| **Framework** | Spring Boot 3.2.0 |
| **Web** | Spring Web MVC |
| **Security** | Spring Security 6.x |
| **Database** | PostgreSQL + Spring Data JPA |
| **Authentication** | JWT (jjwt 0.11.5) |
| **Serialization** | Jackson |
| **Validation** | Spring Validation |
| **ORM** | Hibernate (JPA) |
| **Build Tool** | Maven 3.8+ |
| **Language** | Java 17 |
| **Utilities** | Lombok |

---

## 📝 Configuration

### Environment Variables (Optional)
```bash
JWT_SECRET=your-secret-key
JWT_EXPIRATION=86400000
DB_USERNAME=postgres
DB_PASSWORD=postgres
DB_URL=jdbc:postgresql://localhost:5432/paraspa_db
SERVER_PORT=8080
```

### Default Configuration
```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/paraspa_db
spring.datasource.username=postgres
spring.datasource.password=postgres

# JWT
jwt.secret=your-secret-key-change-this-in-production
jwt.expiration=86400000

# Server
server.port=8080

# Logging
logging.level.root=INFO
logging.level.com.sashank.paraspa=DEBUG
```

---

## 🧪 Testing

### Unit Testing
- Test services with mocked repositories
- Test validation logic
- Test business rules

### Integration Testing
- Test API endpoints
- Test database operations
- Test JWT authentication

See **API-TESTING.md** for curl examples.

---

## 🔄 Development Workflow

1. **Setup**: Run `mvn clean install -DskipTests`
2. **Develop**: Modify code in src/main/java
3. **Build**: Run `mvn clean package`
4. **Test**: Run API tests using curl commands
5. **Deploy**: Package as JAR and run on server

---

## 🎯 Future Enhancements

- Email notifications for exchanges
- Skill endorsements/testimonials
- Session rescheduling
- Advanced user search/filtering
- Real-time chat during sessions
- Payment integration
- Mobile app support
- Social features (follow, messaging)

---

## 📜 License

Educational Project - Free to use and modify

---

## ✅ Quality Checklist

- ✅ All endpoints implemented
- ✅ Input validation on all inputs
- ✅ Error handling with proper HTTP status codes
- ✅ JWT authentication on protected routes
- ✅ Database relationships properly modeled
- ✅ DTOs for all request/responses
- ✅ Service layer for business logic
- ✅ Repository pattern for data access
- ✅ Centralized exception handling
- ✅ Code organized by package
- ✅ Comprehensive documentation
- ✅ Sample data initialization script
- ✅ Configuration management
- ✅ CORS enabled for frontend integration
- ✅ Lombok for cleaner code

---

## 🎓 Learning Resources

- Start with: **QUICKSTART.md**
- Read: **README.md** for full documentation
- Explore: **PROJECT-STRUCTURE.md** for code organization
- Test: **API-TESTING.md** for endpoint examples
- Setup: **SETUP.md** for environment configuration

---

**paraspa is ready to use! Start the application and begin exchanging skills! 🚀**


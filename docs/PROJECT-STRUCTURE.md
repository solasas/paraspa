# Project Structure and File Organization

## Complete Directory Tree

```
paraspa/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/sashank/paraspa/
│   │   │       ├── paraspaApplication.java          # Main Spring Boot application
│   │   │       ├── config/
│   │   │       │   └── SecurityConfig.java            # Spring Security configuration
│   │   │       ├── controller/                        # REST API endpoints
│   │   │       │   ├── AuthController.java            # Authentication endpoints
│   │   │       │   ├── ProfileController.java         # User profile management
│   │   │       │   ├── SkillController.java           # Skill endpoints
│   │   │       │   ├── MatchController.java           # Skill matching
│   │   │       │   ├── ExchangeController.java        # Skill exchanges
│   │   │       │   ├── SessionController.java         # Teaching sessions
│   │   │       │   └── RatingController.java          # Ratings and reviews
│   │   │       ├── dto/
│   │   │       │   ├── request/                       # Request DTOs
│   │   │       │   │   ├── RegisterRequest.java
│   │   │       │   │   ├── LoginRequest.java
│   │   │       │   │   ├── UpdateProfileRequest.java
│   │   │       │   │   ├── AddUserSkillRequest.java
│   │   │       │   │   ├── CreateSkillRequest.java
│   │   │       │   │   ├── CreateExchangeRequest.java
│   │   │       │   │   ├── CreateSessionRequest.java
│   │   │       │   │   └── CreateRatingRequest.java
│   │   │       │   └── response/                      # Response DTOs
│   │   │       │       ├── AuthResponse.java
│   │   │       │       ├── UserResponse.java
│   │   │       │       ├── SkillResponse.java
│   │   │       │       ├── UserSkillResponse.java
│   │   │       │       ├── ProfileResponse.java
│   │   │       │       ├── MatchResponse.java
│   │   │       │       ├── ExchangeResponse.java
│   │   │       │       ├── SessionResponse.java
│   │   │       │       ├── RatingResponse.java
│   │   │       │       ├── RatingSummaryResponse.java
│   │   │       │       └── ErrorResponse.java
│   │   │       ├── entity/                            # JPA entities
│   │   │       │   ├── User.java
│   │   │       │   ├── Skill.java
│   │   │       │   ├── UserSkill.java
│   │   │       │   ├── SkillExchange.java
│   │   │       │   ├── Session.java
│   │   │       │   └── Rating.java
│   │   │       ├── enums/                             # Enumerations
│   │   │       │   ├── SkillLevel.java
│   │   │       │   ├── SkillCategory.java
│   │   │       │   ├── SkillType.java
│   │   │       │   ├── ExchangeStatus.java
│   │   │       │   ├── SessionStatus.java
│   │   │       │   ├── SessionMode.java
│   │   │       │   └── UserRole.java
│   │   │       ├── exception/                         # Exception handling
│   │   │       │   ├── ResourceNotFoundException.java
│   │   │       │   ├── UnauthorizedException.java
│   │   │       │   ├── BadRequestException.java
│   │   │       │   ├── AlreadyExistsException.java
│   │   │       │   └── GlobalExceptionHandler.java
│   │   │       ├── repository/                        # Data access layer
│   │   │       │   ├── UserRepository.java
│   │   │       │   ├── SkillRepository.java
│   │   │       │   ├── UserSkillRepository.java
│   │   │       │   ├── SkillExchangeRepository.java
│   │   │       │   ├── SessionRepository.java
│   │   │       │   └── RatingRepository.java
│   │   │       ├── security/                          # JWT & security
│   │   │       │   ├── JwtTokenProvider.java          # JWT token generation/validation
│   │   │       │   └── JwtAuthenticationFilter.java   # JWT authentication filter
│   │   │       ├── service/                           # Business logic interfaces
│   │   │       │   ├── AuthService.java
│   │   │       │   ├── UserService.java
│   │   │       │   ├── SkillService.java
│   │   │       │   ├── UserSkillService.java
│   │   │       │   ├── MatchService.java
│   │   │       │   ├── ExchangeService.java
│   │   │       │   ├── SessionService.java
│   │   │       │   ├── RatingService.java
│   │   │       │   └── impl/                          # Service implementations
│   │   │       │       ├── AuthServiceImpl.java
│   │   │       │       ├── UserServiceImpl.java
│   │   │       │       ├── SkillServiceImpl.java
│   │   │       │       ├── UserSkillServiceImpl.java
│   │   │       │       ├── MatchServiceImpl.java
│   │   │       │       ├── ExchangeServiceImpl.java
│   │   │       │       ├── SessionServiceImpl.java
│   │   │       │       └── RatingServiceImpl.java
│   │   │       └── util/                              # Utilities
│   │   │           └── DtoMapper.java                 # DTO mapping utility
│   │   └── resources/
│   │       ├── application.properties                 # Application configuration
│   │       └── init-data.sql                          # Sample data script
│   └── test/
│       └── java/
│           └── com/sashank/paraspa/
│               └── paraspaApplicationTests.java
├── pom.xml                                            # Maven configuration
├── README.md                                          # Main documentation
├── SETUP.md                                           # Environment setup guide
├── API-TESTING.md                                     # API testing guide
├── PROJECT-STRUCTURE.md                               # This file
└── setup.sh                                           # Setup script
```

## Key Files Description

### Configuration Files
- **pom.xml**: Maven build configuration with all dependencies
- **application.properties**: Spring Boot application configuration
- **SecurityConfig.java**: Spring Security and JWT configuration

### Core Business Logic
- **Services** (service/): Contain business logic for each feature
- **Repositories** (repository/): Database query interfaces
- **Entities** (entity/): JPA entity mappings for database tables

### API Layer
- **Controllers** (controller/): REST API endpoints
- **DTOs** (dto/): Data transfer objects for requests/responses
- **GlobalExceptionHandler**: Centralized exception handling

### Security
- **JwtTokenProvider**: JWT token generation and validation
- **JwtAuthenticationFilter**: Request authentication filter
- **SecurityConfig**: Spring Security configuration

### Data Access
- **DtoMapper**: Utility to convert entities to DTOs
- **Repositories**: Spring Data JPA interfaces for database operations

## Database Schema

### Tables Created by JPA

1. **users** - User accounts
   - id (PK), name, email (UNIQUE), password, bio, city, role, createdAt, updatedAt

2. **skills** - Master skill list
   - id (PK), name (UNIQUE), category, description, createdAt

3. **user_skills** - User's teaching/learning skills
   - id (PK), user_id (FK), skill_id (FK), type, level, createdAt

4. **skill_exchanges** - Exchange requests
   - id (PK), requester_id (FK), receiver_id (FK), offered_skill_id (FK), wanted_skill_id (FK), status, message, createdAt, updatedAt

5. **sessions** - Teaching sessions
   - id (PK), exchange_id (FK), scheduled_by_id (FK), dateTime, durationMinutes, mode, meetLink, location, notes, status, createdAt, updatedAt

6. **ratings** - Session ratings
   - id (PK), session_id (FK), rated_by_id (FK), rated_to_id (FK), stars, review, createdAt

## Dependency Hierarchy

```
Spring Boot 3.2.0
├── Spring Web
├── Spring Data JPA
├── Spring Security
│   └── JWT (jjwt 0.11.5)
├── PostgreSQL Driver
├── Lombok
└── Validation
```

## Package Organization Principles

1. **config/**: All Spring configuration beans
2. **entity/**: JPA mapped classes (database models)
3. **dto/**: Data transfer objects separated into request/response
4. **repository/**: Spring Data JPA interfaces
5. **service/**: Business logic interfaces and implementations
6. **controller/**: REST API endpoints
7. **exception/**: Custom exceptions and global handler
8. **security/**: JWT and authentication components
9. **util/**: Helper utilities (mappers, constants, etc.)
10. **enums/**: Enumeration classes

## Class Naming Conventions

- **Entities**: Singular noun (User, Skill, Session)
- **Repositories**: EntityRepository (UserRepository)
- **Services**: EntityService (UserService), EntityServiceImpl
- **Controllers**: EntityController (UserController)
- **Exceptions**: DescriptiveException (ResourceNotFoundException)
- **DTOs**: OperationDescriptorDto (CreateUserRequest, UserResponse)
- **Enums**: SingularEnum (SkillLevel, SessionStatus)

## Dependency Injection Flow

```
Controller
  └── @Autowired Service
        ├── @Autowired Repository
        └── @Autowired Other Services
```

## Request/Response Flow

```
HTTP Request
  └── Controller.method(DTO)
        └── Service.method(data)
              └── Repository.query()
                    └── Database
              └── Return Entity
        └── DtoMapper.toDTO(Entity)
              └── Return DTO
        └── ResponseEntity<DTO>
              └── HTTP Response (JSON)
```

## Security Flow

```
HTTP Request (with Authorization header)
  └── JwtAuthenticationFilter
        └── JwtTokenProvider.validateToken()
              └── Extract userId/email
              └── Set in RequestAttribute
        └── DispatcherServlet
              └── Controller
                    └── SecurityConfig checks if path allowed
                          └── Process request
              └── Response
  └── HTTP Response
```

## File Statistics

- Total Java Classes: 60+
- Controllers: 7
- Services: 8 (16 including impls)
- Repositories: 6
- Entities: 6
- DTOs: 18 (8 request + 10 response)
- Enums: 7
- Total Lines of Code: ~5000+

## How to Add a New Feature

1. **Create Entity** in entity/ package
2. **Create Repository** in repository/ package
3. **Create Request/Response DTOs** in dto/
4. **Create Service Interface** in service/
5. **Create Service Implementation** in service/impl/
6. **Add Mapping** in DtoMapper.java
7. **Create Controller** in controller/
8. **Add Exception Handling** to GlobalExceptionHandler
9. **Update SecurityConfig** if needed
10. **Add Tests** in test/ package


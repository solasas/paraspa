# paraspa Quick Start Guide

## 🚀 Get Started in 5 Minutes

### Step 1: Prerequisites (2 minutes)
Ensure you have installed:
- Java 17+ ([Download](https://adoptium.net/))
- Maven 3.8+ ([Download](https://maven.apache.org/))
- PostgreSQL 12+ ([Download](https://www.postgresql.org/))

Verify installations:
```bash
java -version
mvn --version
psql --version
```

### Step 2: Setup Database (1 minute)
```bash
# Start PostgreSQL (if not already running)
# macOS: brew services start postgresql
# Ubuntu: sudo systemctl start postgresql

# Create database
psql -U postgres -h localhost -c "CREATE DATABASE paraspa_db;" 2>/dev/null || true
```

### Step 3: Build Project (1 minute)
```bash
cd paraspa
mvn clean install -DskipTests
```

### Step 4: Run Application (1 minute)
```bash
mvn spring-boot:run
```

The API will be available at: **http://localhost:8080**

## ✅ Verify It Works

Open a new terminal and test:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "test@example.com",
    "password": "password123"
  }'
```

Expected response:
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "type": "Bearer",
  "id": 1,
  "name": "Test User",
  "email": "test@example.com"
}
```

## 📚 Next Steps

### Load Sample Data
```bash
# Connect to database
psql -U postgres -d paraspa_db

# Copy-paste contents of src/main/resources/init-data.sql
# Or:
\i src/main/resources/init-data.sql
```

### Test the API
See **API-TESTING.md** for detailed curl examples

### Explore the Code
- Start with: **src/main/java/com/sashank/paraspa/paraspaApplication.java**
- Read: **README.md** for full documentation
- Check: **PROJECT-STRUCTURE.md** for code organization

## 🔑 Key API Endpoints

```
POST   /api/auth/register              # Create account
POST   /api/auth/login                 # Get JWT token
GET    /api/profile/me                 # View profile
POST   /api/profile/skills             # Add skill to profile
GET    /api/matches                    # Find skill matches
POST   /api/exchanges                  # Request skill exchange
PUT    /api/exchanges/{id}/accept      # Accept request
POST   /api/exchanges/{id}/sessions    # Schedule session
POST   /api/sessions/{id}/ratings      # Rate user
```

## 🛠️ Troubleshooting

| Problem | Solution |
|---------|----------|
| `Cannot connect to PostgreSQL` | Start PostgreSQL: `brew services start postgresql` |
| `Port 8080 already in use` | Change port: Edit `application.properties` → `server.port=8081` |
| `Cannot resolve symbol` (IDE) | Right-click `pom.xml` → Maven → Reload Project |
| `Database doesn't exist` | Run: `psql -U postgres -c "CREATE DATABASE paraspa_db;"` |

## 📖 Documentation Files

- **README.md** - Complete feature overview
- **SETUP.md** - Detailed environment setup
- **API-TESTING.md** - API testing with curl examples
- **PROJECT-STRUCTURE.md** - Code organization guide

## 🎯 Sample Workflow

```bash
# 1. Register user
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@test.com","password":"pass123"}' | jq -r '.token')

# 2. Get all skills
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/skills

# 3. Add skill to profile
curl -X POST http://localhost:8080/api/profile/skills \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"skillId":1,"type":"TEACH","level":"ADVANCED"}'

# 4. Find matches
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/matches
```

## 🔐 Security Notes

- All endpoints except `/api/auth/**` require JWT token
- Token format: `Authorization: Bearer <token>`
- Token expires in 24 hours
- Password is hashed with BCrypt
- SQL injection protected (parameterized queries)

## 💾 Database Auto-Schema

The application automatically creates tables on startup:
- `users` - User accounts
- `skills` - Skill catalog  
- `user_skills` - User's skills
- `skill_exchanges` - Exchange requests
- `sessions` - Teaching sessions
- `ratings` - User ratings

## 🚀 Production Deployment

Before deploying to production:

1. **Change JWT Secret**
   ```properties
   jwt.secret=generate-a-long-random-secure-key
   ```

2. **Use Strong Database Password**
   ```properties
   spring.datasource.password=strong-password
   ```

3. **Set ddl-auto to Validate**
   ```properties
   spring.jpa.hibernate.ddl-auto=validate
   ```

4. **Enable HTTPS**
   ```properties
   server.ssl.enabled=true
   server.ssl.key-store=classpath:keystore.p12
   ```

5. **Use Environment Variables**
   ```bash
   export JWT_SECRET=your-secret
   export DB_PASSWORD=your-password
   ```

## 📞 Getting Help

1. Check **README.md** for feature documentation
2. Review **API-TESTING.md** for endpoint examples
3. Read **SETUP.md** for environment configuration
4. Check logs: Application outputs detailed logs to console

## ⚡ Performance Tips

- Database queries are optimized with @Query annotations
- Lazy loading for related entities
- Built-in pagination support
- Connection pooling with HikariCP
- Efficient DTO mapping

## 🔄 Development Workflow

```bash
# Terminal 1: Start application
mvn spring-boot:run

# Terminal 2: Run curl commands
curl http://localhost:8080/api/...

# Terminal 3: Monitor database
psql -U postgres -d paraspa_db
SELECT * FROM users;
```

## 📝 Key Features Implemented

✅ User registration & authentication with JWT  
✅ Skill management & categorization  
✅ User profile with bio & location  
✅ Smart skill matching algorithm  
✅ Skill exchange request system  
✅ Session scheduling (ONLINE/OFFLINE)  
✅ User rating & review system  
✅ Comprehensive error handling  
✅ Input validation  
✅ Security with Spring Security 6.x  

## 🎓 Learning Path

1. **Start**: Read README.md
2. **Understand**: Review entity classes in src/main/java/.../entity/
3. **Learn**: Check service implementations in service/impl/
4. **Explore**: Test endpoints using API-TESTING.md
5. **Extend**: Add new features following PROJECT-STRUCTURE.md

## 📊 Technology Stack Summary

- **Framework**: Spring Boot 3.2.0
- **Security**: Spring Security 6.x + JWT
- **Database**: PostgreSQL + Spring Data JPA
- **Authentication**: jjwt 0.11.5
- **Serialization**: Jackson (built-in)
- **Validation**: Spring Validation
- **Build**: Maven
- **Language**: Java 17

---

**Enjoy using paraspa! Happy skill exchanging! 🎓**


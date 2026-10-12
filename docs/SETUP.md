# Environment Setup Guide

## Prerequisites

Before starting the paraspa application, ensure you have the following installed:

### 1. Java 17+
```bash
# Check Java version
java -version

# If not installed, install from:
# - macOS: brew install openjdk@17
# - Ubuntu: sudo apt-get install openjdk-17-jdk
# - Windows: Download from oracle.com or adoptium.net
```

### 2. Maven 3.8+
```bash
# Check Maven version
mvn -version

# If not installed:
# - macOS: brew install maven
# - Ubuntu: sudo apt-get install maven
# - Windows: Download from maven.apache.org
```

### 3. PostgreSQL 12+
```bash
# Check PostgreSQL version
psql --version

# If not installed:
# - macOS: brew install postgresql
# - Ubuntu: sudo apt-get install postgresql
# - Windows: Download from postgresql.org

# Start PostgreSQL service:
# - macOS: brew services start postgresql
# - Ubuntu: sudo systemctl start postgresql
# - Windows: Start from Services or PostgreSQL installer
```

## Database Setup

### Option 1: Using psql Command Line

```bash
# Connect to PostgreSQL
psql -U postgres -h localhost

# Create database
CREATE DATABASE paraspa_db;

# Verify creation
\l

# Exit
\q
```

### Option 2: Using pgAdmin GUI

1. Open pgAdmin (usually at http://localhost:5050)
2. Right-click "Databases" → Create → Database
3. Name: `paraspa_db`
4. Click "Save"

### Option 3: Let Spring Boot Create It

The application will automatically create the database if you:
1. Have PostgreSQL running
2. Spring is configured to auto-create tables (ddl-auto=update)

## Application Configuration

### Database Connection

Edit `src/main/resources/application.properties`:

```properties
# Default configuration (should work on most local setups)
spring.datasource.url=jdbc:postgresql://localhost:5432/paraspa_db
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA/Hibernate
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Server Port
server.port=8080

# JWT Secret (IMPORTANT: Change in production!)
jwt.secret=your-secret-key-change-this-in-production-use-a-long-secure-string-at-least-256-bits
jwt.expiration=86400000
```

## Building the Application

### Full Build (with tests)
```bash
mvn clean install
```

### Build without Tests (faster)
```bash
mvn clean install -DskipTests
```

### Build Single Module
```bash
mvn clean package
```

## Running the Application

### Option 1: Maven
```bash
mvn spring-boot:run
```

### Option 2: Java Command
```bash
# First build the jar
mvn clean package -DskipTests

# Then run
java -jar target/paraspa-0.0.1-SNAPSHOT.jar
```

### Option 3: IDE Run
- IntelliJ IDEA: Right-click paraspaApplication.java → Run
- Eclipse: Right-click project → Run As → Spring Boot App
- VS Code: Use Spring Boot Extension

## Verifying the Setup

### 1. Check Server is Running
```bash
curl http://localhost:8080/api/auth/login
```

### 2. Test API
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "test@example.com",
    "password": "password123"
  }'
```

### 3. Check Logs
Look for:
```
Started paraspaApplication in X seconds
```

## Troubleshooting

### Issue: Cannot connect to PostgreSQL
```
Error: connection refused
```

**Solution:**
1. Verify PostgreSQL is running: `pg_isready -h localhost`
2. Check credentials in application.properties
3. Verify port: default is 5432

### Issue: Database does not exist
```
Error: FATAL: database "paraspa_db" does not exist
```

**Solution:**
```bash
# Create the database
psql -U postgres -h localhost -c "CREATE DATABASE paraspa_db;"
```

### Issue: Port 8080 already in use
```
Error: Address already in use
```

**Solution:**
Option A: Change port in application.properties
```properties
server.port=8081
```

Option B: Kill process using port 8080
```bash
# macOS/Linux
lsof -ti:8080 | xargs kill -9

# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Issue: Wrong Java version
```
Error: unsupported class file format
```

**Solution:**
```bash
# Check Java version
java -version

# Set JAVA_HOME to Java 17+
export JAVA_HOME=/path/to/java/17
```

### Issue: Maven dependencies not downloading
```
Error: [ERROR] Could not find goal 'org.springframework.boot:spring-boot-maven-plugin'
```

**Solution:**
```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Run Maven again
mvn clean install
```

### Issue: IDE not recognizing Spring annotations
```
Cannot resolve symbol 'EnableWebSecurity'
```

**Solution:**
1. Right-click pom.xml → Maven → Reload Project (IntelliJ)
2. Or manually run: `mvn clean install`
3. Refresh IDE classpath

## IDE Setup

### IntelliJ IDEA
1. Open project folder
2. Mark `src/main/java` as Sources Root
3. Mark `src/main/resources` as Resources Root
4. Right-click pom.xml → Maven → Reload Project
5. Run → Edit Configurations → Add Spring Boot configuration

### Eclipse
1. File → Import → Maven → Existing Maven Projects
2. Select the project folder
3. Maven → Update Project
4. Run → Run As → Spring Boot App

### VS Code
1. Install "Extension Pack for Java"
2. Install "Spring Boot Extension Pack"
3. Open command palette → Spring Boot: Start
4. Or press F5 to debug

## Development Workflow

### 1. Start PostgreSQL
```bash
# macOS
brew services start postgresql

# Ubuntu
sudo systemctl start postgresql

# Windows
# Start from Services or PostgreSQL installed program
```

### 2. Build Project
```bash
mvn clean install -DskipTests
```

### 3. Run Application
```bash
mvn spring-boot:run
```

### 4. Test API
```bash
# Use curl commands from API-TESTING.md
# Or use Postman collection
```

### 5. Check Database
```bash
# Connect to database
psql -U postgres -d paraspa_db

# View tables
\dt

# Exit
\q
```

## Production Considerations

### Security
- **JWT Secret**: Generate a strong random key
  ```bash
  # Generate random key
  openssl rand -base64 32
  ```
- Never commit secrets to version control
- Use environment variables for sensitive config

### Database
- Use strong passwords
- Enable SSL/TLS connections
- Regular backups
- Use connection pooling

### Performance
- Use connection pooling (HikariCP)
- Add database indexes
- Cache frequently accessed data
- Implement pagination

### Monitoring
- Add logging
- Use application monitoring tools
- Track API metrics
- Monitor database performance

## Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Spring Security](https://spring.io/projects/spring-security)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [JWT.io](https://jwt.io)


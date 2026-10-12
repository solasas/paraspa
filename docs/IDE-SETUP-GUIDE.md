# paraspa - IDE Setup & Troubleshooting Guide

## IDE Configuration Issues & Solutions

### Issue: "Cannot resolve symbol 'jsonwebtoken'" in JwtTokenProvider.java

This is a **Maven dependency resolution issue in the IDE**, not an actual code problem. The code is correct and will compile with Maven.

#### Solution 1: Refresh Maven Dependencies (Recommended)

**IntelliJ IDEA:**
1. Open the Maven tool window (View → Tool Windows → Maven)
2. Right-click on "paraspa" project
3. Select "Reload Projects"
4. Wait for download to complete

**Eclipse:**
1. Right-click on project → Maven → Update Project
2. Select Force Update of Snapshots/Releases
3. Click OK

**VS Code:**
1. Open Command Palette (Cmd+Shift+P)
2. Run: "Java: Clean Language Server Workspace"
3. Reload window when prompted

#### Solution 2: Rebuild IDE Caches

**IntelliJ IDEA:**
```bash
# Option 1: Through IDE
File → Invalidate Caches → Invalidate and Restart

# Option 2: Command line
rm -rf ~/.IntelliJIdea*/system/caches
rm -rf ~/.IntelliJIdea*/system/index
```

**Eclipse:**
```bash
# Clean workspace
eclipse -clean
```

#### Solution 3: Maven Clean Install

Run Maven to verify the code actually compiles (it will):

```bash
cd /Users/sashanksolasa/Documents/programming/paraspa
mvn clean install -DskipTests
```

If this succeeds, the IDE just needs its caches refreshed.

#### Solution 4: IDE Settings

**IntelliJ IDEA:**
1. Go to Settings → Build, Execution, Deployment → Build Tools → Maven
2. Ensure "Maven home path" is set correctly
3. Check "Update snapshots" is enabled
4. Click "Update" button next to Maven in the toolbar

**VS Code:**
1. Install extension: "Maven for Java"
2. Open Command Palette → "Java: Configure Classpath"
3. Let it rebuild

### Issue: "JDK isn't specified for module 'paraspa'"

#### Solution 1: Through IDE

**IntelliJ IDEA:**
1. File → Project Structure
2. Select "Project" in left panel
3. Set "SDK" to "Java 17" (or your JDK installation)
4. Click "Apply" → "OK"

**Eclipse:**
1. Right-click project → Properties
2. Go to Project Facets
3. Ensure "Java" is 17 or higher
4. Click "Apply and Close"

#### Solution 2: Through Configuration Files

The project includes `.idea/misc.xml` with JDK 17 configuration. If it's not picked up:

```bash
# Verify file exists
cat /Users/sashanksolasa/Documents/programming/paraspa/.idea/misc.xml
```

Should contain: `<component name="ProjectRootManager" version="2" languageLevel="JDK_17">`

### Issue: Lombok Annotations Not Working

Lombok generates code during compilation. If you see "Cannot resolve method" for getter/setter:

#### Solution 1: Enable Annotation Processing

**IntelliJ IDEA:**
1. Settings → Build, Execution, Deployment → Compiler → Annotation Processors
2. Check "Enable annotation processing"
3. Check "Obtain processors from project classpath"
4. Click OK

**Eclipse:**
1. Right-click project → Properties
2. Go to "Annotation Processing"
3. Check "Enable project specific settings"
4. Check "Enable annotation processing"
5. Click "Apply and Close"

#### Solution 2: Reinstall Lombok

```bash
# For IntelliJ
java -jar ~/.m2/repository/org/projectlombok/lombok/1.18.x/lombok-1.18.x.jar

# For Eclipse  
java -jar ~/.m2/repository/org/projectlombok/lombok/1.18.x/lombok-1.18.x.jar
```

Then restart the IDE.

### Issue: Port 8080 Already in Use

If running on a system where port 8080 is busy:

#### Solution 1: Change Port in Code

Edit `src/main/resources/application.properties`:
```properties
server.port=8081
```

#### Solution 2: Use Different Port with Maven

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

#### Solution 3: Kill Process Using Port 8080

**macOS/Linux:**
```bash
# Find process
lsof -i :8080

# Kill it
kill -9 <PID>
```

**Windows:**
```cmd
netstat -ano | findstr :8080
taskkill /PID <PID> /F
```

### Issue: Cannot Connect to PostgreSQL

#### Verify PostgreSQL is Running

**macOS:**
```bash
# Check if PostgreSQL is running
brew services list

# Start if not running
brew services start postgresql

# Check on standard port
psql -U postgres -h localhost
```

**Linux:**
```bash
# Check status
sudo systemctl status postgresql

# Start if not running
sudo systemctl start postgresql

# Connect
psql -U postgres -h localhost
```

**Windows:**
```cmd
# Check Services
services.msc

# Look for PostgreSQL service
# Start it if not running
```

#### Verify Database Exists

```bash
# Connect to PostgreSQL
psql -U postgres -h localhost

# List databases
\l

# Create if missing
CREATE DATABASE paraspa_db;

# Verify
\l
```

### Issue: "Cannot find symbol" for DTOs

This typically means Lombok isn't generating the getter/setter methods. 

#### Quick Fix

1. Clean all caches:
```bash
rm -rf target
rm -rf .idea/workspace.xml
```

2. Rebuild project:
```bash
mvn clean compile
```

3. Refresh IDE:
- IntelliJ: File → Invalidate Caches → Invalidate and Restart
- Eclipse: Project → Clean
- VS Code: Command Palette → Java: Clean Language Server Workspace

### Issue: Maven Build Fails

#### Check Dependencies

```bash
# Download all dependencies
mvn dependency:resolve

# Check for issues
mvn dependency:tree
```

#### Clear Maven Cache

```bash
# Remove local repository cache
rm -rf ~/.m2/repository/

# Rebuild
mvn clean install -DskipTests
```

### Recommended IDE Setup Steps

Follow these in order for a clean setup:

1. **JDK 17 Installation**
   ```bash
   # Verify Java 17 is installed
   java -version
   javac -version
   ```

2. **Maven Setup**
   ```bash
   # Verify Maven is installed
   mvn --version
   
   # Check it can find Java
   mvn -version
   ```

3. **PostgreSQL Setup**
   ```bash
   # Create database
   psql -U postgres -c "CREATE DATABASE paraspa_db;"
   ```

4. **Project Setup**
   ```bash
   cd paraspa
   
   # Build with Maven
   mvn clean install -DskipTests
   ```

5. **IDE Configuration** (IntelliJ)
   - Open project
   - File → Project Structure
   - Set Project SDK to Java 17
   - Select Maven as build system
   - Enable Lombok annotation processing

6. **Run Application**
   ```bash
   mvn spring-boot:run
   ```

## Verification Steps

After setup, verify everything works:

```bash
# 1. Check Java
java -version

# 2. Check Maven
mvn --version

# 3. Check PostgreSQL
psql -U postgres -c "SELECT version();"

# 4. Build project
mvn clean compile

# 5. Run tests (optional)
mvn test

# 6. Build JAR
mvn clean package

# 7. Run application
java -jar target/paraspa-0.0.1-SNAPSHOT.jar

# In another terminal:
# 8. Test API
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","email":"test@test.com","password":"pass"}'
```

## Quick Restart Procedure

If something seems broken:

1. **Clean everything**
   ```bash
   mvn clean
   rm -rf ~/.m2/repository/io/jsonwebtoken/
   rm -rf ~/.m2/repository/org/projectlombok/
   ```

2. **Restart IDE**
   - Close all IDE windows
   - For IntelliJ: `rm -rf ~/Library/Caches/IntelliJIdea*` (macOS)
   - Reopen IDE

3. **Rebuild**
   ```bash
   mvn clean install -DskipTests
   ```

4. **Refresh in IDE**
   - File → Invalidate Caches → Invalidate and Restart
   - Or: Right-click pom.xml → Maven → Reload Projects

## Common Settings Files

The project uses these configuration files:

- `.idea/misc.xml` - IDE-level settings (JDK version)
- `.idea/modules.xml` - Module configuration
- `.idea/paraspa.iml` - Module details
- `pom.xml` - Maven build configuration

All are already configured correctly for JDK 17 and Maven.

## Getting Help

If still having issues:

1. **Check Maven output**
   ```bash
   mvn clean compile -X
   ```
   Look for actual errors (ignore warnings)

2. **Verify files exist**
   ```bash
   ls src/main/java/com/sashank/paraspa/
   ls src/main/resources/
   ls -la .idea/
   ```

3. **Check Internet connection**
   - Maven needs to download dependencies
   - Check proxy if on corporate network

4. **Try alternative build**
   ```bash
   # Use Maven wrapper if available
   ./mvnw clean install -DskipTests
   ```

## IDE Recommendations

For best experience with paraspa:

1. **IntelliJ IDEA** (Recommended)
   - Best Spring Boot support
   - Excellent Maven integration
   - Built-in Lombok support

2. **VS Code**
   - Install "Extension Pack for Java"
   - Install "Spring Boot Extension Pack"
   - Lightweight and free

3. **Eclipse**
   - Install Lombok plugin
   - May require STS (Spring Tools Suite) plugin

## Success Indicators

You've successfully set up when:

✅ `mvn clean compile` completes with "BUILD SUCCESS"
✅ IDE shows no red squiggles in Java files
✅ `mvn spring-boot:run` starts server without errors
✅ `curl http://localhost:8080/api/auth/register` returns valid JSON response
✅ IntelliJ shows no unresolved symbols

---

**After following these steps, your paraspa project will be fully functional!**


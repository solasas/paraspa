# Fix: io.jsonwebtoken Package Not Found

## Problem
IDE shows error: `java: package io.jsonwebtoken does not exist`

## Root Cause
Maven dependencies have been updated in `pom.xml` but the IDE hasn't refreshed its classpath yet.

## Solution

### Option 1: IntelliJ IDEA (Recommended)
1. Open `pom.xml` in editor
2. Right-click on `pom.xml` in Project view
3. Select **Maven → Reload Project**
4. Wait for indexing to complete
5. The error should disappear

**Alternative Method:**
1. File → Invalidate Caches
2. Select "Invalidate and Restart"
3. IDE will restart and refresh dependencies

### Option 2: Eclipse
1. Right-click project
2. Select **Maven → Update Project**
3. Press **Alt+F5**
4. Select "OK"
5. Wait for build to complete

### Option 3: VS Code
1. Open terminal in project root
2. Run: `mvn clean install -DskipTests`
3. Wait for completion
4. Refresh the IDE

### Option 4: Command Line (All IDEs)
```bash
cd /Users/sashanksolasa/Documents/programming/paraspa

# Clear Maven cache
rm -rf ~/.m2/repository/io/jsonwebtoken

# Rebuild
mvn clean install -DskipTests
```

## Verification
After refreshing, you should see:
- ✅ No error on line 3 of JwtTokenProvider.java
- ✅ `io.jsonwebtoken` imports resolve
- ✅ All JWT classes available

## Updated Dependencies
The pom.xml now includes all required JWT dependencies:
```xml
<!-- jjwt-api (main API) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>

<!-- jjwt-impl (implementation - runtime) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>

<!-- jjwt-jackson (JSON serialization - runtime) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
```

## If Error Persists

### Clear IDE Cache
**IntelliJ:**
```
~/Library/Caches/IntelliJIdea*
```

**Eclipse:**
```
.metadata/.plugins/org.eclipse.m2e.core
```

### Verify pom.xml
Check that JWT dependencies are correctly added:
```bash
grep -A 20 "<!-- JWT" pom.xml
```

Should show 3 dependencies (jjwt-api, jjwt-impl, jjwt-jackson)

## Next Steps
Once error is resolved:
1. Build project: `mvn clean install -DskipTests`
2. Run application: `mvn spring-boot:run`
3. Test API: See API-TESTING.md

---

**The pom.xml has been updated. Just refresh your IDE!** ✅


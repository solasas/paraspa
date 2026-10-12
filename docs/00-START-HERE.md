# ✅ paraspa - START HERE

## 🎯 You Have a Complete REST API - Ready to Run!

Your paraspa platform is **fully built** with 22 REST endpoints, database schema, JWT security, and everything else needed.

---

## 🚀 Get It Running (3 Steps - 5 Minutes)

### Step 1: Create Database
```bash
psql -U postgres -c "CREATE DATABASE paraspa_db;"
```

### Step 2: Build Project
```bash
cd /Users/sashanksolasa/Documents/programming/paraspa
mvn clean install -DskipTests
```

### Step 3: Run Application
```bash
mvn spring-boot:run
```

✅ **API is ready at:** http://localhost:8080

---

## ✨ Test It Works

In a new terminal:

```bash
# Register a user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"test@example.com","password":"password123"}'
```

You should get back a JWT token! 🎉

---

## 📖 Next: Read the Documentation

Read these files in order:

1. **QUICKSTART.md** ← Start here for setup details
2. **QUICK-REFERENCE.md** ← All endpoints at a glance
3. **COMPLETE-README.md** ← Full API documentation
4. **IDE-SETUP-GUIDE.md** ← If you have IDE issues

---

## 🎓 What's Included

✅ **22 REST Endpoints** - All features working
✅ **PostgreSQL Database** - 6 tables, auto-created
✅ **JWT Authentication** - Secure token-based auth
✅ **Input Validation** - All data validated
✅ **Error Handling** - Consistent error responses
✅ **Sample Data** - Ready for testing
✅ **Spring Security** - Professional security
✅ **Complete Code** - No placeholders

---

## 🔑 Key Endpoints

```bash
# Auth (public)
POST /api/auth/register      # Create account
POST /api/auth/login         # Get JWT token

# Profile (protected - need token)
GET  /api/profile/me         # Get your profile
POST /api/profile/skills     # Add skill
GET  /api/matches            # Find matches

# Exchange & Sessions
POST /api/exchanges          # Send exchange request
POST /api/exchanges/{id}/sessions  # Schedule session
POST /api/sessions/{id}/ratings    # Rate user
```

For all 22 endpoints, see **QUICK-REFERENCE.md**

---

## ⚙️ Configuration

All settings in: `src/main/resources/application.properties`

**Default settings:**
- Database: `paraspa_db` on localhost:5432
- Server: Port 8080
- JWT: 24-hour token expiration
- Username: `postgres`
- Password: `postgres`

Change any of these if needed.

---

## 🛠️ Technology Used

- Java 17
- Spring Boot 3.2.0
- PostgreSQL
- JWT (jjwt 0.11.5)
- Lombok
- Maven

All configured and ready to go!

---

## 🚀 What You Can Do Now

1. **Build a Web Frontend** - React, Vue, Angular, etc.
2. **Build a Mobile App** - iOS, Android
3. **Integrate Other Services** - Payment, Email, etc.
4. **Deploy to Cloud** - AWS, Azure, GCP
5. **Scale with Docker** - Container deployment
6. **Add More Features** - Extend the platform

---

## 📊 Project Contents

```
71 Java classes
22 REST endpoints  
6 Database tables
8 Service implementations
Complete security setup
Full error handling
Input validation
Sample data
6+ Documentation files
```

---

## ❓ Troubleshooting

### "JDK isn't specified"
→ See **IDE-SETUP-GUIDE.md**

### "Cannot connect to database"
→ Make sure PostgreSQL is running and database exists

### "Cannot find symbol" in IDE
→ Run Maven: `mvn clean install -DskipTests`

### "Port 8080 in use"
→ Change `server.port=8081` in application.properties

### "Build fails"
→ Run: `mvn clean install -DskipTests` to see the error

---

## ✅ Success Checklist

After setup, you should have:

- ✅ Database created (`paraspa_db`)
- ✅ Maven build completed ("BUILD SUCCESS")
- ✅ Application running on port 8080
- ✅ API responds to requests
- ✅ Can register users and get JWT tokens

---

## 📚 Documentation Map

```
START HERE
    ↓
QUICKSTART.md (5-min setup)
    ↓
QUICK-REFERENCE.md (endpoint list)
    ↓
COMPLETE-README.md (full docs)
    ↓
IDE-SETUP-GUIDE.md (if issues)
```

---

## 🎯 Sample Workflow

```bash
# 1. Get token (from register or login)
TOKEN="eyJhbGciOiJIUzUxMiJ9..."

# 2. Get your profile
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/profile/me

# 3. Add a skill
curl -X POST http://localhost:8080/api/profile/skills \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"skillId":1,"type":"TEACH","level":"ADVANCED"}'

# 4. Find matches
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/matches

# 5. Send exchange request
curl -X POST http://localhost:8080/api/exchanges \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"receiverId":2,"offeredSkillId":1,"wantedSkillId":3}'
```

---

## 💡 Pro Tips

1. **Save JWT tokens** - Use them in all subsequent requests
2. **Test with curl** - Check QUICK-REFERENCE.md for examples
3. **Enable SQL logging** - Set `spring.jpa.show-sql=true`
4. **Use Postman** - Import endpoints for easier testing
5. **Read code comments** - Understand the architecture

---

## 🎉 You're Ready!

Your complete REST API backend is ready to use.

### Next 5 Minutes:
1. Run the 3 commands above
2. Test one endpoint with curl
3. Read QUICKSTART.md for more details

### Next 30 Minutes:
1. Explore QUICK-REFERENCE.md
2. Test different endpoints
3. Check COMPLETE-README.md

### Next Steps:
1. Build your frontend
2. Connect it to this API
3. Deploy and enjoy!

---

## 📞 Need Help?

- **Setup issues?** → QUICKSTART.md
- **Endpoint questions?** → QUICK-REFERENCE.md  
- **API details?** → COMPLETE-README.md
- **IDE problems?** → IDE-SETUP-GUIDE.md
- **Project overview?** → PROJECT-COMPLETION.md

---

## 🚀 Let's Go!

```bash
# Copy-paste these 3 commands:

psql -U postgres -c "CREATE DATABASE paraspa_db;"

mvn clean install -DskipTests

mvn spring-boot:run
```

API will be at **http://localhost:8080** 

**Happy coding!** 🎓


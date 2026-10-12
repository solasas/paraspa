# paraspa API Testing Guide

This guide provides curl commands to test all API endpoints.

## Base URL
```
http://localhost:8080/api
```

## 1. Authentication

### Register New User
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Johnson",
    "email": "alice@example.com",
    "password": "password123"
  }'
```

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "password123"
  }'
```

**Response will contain the JWT token - save it for other requests:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "type": "Bearer",
  "id": 1,
  "name": "Alice Johnson",
  "email": "alice@example.com"
}
```

**Set token as environment variable:**
```bash
export TOKEN="your-token-here"
```

## 2. Profile Management

### Get My Profile
```bash
curl -X GET http://localhost:8080/api/profile/me \
  -H "Authorization: Bearer $TOKEN"
```

### Update Profile
```bash
curl -X PUT http://localhost:8080/api/profile/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Johnson",
    "bio": "Guitar enthusiast and music lover",
    "city": "San Francisco"
  }'
```

## 3. Skills

### Get All Skills
```bash
curl -X GET http://localhost:8080/api/skills \
  -H "Authorization: Bearer $TOKEN"
```

### Get Skills by Category
```bash
curl -X GET "http://localhost:8080/api/skills?category=MUSIC" \
  -H "Authorization: Bearer $TOKEN"
```

### Get Skill Categories
```bash
curl -X GET http://localhost:8080/api/skills/categories \
  -H "Authorization: Bearer $TOKEN"
```

### Create New Skill
```bash
curl -X POST http://localhost:8080/api/skills \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Piano",
    "category": "MUSIC",
    "description": "Piano lessons for all levels"
  }'
```

## 4. User Skills

### Add Skill to Profile (Teach)
```bash
curl -X POST http://localhost:8080/api/profile/skills \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "skillId": 1,
    "type": "TEACH",
    "level": "ADVANCED"
  }'
```

### Add Skill to Profile (Learn)
```bash
curl -X POST http://localhost:8080/api/profile/skills \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "skillId": 9,
    "type": "LEARN",
    "level": "BEGINNER"
  }'
```

### Get My Skills
```bash
curl -X GET http://localhost:8080/api/profile/skills \
  -H "Authorization: Bearer $TOKEN"
```

### Remove a Skill
```bash
curl -X DELETE http://localhost:8080/api/profile/skills/1 \
  -H "Authorization: Bearer $TOKEN"
```

## 5. Matching

### Find All Matches
```bash
curl -X GET http://localhost:8080/api/matches \
  -H "Authorization: Bearer $TOKEN"
```

### Find Mutual Matches Only
```bash
curl -X GET http://localhost:8080/api/matches/mutual \
  -H "Authorization: Bearer $TOKEN"
```

### Check Match with Specific User
```bash
curl -X GET http://localhost:8080/api/matches/2 \
  -H "Authorization: Bearer $TOKEN"
```

## 6. Skill Exchanges

### Create Exchange Request
```bash
curl -X POST http://localhost:8080/api/exchanges \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "receiverId": 2,
    "offeredSkillId": 1,
    "wantedSkillId": 9,
    "message": "I would love to learn Spanish!"
  }'
```

### Get All My Exchanges
```bash
curl -X GET http://localhost:8080/api/exchanges \
  -H "Authorization: Bearer $TOKEN"
```

### Get Sent Exchanges
```bash
curl -X GET http://localhost:8080/api/exchanges/sent \
  -H "Authorization: Bearer $TOKEN"
```

### Get Received Exchanges
```bash
curl -X GET http://localhost:8080/api/exchanges/received \
  -H "Authorization: Bearer $TOKEN"
```

### Get Exchange Details
```bash
curl -X GET http://localhost:8080/api/exchanges/1 \
  -H "Authorization: Bearer $TOKEN"
```

### Accept Exchange Request
```bash
curl -X PUT http://localhost:8080/api/exchanges/1/accept \
  -H "Authorization: Bearer $TOKEN"
```

### Reject Exchange Request
```bash
curl -X PUT http://localhost:8080/api/exchanges/1/reject \
  -H "Authorization: Bearer $TOKEN"
```

### Complete Exchange
```bash
curl -X PUT http://localhost:8080/api/exchanges/1/complete \
  -H "Authorization: Bearer $TOKEN"
```

## 7. Sessions

### Schedule a Session
```bash
curl -X POST http://localhost:8080/api/exchanges/1/sessions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "dateTime": "2024-04-10T15:00:00",
    "durationMinutes": 60,
    "mode": "ONLINE",
    "meetLink": "https://zoom.us/j/meeting123",
    "notes": "First lesson on basics"
  }'
```

### Schedule Offline Session
```bash
curl -X POST http://localhost:8080/api/exchanges/1/sessions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "dateTime": "2024-04-10T15:00:00",
    "durationMinutes": 60,
    "mode": "OFFLINE",
    "location": "Coffee shop at Main Street",
    "notes": "In-person lesson"
  }'
```

### Get Sessions in Exchange
```bash
curl -X GET http://localhost:8080/api/exchanges/1/sessions \
  -H "Authorization: Bearer $TOKEN"
```

### Get My Upcoming Sessions
```bash
curl -X GET http://localhost:8080/api/sessions/my-sessions \
  -H "Authorization: Bearer $TOKEN"
```

### Get Session Details
```bash
curl -X GET http://localhost:8080/api/sessions/1 \
  -H "Authorization: Bearer $TOKEN"
```

### Complete Session
```bash
curl -X PUT http://localhost:8080/api/sessions/1/complete \
  -H "Authorization: Bearer $TOKEN"
```

### Cancel Session
```bash
curl -X PUT http://localhost:8080/api/sessions/1/cancel \
  -H "Authorization: Bearer $TOKEN"
```

## 8. Ratings

### Rate a Session (After Completion)
```bash
curl -X POST http://localhost:8080/api/sessions/1/ratings \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "stars": 5,
    "review": "David is an excellent Spanish teacher! Very patient and thorough."
  }'
```

### Get User Ratings
```bash
curl -X GET http://localhost:8080/api/users/2/ratings \
  -H "Authorization: Bearer $TOKEN"
```

### Get User Rating Summary
```bash
curl -X GET http://localhost:8080/api/users/2/rating-summary \
  -H "Authorization: Bearer $TOKEN"
```

### Get My Received Ratings
```bash
curl -X GET http://localhost:8080/api/profile/me/ratings \
  -H "Authorization: Bearer $TOKEN"
```

## Testing Workflow

### 1. Setup Phase
1. Register two test users
2. Note down their tokens and IDs
3. Create skills in the system
4. Add skills to both user profiles

### 2. Matching Phase
1. User A adds a skill they want to teach
2. User B adds the same skill as something they want to learn
3. Check matches - they should appear in each other's matches

### 3. Exchange Phase
1. User A creates an exchange request to User B
2. User B accepts the exchange
3. Verify exchange status is ACCEPTED

### 4. Session Phase
1. User A (requester) schedules a session
2. Verify session appears in both users' upcoming sessions
3. Mark session as COMPLETED

### 5. Rating Phase
1. Both users can now rate each other (after session is completed)
2. Check rating summary to see average rating and breakdown

## Sample Complete Flow

```bash
# 1. Register Users
TOKEN_ALICE=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice", "email": "alice@test.com", "password": "pass123"}' | jq -r '.token')

TOKEN_BOB=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name": "Bob", "email": "bob@test.com", "password": "pass123"}' | jq -r '.token')

# 2. Get Skills
SKILLS=$(curl -s -X GET http://localhost:8080/api/skills \
  -H "Authorization: Bearer $TOKEN_ALICE")

# 3. Add Skills to Profile
curl -X POST http://localhost:8080/api/profile/skills \
  -H "Authorization: Bearer $TOKEN_ALICE" \
  -H "Content-Type: application/json" \
  -d '{"skillId": 1, "type": "TEACH", "level": "ADVANCED"}'

# 4. Create Exchange
EXCHANGE=$(curl -s -X POST http://localhost:8080/api/exchanges \
  -H "Authorization: Bearer $TOKEN_ALICE" \
  -H "Content-Type: application/json" \
  -d '{"receiverId": 2, "offeredSkillId": 1, "wantedSkillId": 9}' | jq -r '.id')

# 5. Accept Exchange (as Bob)
curl -X PUT http://localhost:8080/api/exchanges/$EXCHANGE/accept \
  -H "Authorization: Bearer $TOKEN_BOB"

# 6. Schedule Session
curl -X POST http://localhost:8080/api/exchanges/$EXCHANGE/sessions \
  -H "Authorization: Bearer $TOKEN_ALICE" \
  -H "Content-Type: application/json" \
  -d '{"dateTime": "2024-04-10T15:00:00", "durationMinutes": 60, "mode": "ONLINE", "meetLink": "https://zoom.us/j/test"}'
```

## Error Examples

### Validation Error (Missing Field)
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test"
  }'
```

Response:
```json
{
  "timestamp": "2024-04-05T10:30:00",
  "status": 400,
  "error": "Validation Error",
  "message": "email: Email is required; password: Password is required",
  "path": "/api/auth/register"
}
```

### Unauthorized (Missing Token)
```bash
curl -X GET http://localhost:8080/api/profile/me
```

Response: Will not include authorization info (depends on security implementation)

### Not Found
```bash
curl -X GET http://localhost:8080/api/users/999 \
  -H "Authorization: Bearer $TOKEN"
```

Response:
```json
{
  "timestamp": "2024-04-05T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "User not found",
  "path": "/api/users/999"
}
```

## Tips

- Use `jq` to parse JSON responses: `| jq -r '.id'`
- Set `TOKEN` environment variable to avoid repeating it
- Use `export EXCHANGE_ID=1` to store IDs for later use
- Check response status codes (200, 201, 400, 401, 404, 409)
- Validate JWT tokens at jwt.io if needed


-- paraspa Database Initialization Script
-- Run this script after the application creates the tables

-- Insert sample skills
INSERT INTO skills (name, category, description, created_at) VALUES
('Guitar', 'MUSIC', 'Learn to play acoustic or electric guitar', NOW()),
('Piano', 'MUSIC', 'Piano lessons for all levels', NOW()),
('Singing', 'MUSIC', 'Vocal training and singing techniques', NOW()),
('Java', 'TECH', 'Java programming language', NOW()),
('Python', 'TECH', 'Python programming language', NOW()),
('Web Development', 'TECH', 'HTML, CSS, JavaScript web development', NOW()),
('Cooking Italian', 'COOKING', 'Learn to cook authentic Italian dishes', NOW()),
('Baking', 'COOKING', 'Baking cakes, bread, and pastries', NOW()),
('Spanish', 'LANGUAGE', 'Spanish language lessons', NOW()),
('French', 'LANGUAGE', 'French language lessons', NOW()),
('Yoga', 'FITNESS', 'Yoga practice and flexibility training', NOW()),
('Drawing', 'ART', 'Basic drawing and sketching techniques', NOW());

-- Insert sample users (password123 hashed with BCrypt)
INSERT INTO users (name, email, password, bio, city, role, created_at, updated_at) VALUES
('Alice Johnson', 'alice@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36DRcT36', 'Guitar enthusiast and music lover', 'San Francisco', 'USER', NOW(), NOW()),
('Bob Smith', 'bob@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36DRcT36', 'Python developer and open source contributor', 'New York', 'USER', NOW(), NOW()),
('Carol Davis', 'carol@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36DRcT36', 'Professional chef and cooking instructor', 'Los Angeles', 'USER', NOW(), NOW()),
('David Wilson', 'david@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36DRcT36', 'Spanish teacher with 10 years experience', 'Miami', 'USER', NOW(), NOW()),
('Emily Brown', 'emily@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36DRcT36', 'Yoga instructor and fitness enthusiast', 'Boston', 'USER', NOW(), NOW());

-- Insert sample user skills
-- Alice: Can teach Guitar, wants to learn Spanish
INSERT INTO user_skills (user_id, skill_id, type, level, created_at) VALUES (1, 1, 'TEACH', 'ADVANCED', NOW());
INSERT INTO user_skills (user_id, skill_id, type, level, created_at) VALUES (1, 9, 'LEARN', 'BEGINNER', NOW());

-- Bob: Can teach Python and Web Development, wants to learn Guitar
INSERT INTO user_skills (user_id, skill_id, type, level, created_at) VALUES (2, 5, 'TEACH', 'ADVANCED', NOW());
INSERT INTO user_skills (user_id, skill_id, type, level, created_at) VALUES (2, 6, 'TEACH', 'INTERMEDIATE', NOW());
INSERT INTO user_skills (user_id, skill_id, type, level, created_at) VALUES (2, 1, 'LEARN', 'BEGINNER', NOW());

-- Carol: Can teach Cooking Italian and Baking, wants to learn Python
INSERT INTO user_skills (user_id, skill_id, type, level, created_at) VALUES (3, 7, 'TEACH', 'ADVANCED', NOW());
INSERT INTO user_skills (user_id, skill_id, type, level, created_at) VALUES (3, 8, 'TEACH', 'ADVANCED', NOW());
INSERT INTO user_skills (user_id, skill_id, type, level, created_at) VALUES (3, 5, 'LEARN', 'BEGINNER', NOW());

-- David: Can teach Spanish, wants to learn Drawing
INSERT INTO user_skills (user_id, skill_id, type, level, created_at) VALUES (4, 9, 'TEACH', 'ADVANCED', NOW());
INSERT INTO user_skills (user_id, skill_id, type, level, created_at) VALUES (4, 12, 'LEARN', 'BEGINNER', NOW());

-- Emily: Can teach Yoga, wants to learn Piano
INSERT INTO user_skills (user_id, skill_id, type, level, created_at) VALUES (5, 11, 'TEACH', 'ADVANCED', NOW());
INSERT INTO user_skills (user_id, skill_id, type, level, created_at) VALUES (5, 2, 'LEARN', 'BEGINNER', NOW());

-- Sample skill exchanges (Alice requests Spanish from David)
INSERT INTO skill_exchanges (requester_id, receiver_id, offered_skill_id, wanted_skill_id, status, message, created_at, updated_at) VALUES
(1, 4, 1, 9, 'ACCEPTED', 'I would love to learn Spanish!', NOW(), NOW());

-- Sample sessions (Session scheduled for the accepted exchange)
INSERT INTO sessions (exchange_id, scheduled_by_id, date_time, duration_minutes, mode, meet_link, notes, status, created_at, updated_at) VALUES
(1, 1, NOW() + INTERVAL '3 days', 60, 'ONLINE', 'https://zoom.us/meeting/example', 'First guitar lesson for David', 'SCHEDULED', NOW(), NOW());

-- Insert sample ratings (if you complete sessions, you can add ratings)
-- Uncomment after completing sessions
-- INSERT INTO ratings (session_id, rated_by_id, rated_to_id, stars, review, created_at) VALUES
-- (1, 1, 4, 5, 'David is an excellent Spanish teacher!', NOW());

-- View statistics
SELECT COUNT(*) as total_users FROM users;
SELECT COUNT(*) as total_skills FROM skills;
SELECT COUNT(*) as total_user_skills FROM user_skills;
SELECT COUNT(*) as total_exchanges FROM skill_exchanges;
SELECT COUNT(*) as total_sessions FROM sessions;


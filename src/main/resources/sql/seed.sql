-- CricBid: Live Cricket Player Auction System
-- Initial Seed Data
-- Database Name: cricbid

USE cricbid;

-- Clear previous data
DELETE FROM auction_log;
DELETE FROM bids;
DELETE FROM squads;
DELETE FROM players;
DELETE FROM users;
DELETE FROM teams;

-- Reset Auto-increments
ALTER TABLE auction_log AUTO_INCREMENT = 1;
ALTER TABLE bids AUTO_INCREMENT = 1;
ALTER TABLE players AUTO_INCREMENT = 1;
ALTER TABLE users AUTO_INCREMENT = 1;
ALTER TABLE teams AUTO_INCREMENT = 1;

-- 1. Insert 6 Fictional Teams (2 Default Human Franchises for Multi-Laptop + 4 Optional Bot Franchises)
INSERT INTO teams (team_id, name, purse_remaining, owner_type, bot_strategy) VALUES
(1, 'Thunder Hawks', 100.00, 'HUMAN', 'BALANCED'),
(2, 'Iron Wolves', 100.00, 'HUMAN', 'AGGRESSIVE'),
(3, 'Royal Titans', 100.00, 'BOT', 'BALANCED'),
(4, 'Storm Riders', 100.00, 'BOT', 'MONEYBALL'),
(5, 'Neon Vipers', 100.00, 'BOT', 'AGGRESSIVE'),
(6, 'Desert Falcons', 100.00, 'BOT', 'BALANCED');

-- 2. Insert Users (SHA-256 Passwords: admin123, owner123)
-- SHA-256 for 'admin123': 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9
-- SHA-256 for 'owner123': 43a0d17178a9d26c9e0fe9a74b0b45e38d32f27aed887a008a54bf6e033bf7b9
INSERT INTO users (user_id, username, password_hash, role, team_id) VALUES
(1, 'admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'ADMIN', NULL),
(2, 'owner1', '43a0d17178a9d26c9e0fe9a74b0b45e38d32f27aed887a008a54bf6e033bf7b9', 'TEAM_OWNER', 1),
(3, 'owner2', '43a0d17178a9d26c9e0fe9a74b0b45e38d32f27aed887a008a54bf6e033bf7b9', 'TEAM_OWNER', 2),
(4, 'owner3', '43a0d17178a9d26c9e0fe9a74b0b45e38d32f27aed887a008a54bf6e033bf7b9', 'TEAM_OWNER', 3),
(5, 'owner4', '43a0d17178a9d26c9e0fe9a74b0b45e38d32f27aed887a008a54bf6e033bf7b9', 'TEAM_OWNER', 4),
(6, 'owner5', '43a0d17178a9d26c9e0fe9a74b0b45e38d32f27aed887a008a54bf6e033bf7b9', 'TEAM_OWNER', 5),
(7, 'owner6', '43a0d17178a9d26c9e0fe9a74b0b45e38d32f27aed887a008a54bf6e033bf7b9', 'TEAM_OWNER', 6);

-- 3. Insert 48 Fictional Players with role-specific stats and realistic pricing
INSERT INTO players (player_id, name, role, is_overseas, base_price, rating, batting_avg, strike_rate, economy, wickets, dismissals, status) VALUES
-- Batsmen (14 players)
(1, 'Liam Thorne', 'BATSMAN', 1, 2.00, 93, 48.60, 154.20, 0.00, 0, 0, 'AVAILABLE'),
(2, 'Zachary Vance', 'BATSMAN', 1, 1.50, 89, 44.10, 148.50, 0.00, 0, 0, 'AVAILABLE'),
(3, 'Aaryan Mehta', 'BATSMAN', 0, 2.00, 94, 51.30, 142.80, 0.00, 0, 0, 'AVAILABLE'),
(4, 'Declan Frost', 'BATSMAN', 1, 1.00, 85, 39.40, 138.60, 0.00, 0, 0, 'AVAILABLE'),
(5, 'Ryan Sterling', 'BATSMAN', 0, 1.50, 88, 43.50, 141.00, 0.00, 0, 0, 'AVAILABLE'),
(6, 'Marcus Bradley', 'BATSMAN', 1, 0.75, 81, 36.20, 134.50, 0.00, 0, 0, 'AVAILABLE'),
(7, 'Kavi Sen', 'BATSMAN', 0, 1.00, 84, 40.80, 136.20, 0.00, 0, 0, 'AVAILABLE'),
(8, 'Tyler Brooks', 'BATSMAN', 1, 0.50, 78, 33.10, 129.40, 0.00, 0, 0, 'AVAILABLE'),
(9, 'Oliver Vance', 'BATSMAN', 0, 0.50, 79, 34.80, 131.00, 0.00, 0, 0, 'AVAILABLE'),
(10, 'Julian Ray', 'BATSMAN', 1, 1.00, 86, 41.20, 146.00, 0.00, 0, 0, 'AVAILABLE'),
(11, 'Jasper Reed', 'BATSMAN', 0, 0.30, 74, 29.50, 125.00, 0.00, 0, 0, 'AVAILABLE'),
(12, 'Nathan Cross', 'BATSMAN', 0, 0.40, 76, 31.00, 127.80, 0.00, 0, 0, 'AVAILABLE'),
(13, 'Lucas Bennett', 'BATSMAN', 0, 0.20, 72, 28.00, 122.50, 0.00, 0, 0, 'AVAILABLE'),
(14, 'Kieran Shaw', 'BATSMAN', 1, 0.50, 80, 35.00, 133.00, 0.00, 0, 0, 'AVAILABLE'),

-- Bowlers (16 players)
(15, 'Tariq Aziz', 'BOWLER', 1, 2.00, 95, 11.20, 88.00, 6.45, 142, 0, 'AVAILABLE'),
(16, 'Dale Morrison', 'BOWLER', 1, 1.50, 90, 9.80, 75.00, 6.85, 118, 0, 'AVAILABLE'),
(17, 'Jett Harrison', 'BOWLER', 1, 1.00, 87, 8.40, 72.00, 7.10, 98, 0, 'AVAILABLE'),
(18, 'Kabir Verma', 'BOWLER', 0, 2.00, 92, 12.50, 95.00, 6.70, 130, 0, 'AVAILABLE'),
(19, 'Sean O\'Connor', 'BOWLER', 1, 0.75, 83, 10.00, 80.00, 7.40, 76, 0, 'AVAILABLE'),
(20, 'Felix Montgomery', 'BOWLER', 1, 0.50, 80, 7.50, 65.00, 7.80, 64, 0, 'AVAILABLE'),
(21, 'Ethan Hayes', 'BOWLER', 0, 1.50, 88, 14.00, 102.00, 7.05, 105, 0, 'AVAILABLE'),
(22, 'Victor Krause', 'BOWLER', 1, 1.00, 86, 6.80, 60.00, 7.25, 89, 0, 'AVAILABLE'),
(23, 'Farhan Malik', 'BOWLER', 0, 1.00, 84, 11.50, 85.00, 7.50, 82, 0, 'AVAILABLE'),
(24, 'Caleb Stone', 'BOWLER', 0, 0.50, 79, 9.00, 70.00, 7.95, 58, 0, 'AVAILABLE'),
(25, 'Mitchell Vance', 'BOWLER', 0, 0.40, 77, 8.00, 68.00, 8.10, 52, 0, 'AVAILABLE'),
(26, 'Rory Gallagher', 'BOWLER', 1, 0.50, 81, 10.20, 82.00, 7.65, 70, 0, 'AVAILABLE'),
(27, 'Ashwin Rao', 'BOWLER', 0, 1.00, 85, 13.00, 92.00, 6.95, 94, 0, 'AVAILABLE'),
(28, 'Travis Sterling', 'BOWLER', 0, 0.30, 75, 7.20, 64.00, 8.30, 44, 0, 'AVAILABLE'),
(29, 'Zane Cooper', 'BOWLER', 0, 0.20, 71, 6.50, 58.00, 8.60, 36, 0, 'AVAILABLE'),
(30, 'Xavier Knox', 'BOWLER', 0, 0.20, 73, 8.10, 66.00, 8.40, 40, 0, 'AVAILABLE'),

-- AllRounders (10 players)
(31, 'Kyle Donovan', 'ALLROUNDER', 1, 2.00, 96, 37.80, 152.40, 7.30, 110, 0, 'AVAILABLE'),
(32, 'Cameron Vance', 'ALLROUNDER', 1, 1.50, 91, 34.20, 144.00, 7.60, 92, 0, 'AVAILABLE'),
(33, 'Aarav Kapoor', 'ALLROUNDER', 0, 2.00, 93, 38.50, 140.50, 7.15, 104, 0, 'AVAILABLE'),
(34, 'Bradley Cooper', 'ALLROUNDER', 1, 1.00, 86, 31.00, 138.00, 7.85, 74, 0, 'AVAILABLE'),
(35, 'Dominic Drake', 'ALLROUNDER', 0, 1.50, 89, 35.40, 142.20, 7.45, 88, 0, 'AVAILABLE'),
(36, 'Neil Armstrong-Smith', 'ALLROUNDER', 1, 0.75, 82, 28.50, 132.00, 8.10, 62, 0, 'AVAILABLE'),
(37, 'Connor MacLeod', 'ALLROUNDER', 1, 0.50, 79, 26.20, 128.50, 8.25, 54, 0, 'AVAILABLE'),
(38, 'Tristan Walker', 'ALLROUNDER', 0, 1.00, 85, 32.80, 136.00, 7.70, 78, 0, 'AVAILABLE'),
(39, 'Surya Nair', 'ALLROUNDER', 0, 0.50, 80, 29.10, 130.00, 8.00, 60, 0, 'AVAILABLE'),
(40, 'Devon Cole', 'ALLROUNDER', 0, 0.30, 76, 25.40, 124.00, 8.40, 42, 0, 'AVAILABLE'),

-- WicketKeepers (8 players)
(41, 'Jesse Morgan', 'WICKETKEEPER', 1, 2.00, 94, 46.50, 149.20, 0.00, 0, 88, 'AVAILABLE'),
(42, 'Rohan Joshi', 'WICKETKEEPER', 0, 2.00, 92, 43.80, 141.50, 0.00, 0, 82, 'AVAILABLE'),
(43, 'Lucas Drake', 'WICKETKEEPER', 1, 1.50, 88, 39.20, 138.00, 0.00, 0, 68, 'AVAILABLE'),
(44, 'Callum Campbell', 'WICKETKEEPER', 1, 1.00, 84, 35.60, 134.20, 0.00, 0, 56, 'AVAILABLE'),
(45, 'Sean Miller', 'WICKETKEEPER', 0, 1.00, 85, 37.00, 136.00, 0.00, 0, 62, 'AVAILABLE'),
(46, 'Tobias Wright', 'WICKETKEEPER', 0, 0.50, 80, 32.40, 129.00, 0.00, 0, 48, 'AVAILABLE'),
(47, 'Devon Ward', 'WICKETKEEPER', 0, 0.40, 77, 29.80, 126.50, 0.00, 0, 39, 'AVAILABLE'),
(48, 'Arthur Pendelton', 'WICKETKEEPER', 0, 0.20, 73, 27.00, 121.00, 0.00, 0, 34, 'AVAILABLE');

-- Seed initial state without mock logs (auction_log will be populated during live auction)

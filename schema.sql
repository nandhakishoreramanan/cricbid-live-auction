-- CricBid: Live Cricket Player Auction System
-- Database Schema for MySQL 8+
-- Database Name: cricbid

CREATE DATABASE IF NOT EXISTS cricbid CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cricbid;

-- Drop existing tables in reverse dependency order
DROP TABLE IF EXISTS auction_log;
DROP TABLE IF EXISTS bids;
DROP TABLE IF EXISTS squads;
DROP TABLE IF EXISTS players;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS teams;

-- 1. Teams Table
CREATE TABLE teams (
    team_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    purse_remaining DECIMAL(10,2) NOT NULL DEFAULT 100.00,
    owner_type ENUM('HUMAN','BOT') NOT NULL,
    bot_strategy ENUM('AGGRESSIVE','BALANCED','MONEYBALL') NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Users Table
CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(64) NOT NULL,
    role ENUM('ADMIN','TEAM_OWNER') NOT NULL,
    team_id INT NULL,
    CONSTRAINT fk_users_team FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Players Table
CREATE TABLE players (
    player_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    role ENUM('BATSMAN','BOWLER','ALLROUNDER','WICKETKEEPER') NOT NULL,
    is_overseas BOOLEAN NOT NULL DEFAULT FALSE,
    base_price DECIMAL(6,2) NOT NULL,
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 100),
    batting_avg DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    strike_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    economy DECIMAL(4,2) NOT NULL DEFAULT 0.00,
    wickets INT NOT NULL DEFAULT 0,
    dismissals INT NOT NULL DEFAULT 0,
    status ENUM('AVAILABLE','SOLD','UNSOLD') NOT NULL DEFAULT 'AVAILABLE'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Squads Table (Team-Player M:N / Ownership)
CREATE TABLE squads (
    team_id INT NOT NULL,
    player_id INT NOT NULL UNIQUE,
    sold_price DECIMAL(6,2) NOT NULL,
    PRIMARY KEY (team_id, player_id),
    CONSTRAINT fk_squads_team FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE,
    CONSTRAINT fk_squads_player FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Bids Table
CREATE TABLE bids (
    bid_id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    team_id INT NOT NULL,
    amount DECIMAL(6,2) NOT NULL,
    bid_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bids_player FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE,
    CONSTRAINT fk_bids_team FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Auction Log Table
CREATE TABLE auction_log (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    event ENUM('BID','SOLD','UNSOLD','RULE_VIOLATION','TRADE') NOT NULL,
    details VARCHAR(255) NOT NULL,
    log_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Indexes for performance
CREATE INDEX idx_players_status ON players(status);
CREATE INDEX idx_players_role ON players(role);
CREATE INDEX idx_bids_player_id ON bids(player_id);
CREATE INDEX idx_squads_team_id ON squads(team_id);

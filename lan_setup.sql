-- CricBid Multi-Laptop LAN Setup Script
-- Run this once on Laptop 1 (the MySQL + Auctioneer Host) as root:
--   mysql -u root -p < lan_setup.sql
--
-- This creates a dedicated LAN database user ('cricbid' / 'cricbid123')
-- so Laptops 2 & 3 can connect over Wi-Fi / Mobile Hotspot without root password issues.

CREATE DATABASE IF NOT EXISTS cricbid CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'cricbid'@'%' IDENTIFIED BY 'cricbid123';
ALTER USER 'cricbid'@'%' IDENTIFIED BY 'cricbid123';

CREATE USER IF NOT EXISTS 'cricbid'@'localhost' IDENTIFIED BY 'cricbid123';
ALTER USER 'cricbid'@'localhost' IDENTIFIED BY 'cricbid123';

GRANT ALL PRIVILEGES ON cricbid.* TO 'cricbid'@'%';
GRANT ALL PRIVILEGES ON cricbid.* TO 'cricbid'@'localhost';
FLUSH PRIVILEGES;

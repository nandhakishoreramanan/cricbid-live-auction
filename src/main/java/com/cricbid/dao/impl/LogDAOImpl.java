package com.cricbid.dao.impl;

import com.cricbid.dao.LogDAO;
import com.cricbid.db.DBConnection;
import com.cricbid.model.AuctionEvent;
import com.cricbid.model.AuctionEventType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;


public class LogDAOImpl implements LogDAO {

    private final DBConnection db = DBConnection.getInstance();

    @Override
    public void logEvent(AuctionEventType type, String details) throws SQLException {
        String sql = "INSERT INTO auction_log (event, details, log_time) VALUES (?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, type.name());
            String trimmed = details != null && details.length() > 255 ? details.substring(0, 255) : details;
            stmt.setString(2, trimmed != null ? trimmed : "");
            stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
        }
    }

    @Override
    public List<AuctionEvent> getRecentEvents(int limit) throws SQLException {
        String sql = "SELECT * FROM auction_log ORDER BY log_id DESC LIMIT ?";
        List<AuctionEvent> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AuctionEvent event = new AuctionEvent(
                            rs.getInt("log_id"),
                            AuctionEventType.fromString(rs.getString("event")),
                            rs.getString("details"),
                            rs.getTimestamp("log_time")
                    );
                    list.add(event);
                }
            }
        }
        return list;
    }

    @Override
    public void clearLogs() throws SQLException {
        String sql = "DELETE FROM auction_log";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }
}

package com.cricbid.dao.impl;

import com.cricbid.dao.UserDAO;
import com.cricbid.db.DBConnection;
import com.cricbid.model.User;
import com.cricbid.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


public class UserDAOImpl implements UserDAO {

    private final DBConnection db = DBConnection.getInstance();

    @Override
    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public User authenticate(String username, String passwordHash) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ? AND password_hash = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    private User mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("user_id");
        String uname = rs.getString("username");
        String hash = rs.getString("password_hash");
        UserRole role = UserRole.fromString(rs.getString("role"));
        int teamIdVal = rs.getInt("team_id");
        Integer teamId = rs.wasNull() ? null : teamIdVal;
        return new User(id, uname, hash, role, teamId);
    }
}

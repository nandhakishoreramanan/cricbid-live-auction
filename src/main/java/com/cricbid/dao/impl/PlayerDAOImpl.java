package com.cricbid.dao.impl;

import com.cricbid.dao.PlayerDAO;
import com.cricbid.db.DBConnection;
import com.cricbid.model.Player;
import com.cricbid.model.PlayerFactory;
import com.cricbid.model.PlayerRole;
import com.cricbid.model.PlayerStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class PlayerDAOImpl implements PlayerDAO {

    private final DBConnection db = DBConnection.getInstance();

    @Override
    public List<Player> findAll() throws SQLException {
        String sql = "SELECT * FROM players ORDER BY player_id ASC";
        List<Player> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(PlayerFactory.fromResultSet(rs));
            }
        }
        return list;
    }

    @Override
    public Player findById(int playerId) throws SQLException {
        String sql = "SELECT * FROM players WHERE player_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return PlayerFactory.fromResultSet(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<Player> findByStatus(PlayerStatus status) throws SQLException {
        String sql = "SELECT * FROM players WHERE status = ? ORDER BY player_id ASC";
        List<Player> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(PlayerFactory.fromResultSet(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<Player> findByRole(PlayerRole role) throws SQLException {
        String sql = "SELECT * FROM players WHERE role = ? ORDER BY player_id ASC";
        List<Player> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, role.name());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(PlayerFactory.fromResultSet(rs));
                }
            }
        }
        return list;
    }

    @Override
    public boolean updateStatus(int playerId, PlayerStatus status) throws SQLException {
        String sql = "UPDATE players SET status = ? WHERE player_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            stmt.setInt(2, playerId);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public void resetAllStatus() throws SQLException {
        String sql = "UPDATE players SET status = 'AVAILABLE'";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }

    @Override
    public int countAvailable() throws SQLException {
        String sql = "SELECT COUNT(*) FROM players WHERE status = 'AVAILABLE'";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
}

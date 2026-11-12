package com.cricbid.dao.impl;

import com.cricbid.dao.TeamDAO;
import com.cricbid.db.DBConnection;
import com.cricbid.model.BotStrategyType;
import com.cricbid.model.OwnerType;
import com.cricbid.model.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class TeamDAOImpl implements TeamDAO {

    private final DBConnection db = DBConnection.getInstance();

    @Override
    public List<Team> findAll() throws SQLException {
        String sql = "SELECT * FROM teams ORDER BY team_id ASC";
        List<Team> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    @Override
    public Team findById(int teamId) throws SQLException {
        String sql = "SELECT * FROM teams WHERE team_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, teamId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public boolean updatePurse(int teamId, double newPurse) throws SQLException {
        String sql = "UPDATE teams SET purse_remaining = ? WHERE team_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, Math.round(newPurse * 100.0) / 100.0);
            stmt.setInt(2, teamId);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updateStrategy(int teamId, BotStrategyType strategy) throws SQLException {
        String sql = "UPDATE teams SET bot_strategy = ? WHERE team_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, strategy != null ? strategy.name() : null);
            stmt.setInt(2, teamId);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public boolean updateOwnerType(int teamId, OwnerType ownerType) throws SQLException {
        String sql = "UPDATE teams SET owner_type = ? WHERE team_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ownerType != null ? ownerType.name() : OwnerType.BOT.name());
            stmt.setInt(2, teamId);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public void resetAllPurse(double initialPurse) throws SQLException {
        String sql = "UPDATE teams SET purse_remaining = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, initialPurse);
            stmt.executeUpdate();
        }
    }

    private Team mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("team_id");
        String name = rs.getString("name");
        double purse = rs.getDouble("purse_remaining");
        OwnerType ownerType = OwnerType.fromString(rs.getString("owner_type"));
        String stratStr = rs.getString("bot_strategy");
        BotStrategyType strat = stratStr != null ? BotStrategyType.fromString(stratStr) : null;
        return new Team(id, name, purse, ownerType, strat);
    }
}

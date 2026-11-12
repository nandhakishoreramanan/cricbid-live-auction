package com.cricbid.dao.impl;

import com.cricbid.dao.SquadDAO;
import com.cricbid.db.DBConnection;
import com.cricbid.model.Player;
import com.cricbid.model.PlayerFactory;
import com.cricbid.model.SquadEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class SquadDAOImpl implements SquadDAO {

    private final DBConnection db = DBConnection.getInstance();

    @Override
    public boolean addSquadMember(int teamId, int playerId, double soldPrice) throws SQLException {
        String sql = "INSERT INTO squads (team_id, player_id, sold_price) VALUES (?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, teamId);
            stmt.setInt(2, playerId);
            stmt.setDouble(3, Math.round(soldPrice * 100.0) / 100.0);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public boolean removeSquadMember(int teamId, int playerId) throws SQLException {
        String sql = "DELETE FROM squads WHERE team_id = ? AND player_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, teamId);
            stmt.setInt(2, playerId);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public List<SquadEntry> getSquadByTeam(int teamId) throws SQLException {
        String sql = "SELECT s.team_id, s.player_id, s.sold_price, p.* " +
                     "FROM squads s " +
                     "JOIN players p ON s.player_id = p.player_id " +
                     "WHERE s.team_id = ? " +
                     "ORDER BY s.sold_price DESC";
        List<SquadEntry> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, teamId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Player p = PlayerFactory.fromResultSet(rs);
                    double soldPrice = rs.getDouble("sold_price");
                    SquadEntry entry = new SquadEntry(teamId, p.getPlayerId(), soldPrice, p);
                    list.add(entry);
                }
            }
        }
        return list;
    }

    @Override
    public void clearSquads() throws SQLException {
        String sql = "DELETE FROM squads";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }
}

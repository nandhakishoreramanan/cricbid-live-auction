package com.cricbid.dao.impl;

import com.cricbid.dao.BidDAO;
import com.cricbid.db.DBConnection;
import com.cricbid.model.Bid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;


public class BidDAOImpl implements BidDAO {

    private final DBConnection db = DBConnection.getInstance();

    @Override
    public int recordBid(Bid bid) throws SQLException {
        String sql = "INSERT INTO bids (player_id, team_id, amount, bid_time) VALUES (?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, bid.getPlayerId());
            stmt.setInt(2, bid.getTeamId());
            stmt.setDouble(3, bid.getAmount());
            stmt.setTimestamp(4, bid.getBidTime());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    bid.setBidId(id);
                    return id;
                }
            }
        }
        return -1;
    }

    @Override
    public List<Bid> getBidsForPlayer(int playerId) throws SQLException {
        String sql = "SELECT b.*, t.name AS team_name, p.name AS player_name " +
                     "FROM bids b " +
                     "JOIN teams t ON b.team_id = t.team_id " +
                     "JOIN players p ON b.player_id = p.player_id " +
                     "WHERE b.player_id = ? ORDER BY b.bid_id DESC";
        List<Bid> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Bid b = new Bid(
                            rs.getInt("bid_id"),
                            rs.getInt("player_id"),
                            rs.getInt("team_id"),
                            rs.getDouble("amount"),
                            rs.getTimestamp("bid_time")
                    );
                    b.setTeamName(rs.getString("team_name"));
                    b.setPlayerName(rs.getString("player_name"));
                    list.add(b);
                }
            }
        }
        return list;
    }

    @Override
    public Bid getHighestBidForPlayer(int playerId) throws SQLException {
        String sql = "SELECT b.*, t.name AS team_name, p.name AS player_name " +
                     "FROM bids b " +
                     "JOIN teams t ON b.team_id = t.team_id " +
                     "JOIN players p ON b.player_id = p.player_id " +
                     "WHERE b.player_id = ? ORDER BY b.amount DESC, b.bid_id DESC LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Bid b = new Bid(
                            rs.getInt("bid_id"),
                            rs.getInt("player_id"),
                            rs.getInt("team_id"),
                            rs.getDouble("amount"),
                            rs.getTimestamp("bid_time")
                    );
                    b.setTeamName(rs.getString("team_name"));
                    b.setPlayerName(rs.getString("player_name"));
                    return b;
                }
            }
        }
        return null;
    }

    @Override
    public void clearBids() throws SQLException {
        String sql = "DELETE FROM bids";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }
}

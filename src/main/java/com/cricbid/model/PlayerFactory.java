package com.cricbid.model;

import java.sql.ResultSet;
import java.sql.SQLException;


public class PlayerFactory {

    
    public static Player createPlayer(int playerId, String name, PlayerRole role, boolean overseas,
                                      double basePrice, int rating, double battingAvg, double strikeRate,
                                      double economy, int wickets, int dismissals, PlayerStatus status) {
        if (role == null) {
            role = PlayerRole.BATSMAN;
        }

        switch (role) {
            case BATSMAN:
                return new Batsman(playerId, name, overseas, basePrice, rating, battingAvg, strikeRate, status);
            case BOWLER:
                return new Bowler(playerId, name, overseas, basePrice, rating, economy, wickets, status);
            case ALLROUNDER:
                return new AllRounder(playerId, name, overseas, basePrice, rating, battingAvg, strikeRate, economy, wickets, status);
            case WICKETKEEPER:
                return new WicketKeeper(playerId, name, overseas, basePrice, rating, battingAvg, strikeRate, dismissals, status);
            default:
                throw new IllegalArgumentException("Unknown player role: " + role);
        }
    }

    
    public static Player fromResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt("player_id");
        String name = rs.getString("name");
        PlayerRole role = PlayerRole.fromString(rs.getString("role"));
        boolean isOverseas = rs.getBoolean("is_overseas");
        double basePrice = rs.getDouble("base_price");
        int rating = rs.getInt("rating");
        double battingAvg = rs.getDouble("batting_avg");
        double strikeRate = rs.getDouble("strike_rate");
        double economy = rs.getDouble("economy");
        int wickets = rs.getInt("wickets");
        int dismissals = rs.getInt("dismissals");
        PlayerStatus status = PlayerStatus.fromString(rs.getString("status"));

        return createPlayer(id, name, role, isOverseas, basePrice, rating,
                battingAvg, strikeRate, economy, wickets, dismissals, status);
    }
}

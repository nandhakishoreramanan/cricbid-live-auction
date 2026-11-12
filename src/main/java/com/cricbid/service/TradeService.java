package com.cricbid.service;

import com.cricbid.db.DBConnection;
import com.cricbid.exception.BudgetExceededException;
import com.cricbid.exception.CricBidException;
import com.cricbid.exception.OverseasLimitException;
import com.cricbid.model.AuctionEventType;
import com.cricbid.model.Player;
import com.cricbid.model.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;


public class TradeService {

    private final DBConnection db = DBConnection.getInstance();

    
    public void executeTrade(Team teamA, Player playerA, Team teamB, Player playerB, double cashFromAToB)
            throws CricBidException, SQLException {

        if (teamA == null || teamB == null || playerA == null || playerB == null) {
            throw new CricBidException("Trade participants and players cannot be null.");
        }
        if (teamA.getTeamId() == teamB.getTeamId()) {
            throw new CricBidException("Cannot trade between the same team.");
        }
        if (playerA.getPlayerId() == playerB.getPlayerId()) {
            throw new CricBidException("Cannot trade a player for themselves.");
        }

        
        
        if (playerB.isOverseas() && !playerA.isOverseas() && teamA.getOverseasCount() >= RuleValidator.MAX_OVERSEAS_PLAYERS) {
            throw new OverseasLimitException(String.format("Team '%s' would exceed the overseas limit of %d.",
                    teamA.getName(), RuleValidator.MAX_OVERSEAS_PLAYERS));
        }
        if (playerA.isOverseas() && !playerB.isOverseas() && teamB.getOverseasCount() >= RuleValidator.MAX_OVERSEAS_PLAYERS) {
            throw new OverseasLimitException(String.format("Team '%s' would exceed the overseas limit of %d.",
                    teamB.getName(), RuleValidator.MAX_OVERSEAS_PLAYERS));
        }

        
        double newPurseA = teamA.getPurseRemaining() - cashFromAToB;
        double newPurseB = teamB.getPurseRemaining() + cashFromAToB;

        if (newPurseA < 0) {
            throw new BudgetExceededException(String.format("Team '%s' cannot afford cash transfer of %.2f Cr.",
                    teamA.getName(), cashFromAToB));
        }
        if (newPurseB < 0) {
            throw new BudgetExceededException(String.format("Team '%s' cannot afford cash transfer.",
                    teamB.getName()));
        }

        
        Connection conn = null;
        boolean originalAutoCommit = true;
        try {
            conn = db.getConnection();
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false); 

            
            String deleteSql = "DELETE FROM squads WHERE team_id = ? AND player_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setInt(1, teamA.getTeamId());
                stmt.setInt(2, playerA.getPlayerId());
                stmt.executeUpdate();

                stmt.setInt(1, teamB.getTeamId());
                stmt.setInt(2, playerB.getPlayerId());
                stmt.executeUpdate();
            }

            
            String insertSql = "INSERT INTO squads (team_id, player_id, sold_price) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                
                stmt.setInt(1, teamA.getTeamId());
                stmt.setInt(2, playerB.getPlayerId());
                stmt.setDouble(3, playerB.getBasePrice());
                stmt.executeUpdate();

                
                stmt.setInt(1, teamB.getTeamId());
                stmt.setInt(2, playerA.getPlayerId());
                stmt.setDouble(3, playerA.getBasePrice());
                stmt.executeUpdate();
            }

            
            String purseSql = "UPDATE teams SET purse_remaining = ? WHERE team_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(purseSql)) {
                stmt.setDouble(1, Math.round(newPurseA * 100.0) / 100.0);
                stmt.setInt(2, teamA.getTeamId());
                stmt.executeUpdate();

                stmt.setDouble(1, Math.round(newPurseB * 100.0) / 100.0);
                stmt.setInt(2, teamB.getTeamId());
                stmt.executeUpdate();
            }

            
            String logSql = "INSERT INTO auction_log (event, details, log_time) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(logSql)) {
                stmt.setString(1, AuctionEventType.TRADE.name());
                String details = String.format("Trade: %s traded %s to %s for %s (Cash: %.2f Cr)",
                        teamA.getName(), playerA.getName(), teamB.getName(), playerB.getName(), cashFromAToB);
                stmt.setString(2, details.length() > 255 ? details.substring(0, 255) : details);
                stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                stmt.executeUpdate();
            }

            
            conn.commit();

            
            teamA.setPurseRemaining(newPurseA);
            teamB.setPurseRemaining(newPurseB);

        } catch (SQLException ex) {
            if (conn != null) {
                try {
                    conn.rollback(); 
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }
}

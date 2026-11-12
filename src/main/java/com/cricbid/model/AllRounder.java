package com.cricbid.model;


public class AllRounder extends Player {

    public AllRounder() {
        super();
        setRole(PlayerRole.ALLROUNDER);
    }

    public AllRounder(int playerId, String name, boolean overseas, double basePrice,
                      int rating, double battingAvg, double strikeRate, double economy,
                      int wickets, PlayerStatus status) {
        super(playerId, name, PlayerRole.ALLROUNDER, overseas, basePrice, rating,
              battingAvg, strikeRate, economy, wickets, 0, status);
    }

    @Override
    public double calculateValue() {
        
        double ratingComponent = (getRating() / 100.0) * 8.5;
        double battingScore = (getBattingAvg() / 40.0) * 2.5 + ((getStrikeRate() - 100.0) / 60.0) * 1.5;
        double bowlingScore = (getWickets() / 100.0) * 2.5 + Math.max(0, (9.0 - getEconomy()) * 0.5);
        double raw = ratingComponent + Math.max(0, battingScore) + Math.max(0, bowlingScore);
        return Math.max(getBasePrice(), Math.round(raw * 100.0) / 100.0);
    }

    @Override
    public String describe() {
        double runRate = (getStrikeRate() * 6.0) / 100.0;
        return String.format("All-Rounder | SR: %.1f (%.2f RPO) | Economy: %.2f RPO | Wkts: %d",
                getStrikeRate(), runRate, getEconomy(), getWickets());
    }
}

package com.cricbid.model;


public class Batsman extends Player {

    public Batsman() {
        super();
        setRole(PlayerRole.BATSMAN);
    }

    public Batsman(int playerId, String name, boolean overseas, double basePrice,
                   int rating, double battingAvg, double strikeRate, PlayerStatus status) {
        super(playerId, name, PlayerRole.BATSMAN, overseas, basePrice, rating,
              battingAvg, strikeRate, 0.0, 0, 0, status);
    }

    @Override
    public double calculateValue() {
        
        double ratingComponent = (getRating() / 100.0) * 8.0;
        double avgComponent = (getBattingAvg() / 50.0) * 4.0;
        double srComponent = ((getStrikeRate() - 100.0) / 60.0) * 3.0;
        double raw = ratingComponent + avgComponent + Math.max(0, srComponent);
        return Math.max(getBasePrice(), Math.round(raw * 100.0) / 100.0);
    }

    @Override
    public String describe() {
        return String.format("Specialist Batsman | Avg: %.2f | SR: %.2f | Rating: %d/100",
                getBattingAvg(), getStrikeRate(), getRating());
    }
}

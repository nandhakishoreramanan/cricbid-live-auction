package com.cricbid.model;


public class WicketKeeper extends Player {

    public WicketKeeper() {
        super();
        setRole(PlayerRole.WICKETKEEPER);
    }

    public WicketKeeper(int playerId, String name, boolean overseas, double basePrice,
                        int rating, double battingAvg, double strikeRate, int dismissals,
                        PlayerStatus status) {
        super(playerId, name, PlayerRole.WICKETKEEPER, overseas, basePrice, rating,
              battingAvg, strikeRate, 0.0, 0, dismissals, status);
    }

    @Override
    public double calculateValue() {
        
        double ratingComponent = (getRating() / 100.0) * 8.0;
        double dismissalsComponent = (getDismissals() / 80.0) * 3.0;
        double battingScore = (getBattingAvg() / 45.0) * 3.0;
        double raw = ratingComponent + dismissalsComponent + battingScore;
        return Math.max(getBasePrice(), Math.round(raw * 100.0) / 100.0);
    }

    @Override
    public String describe() {
        return String.format("Wicketkeeper | Dismissals: %d | Bat Avg: %.2f | SR: %.2f | Rating: %d/100",
                getDismissals(), getBattingAvg(), getStrikeRate(), getRating());
    }
}

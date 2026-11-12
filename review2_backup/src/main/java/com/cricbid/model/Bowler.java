package com.cricbid.model;


public class Bowler extends Player {

    public Bowler() {
        super();
        setRole(PlayerRole.BOWLER);
    }

    public Bowler(int playerId, String name, boolean overseas, double basePrice,
                  int rating, double economy, int wickets, PlayerStatus status) {
        super(playerId, name, PlayerRole.BOWLER, overseas, basePrice, rating,
              0.0, 0.0, economy, wickets, 0, status);
    }

    @Override
    public double calculateValue() {
        
        double ratingComponent = (getRating() / 100.0) * 8.0;
        double wicketComponent = (getWickets() / 120.0) * 4.0;
        double econBonus = Math.max(0, (9.0 - getEconomy()) * 0.8);
        double raw = ratingComponent + wicketComponent + econBonus;
        return Math.max(getBasePrice(), Math.round(raw * 100.0) / 100.0);
    }

    @Override
    public String describe() {
        return String.format("Specialist Bowler | Wkts: %d | Econ: %.2f | Rating: %d/100",
                getWickets(), getEconomy(), getRating());
    }
}

package com.cricbid.strategy;

import com.cricbid.model.Player;
import com.cricbid.model.Team;


public class AggressiveBot implements BiddingStrategy {

    @Override
    public boolean shouldBid(Team team, Player player, double currentPrice, double nextBidAmount) {
        if (team == null || player == null) return false;
        double maxWillingness = calculateMaxBid(team, player);
        return nextBidAmount <= maxWillingness && nextBidAmount <= team.getPurseRemaining();
    }

    @Override
    public double calculateMaxBid(Team team, Player player) {
        if (team == null || player == null) return 0.0;
        int rating = player.getRating();
        double base = player.getBasePrice();
        double purse = team.getPurseRemaining();

        
        double multiplier;
        if (rating >= 92) {
            multiplier = 5.0;
        } else if (rating >= 85) {
            multiplier = 3.5;
        } else if (rating >= 78) {
            multiplier = 2.0;
        } else {
            multiplier = 1.2;
        }

        double starBonus = (rating - 70) * 0.15;
        double valuation = (base * multiplier) + starBonus;

        
        int remainingSlots = team.getRemainingSlotsToMinSquad();
        double maxCap = (remainingSlots > 5) ? (purse * 0.45) : (purse / Math.max(1, remainingSlots));

        return Math.min(valuation, maxCap);
    }
}

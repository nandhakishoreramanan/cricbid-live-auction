package com.cricbid.strategy;

import com.cricbid.model.Player;
import com.cricbid.model.Team;


public class MoneyballBot implements BiddingStrategy {

    @Override
    public boolean shouldBid(Team team, Player player, double currentPrice, double nextBidAmount) {
        if (team == null || player == null) return false;
        double intrinsicValue = calculateMaxBid(team, player);
        
        return nextBidAmount <= intrinsicValue && nextBidAmount <= team.getPurseRemaining();
    }

    @Override
    public double calculateMaxBid(Team team, Player player) {
        if (team == null || player == null) return 0.0;
        
        double statisticalValue = player.calculateValue();

        
        int remainingSlots = team.getRemainingSlotsToMinSquad();
        double purse = team.getPurseRemaining();
        double safetyPursePerSlot = purse / Math.max(1, remainingSlots);

        
        return Math.min(statisticalValue, safetyPursePerSlot * 2.0);
    }
}

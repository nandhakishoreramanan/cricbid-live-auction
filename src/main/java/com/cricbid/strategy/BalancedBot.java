package com.cricbid.strategy;

import com.cricbid.model.Player;
import com.cricbid.model.PlayerRole;
import com.cricbid.model.Team;


public class BalancedBot implements BiddingStrategy {

    @Override
    public boolean shouldBid(Team team, Player player, double currentPrice, double nextBidAmount) {
        if (team == null || player == null) return false;
        double maxWillingness = calculateMaxBid(team, player);
        return nextBidAmount <= maxWillingness && nextBidAmount <= team.getPurseRemaining();
    }

    @Override
    public double calculateMaxBid(Team team, Player player) {
        if (team == null || player == null) return 0.0;
        int remainingSlots = team.getRemainingSlotsToMinSquad();
        if (remainingSlots <= 0) {
            remainingSlots = Math.max(1, 25 - team.getSquadSize());
        }

        double purse = team.getPurseRemaining();
        double avgBudgetPerSlot = purse / Math.max(1, remainingSlots);

        
        double needWeight = 1.0;
        if (player.getRole() == PlayerRole.WICKETKEEPER && team.getWicketKeeperCount() == 0) {
            needWeight += 1.2; 
        }
        if (player.getRole() == PlayerRole.BOWLER && team.getBowlerCount() < 5) {
            needWeight += 0.8; 
        }

        
        double qualityFactor = (player.getRating() / 100.0) * 1.5;

        double valuation = avgBudgetPerSlot * needWeight * qualityFactor;
        
        double cap = (player.getRating() >= 90) ? avgBudgetPerSlot * 3.5 : avgBudgetPerSlot * 2.2;

        return Math.max(player.getBasePrice(), Math.min(valuation, cap));
    }
}

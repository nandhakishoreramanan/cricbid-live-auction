package com.cricbid.strategy;

import com.cricbid.model.Player;
import com.cricbid.model.Team;


public interface BiddingStrategy {
    
    boolean shouldBid(Team team, Player player, double currentPrice, double nextBidAmount);

    
    double calculateMaxBid(Team team, Player player);
}

package com.cricbid.observer;

import com.cricbid.model.Bid;
import com.cricbid.model.Player;
import com.cricbid.model.Team;


public interface AuctionObserver {
    void onPlayerUpForAuction(Player player, double currentPrice, int roundDurationSec);
    void onBidPlaced(Bid bid, Team team, Player player);
    void onPlayerSold(Player player, Team team, double finalPrice);
    void onPlayerUnsold(Player player);
    void onTimerTick(int secondsRemaining);
    void onAuctionPaused(boolean isPaused);
    void onAuctionCompleted();
    void onRuleViolation(String message);
}

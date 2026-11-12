package com.cricbid.observer;

import com.cricbid.model.Bid;
import com.cricbid.model.Player;
import com.cricbid.model.Team;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public class AuctionSubject {
    private final List<AuctionObserver> observers = new CopyOnWriteArrayList<>();

    public void addObserver(AuctionObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeObserver(AuctionObserver observer) {
        observers.remove(observer);
    }

    public void notifyPlayerUpForAuction(Player player, double currentPrice, int roundDurationSec) {
        SwingUtilities.invokeLater(() -> {
            for (AuctionObserver observer : observers) {
                observer.onPlayerUpForAuction(player, currentPrice, roundDurationSec);
            }
        });
    }

    public void notifyBidPlaced(Bid bid, Team team, Player player) {
        SwingUtilities.invokeLater(() -> {
            for (AuctionObserver observer : observers) {
                observer.onBidPlaced(bid, team, player);
            }
        });
    }

    public void notifyPlayerSold(Player player, Team team, double finalPrice) {
        SwingUtilities.invokeLater(() -> {
            for (AuctionObserver observer : observers) {
                observer.onPlayerSold(player, team, finalPrice);
            }
        });
    }

    public void notifyPlayerUnsold(Player player) {
        SwingUtilities.invokeLater(() -> {
            for (AuctionObserver observer : observers) {
                observer.onPlayerUnsold(player);
            }
        });
    }

    public void notifyTimerTick(int secondsRemaining) {
        SwingUtilities.invokeLater(() -> {
            for (AuctionObserver observer : observers) {
                observer.onTimerTick(secondsRemaining);
            }
        });
    }

    public void notifyAuctionPaused(boolean isPaused) {
        SwingUtilities.invokeLater(() -> {
            for (AuctionObserver observer : observers) {
                observer.onAuctionPaused(isPaused);
            }
        });
    }

    public void notifyAuctionCompleted() {
        SwingUtilities.invokeLater(() -> {
            for (AuctionObserver observer : observers) {
                observer.onAuctionCompleted();
            }
        });
    }

    public void notifyRuleViolation(String message) {
        SwingUtilities.invokeLater(() -> {
            for (AuctionObserver observer : observers) {
                observer.onRuleViolation(message);
            }
        });
    }
}

package com.cricbid.model;

import java.io.Serializable;
import java.util.Objects;


public abstract class Player implements Serializable {
    private static final long serialVersionUID = 1L;
    private int playerId;
    private String name;
    private PlayerRole role;
    private boolean overseas;
    private double basePrice;
    private int rating; 
    private double battingAvg;
    private double strikeRate;
    private double economy;
    private int wickets;
    private int dismissals;
    private PlayerStatus status;

    public Player() {
        this.status = PlayerStatus.AVAILABLE;
    }

    public Player(int playerId, String name, PlayerRole role, boolean overseas,
                  double basePrice, int rating, double battingAvg, double strikeRate,
                  double economy, int wickets, int dismissals, PlayerStatus status) {
        setPlayerId(playerId);
        setName(name);
        setRole(role);
        setOverseas(overseas);
        setBasePrice(basePrice);
        setRating(rating);
        setBattingAvg(battingAvg);
        setStrikeRate(strikeRate);
        setEconomy(economy);
        setWickets(wickets);
        setDismissals(dismissals);
        setStatus(status != null ? status : PlayerStatus.AVAILABLE);
    }

    
    public abstract double calculateValue();

    
    public abstract String describe();

    

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Player name cannot be empty.");
        }
        this.name = name.trim();
    }

    public PlayerRole getRole() {
        return role;
    }

    public void setRole(PlayerRole role) {
        this.role = Objects.requireNonNull(role, "Player role cannot be null.");
    }

    public boolean isOverseas() {
        return overseas;
    }

    public void setOverseas(boolean overseas) {
        this.overseas = overseas;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(double basePrice) {
        if (basePrice < 0) {
            throw new IllegalArgumentException("Base price cannot be negative.");
        }
        this.basePrice = Math.round(basePrice * 100.0) / 100.0;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        if (rating < 1 || rating > 100) {
            throw new IllegalArgumentException("Rating must be between 1 and 100. Provided: " + rating);
        }
        this.rating = rating;
    }

    public double getBattingAvg() {
        return battingAvg;
    }

    public void setBattingAvg(double battingAvg) {
        this.battingAvg = Math.max(0.0, battingAvg);
    }

    public double getStrikeRate() {
        return strikeRate;
    }

    public void setStrikeRate(double strikeRate) {
        this.strikeRate = Math.max(0.0, strikeRate);
    }

    public double getEconomy() {
        return economy;
    }

    public void setEconomy(double economy) {
        this.economy = Math.max(0.0, economy);
    }

    public int getWickets() {
        return wickets;
    }

    public void setWickets(int wickets) {
        this.wickets = Math.max(0, wickets);
    }

    public int getDismissals() {
        return dismissals;
    }

    public void setDismissals(int dismissals) {
        this.dismissals = Math.max(0, dismissals);
    }

    public PlayerStatus getStatus() {
        return status;
    }

    public void setStatus(PlayerStatus status) {
        this.status = status != null ? status : PlayerStatus.AVAILABLE;
    }

    @Override
    public String toString() {
        return String.format("%s (%s%s) - Base: %.2f Cr | Rating: %d",
                name, role.getDisplayName(), (overseas ? " [Overseas]" : ""), basePrice, rating);
    }
}

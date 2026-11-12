package com.cricbid.model;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Objects;


public class Bid implements Serializable {
    private static final long serialVersionUID = 1L;
    private int bidId;
    private int playerId;
    private int teamId;
    private double amount;
    private Timestamp bidTime;
    private String teamName;
    private String playerName;

    public Bid() {
        this.bidTime = new Timestamp(System.currentTimeMillis());
    }

    public Bid(int playerId, int teamId, double amount) {
        this();
        this.playerId = playerId;
        this.teamId = teamId;
        this.amount = Math.round(amount * 100.0) / 100.0;
    }

    public Bid(int bidId, int playerId, int teamId, double amount, Timestamp bidTime) {
        this.bidId = bidId;
        this.playerId = playerId;
        this.teamId = teamId;
        this.amount = Math.round(amount * 100.0) / 100.0;
        this.bidTime = bidTime;
    }

    public int getBidId() {
        return bidId;
    }

    public void setBidId(int bidId) {
        this.bidId = bidId;
    }

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public int getTeamId() {
        return teamId;
    }

    public void setTeamId(int teamId) {
        this.teamId = teamId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = Math.round(amount * 100.0) / 100.0;
    }

    public Timestamp getBidTime() {
        return bidTime;
    }

    public void setBidTime(Timestamp bidTime) {
        this.bidTime = bidTime;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bid)) return false;
        Bid bid = (Bid) o;
        return bidId == bid.bidId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bidId);
    }

    @Override
    public String toString() {
        return String.format("[%s] Team %s bid %.2f Cr for %s",
                bidTime != null ? bidTime.toString().substring(11, 19) : "",
                teamName != null ? teamName : "ID:" + teamId,
                amount,
                playerName != null ? playerName : "Player #" + playerId);
    }
}

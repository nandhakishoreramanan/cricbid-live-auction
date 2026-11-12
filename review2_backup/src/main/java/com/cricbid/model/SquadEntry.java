package com.cricbid.model;

import java.io.Serializable;
import java.util.Objects;


public class SquadEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    private int teamId;
    private int playerId;
    private double soldPrice;
    private Player player;

    public SquadEntry() {}

    public SquadEntry(int teamId, int playerId, double soldPrice, Player player) {
        this.teamId = teamId;
        this.playerId = playerId;
        this.soldPrice = Math.round(soldPrice * 100.0) / 100.0;
        this.player = player;
    }

    public int getTeamId() {
        return teamId;
    }

    public void setTeamId(int teamId) {
        this.teamId = teamId;
    }

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public double getSoldPrice() {
        return soldPrice;
    }

    public void setSoldPrice(double soldPrice) {
        this.soldPrice = Math.round(soldPrice * 100.0) / 100.0;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
        if (player != null) {
            this.playerId = player.getPlayerId();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SquadEntry)) return false;
        SquadEntry that = (SquadEntry) o;
        return teamId == that.teamId && playerId == that.playerId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId, playerId);
    }

    @Override
    public String toString() {
        String pName = player != null ? player.getName() : "Player #" + playerId;
        return String.format("%s - %.2f Cr", pName, soldPrice);
    }
}

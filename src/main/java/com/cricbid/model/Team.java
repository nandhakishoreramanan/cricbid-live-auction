package com.cricbid.model;

import com.cricbid.service.RuleValidator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


public class Team implements Serializable {
    private static final long serialVersionUID = 1L;
    private int teamId;
    private String name;
    private double purseRemaining;
    private OwnerType ownerType;
    private BotStrategyType botStrategy;
    private final List<SquadEntry> squad;

    public Team() {
        this.squad = new ArrayList<>();
        this.purseRemaining = 100.00;
        this.ownerType = OwnerType.BOT;
    }

    public Team(int teamId, String name, double purseRemaining, OwnerType ownerType, BotStrategyType botStrategy) {
        this();
        setTeamId(teamId);
        setName(name);
        setPurseRemaining(purseRemaining);
        setOwnerType(ownerType);
        setBotStrategy(botStrategy);
    }

    

    public int getSquadSize() {
        return squad.size();
    }

    public int getBowlerCount() {
        int count = 0;
        for (SquadEntry entry : squad) {
            if (entry.getPlayer() != null && entry.getPlayer().getRole() == PlayerRole.BOWLER) {
                count++;
            }
        }
        return count;
    }

    public int getOverseasCount() {
        int count = 0;
        for (SquadEntry entry : squad) {
            if (entry.getPlayer() != null && entry.getPlayer().isOverseas()) {
                count++;
            }
        }
        return count;
    }

    public int getWicketKeeperCount() {
        int count = 0;
        for (SquadEntry entry : squad) {
            if (entry.getPlayer() != null && entry.getPlayer().getRole() == PlayerRole.WICKETKEEPER) {
                count++;
            }
        }
        return count;
    }

    public int getRemainingSlotsToMinSquad() {
        return Math.max(0, RuleValidator.getActiveMinSquadSize() - getSquadSize());
    }

    public double getSpentAmount() {
        double spent = 0;
        for (SquadEntry entry : squad) {
            spent += entry.getSoldPrice();
        }
        return Math.round(spent * 100.0) / 100.0;
    }

    public void addSquadEntry(SquadEntry entry) {
        if (entry != null && !squad.contains(entry)) {
            squad.add(entry);
        }
    }

    public void removeSquadEntry(int playerId) {
        squad.removeIf(entry -> entry.getPlayerId() == playerId);
    }

    public void clearSquad() {
        squad.clear();
    }

    public List<SquadEntry> getSquad() {
        return Collections.unmodifiableList(squad);
    }

    

    public int getTeamId() {
        return teamId;
    }

    public void setTeamId(int teamId) {
        this.teamId = teamId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "Team name cannot be null.").trim();
    }

    public double getPurseRemaining() {
        return purseRemaining;
    }

    public void setPurseRemaining(double purseRemaining) {
        this.purseRemaining = Math.round(purseRemaining * 100.0) / 100.0;
    }

    public OwnerType getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(OwnerType ownerType) {
        this.ownerType = ownerType != null ? ownerType : OwnerType.BOT;
    }

    public BotStrategyType getBotStrategy() {
        return botStrategy;
    }

    public void setBotStrategy(BotStrategyType botStrategy) {
        this.botStrategy = botStrategy;
    }

    public boolean isHuman() {
        return this.ownerType == OwnerType.HUMAN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Team)) return false;
        Team team = (Team) o;
        return teamId == team.teamId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId);
    }

    @Override
    public String toString() {
        return String.format("%s (Purse: %.2f Cr, Squad: %d/25%s)",
                name, purseRemaining, squad.size(), (isHuman() ? " [User]" : " [" + botStrategy + "]"));
    }
}

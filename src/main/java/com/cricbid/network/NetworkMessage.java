package com.cricbid.network;

import com.cricbid.model.Bid;
import com.cricbid.model.Player;
import com.cricbid.model.Team;

import java.io.Serializable;
import java.util.List;

public class NetworkMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final MessageType type;
    private Player player;
    private Team team;
    private Bid bid;
    private double amount;
    private int roundDurationSec;
    private int secondsRemaining;
    private boolean paused;
    private boolean running;
    private boolean roundHasBids;
    private boolean quickMode;
    private Integer teamId;
    private String username;
    private String text;
    private List<Player> upcomingPool;

    public NetworkMessage(MessageType type) {
        this.type = type;
    }

    public MessageType getType() {
        return type;
    }

    public Player getPlayer() {
        return player;
    }

    public NetworkMessage setPlayer(Player player) {
        this.player = player;
        return this;
    }

    public Team getTeam() {
        return team;
    }

    public NetworkMessage setTeam(Team team) {
        this.team = team;
        return this;
    }

    public Bid getBid() {
        return bid;
    }

    public NetworkMessage setBid(Bid bid) {
        this.bid = bid;
        return this;
    }

    public double getAmount() {
        return amount;
    }

    public NetworkMessage setAmount(double amount) {
        this.amount = amount;
        return this;
    }

    public int getRoundDurationSec() {
        return roundDurationSec;
    }

    public NetworkMessage setRoundDurationSec(int roundDurationSec) {
        this.roundDurationSec = roundDurationSec;
        return this;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public NetworkMessage setSecondsRemaining(int secondsRemaining) {
        this.secondsRemaining = secondsRemaining;
        return this;
    }

    public boolean isPaused() {
        return paused;
    }

    public NetworkMessage setPaused(boolean paused) {
        this.paused = paused;
        return this;
    }

    public boolean isRunning() {
        return running;
    }

    public NetworkMessage setRunning(boolean running) {
        this.running = running;
        return this;
    }

    public boolean isRoundHasBids() {
        return roundHasBids;
    }

    public NetworkMessage setRoundHasBids(boolean roundHasBids) {
        this.roundHasBids = roundHasBids;
        return this;
    }

    public boolean isQuickMode() {
        return quickMode;
    }

    public NetworkMessage setQuickMode(boolean quickMode) {
        this.quickMode = quickMode;
        return this;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public NetworkMessage setTeamId(Integer teamId) {
        this.teamId = teamId;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public NetworkMessage setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getText() {
        return text;
    }

    public NetworkMessage setText(String text) {
        this.text = text;
        return this;
    }

    public List<Player> getUpcomingPool() {
        return upcomingPool;
    }

    public NetworkMessage setUpcomingPool(List<Player> upcomingPool) {
        this.upcomingPool = upcomingPool;
        return this;
    }
}

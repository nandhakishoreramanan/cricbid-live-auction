package com.cricbid.model;


public enum PlayerRole {
    BATSMAN("Batsman"),
    BOWLER("Bowler"),
    ALLROUNDER("All-Rounder"),
    WICKETKEEPER("Wicketkeeper");

    private final String displayName;

    PlayerRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PlayerRole fromString(String roleStr) {
        if (roleStr == null) return BATSMAN;
        String clean = roleStr.trim().toUpperCase().replace("-", "").replace(" ", "");
        for (PlayerRole role : values()) {
            if (role.name().replace("_", "").equalsIgnoreCase(clean)) {
                return role;
            }
        }
        return BATSMAN;
    }
}

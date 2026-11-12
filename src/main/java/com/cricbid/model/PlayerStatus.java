package com.cricbid.model;


public enum PlayerStatus {
    AVAILABLE,
    SOLD,
    UNSOLD;

    public static PlayerStatus fromString(String statusStr) {
        if (statusStr == null) return AVAILABLE;
        try {
            return PlayerStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AVAILABLE;
        }
    }
}

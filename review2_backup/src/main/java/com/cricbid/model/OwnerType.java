package com.cricbid.model;


public enum OwnerType {
    HUMAN,
    BOT;

    public static OwnerType fromString(String str) {
        if (str == null) return BOT;
        try {
            return OwnerType.valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BOT;
        }
    }
}

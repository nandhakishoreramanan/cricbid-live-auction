package com.cricbid.model;


public enum UserRole {
    ADMIN,
    TEAM_OWNER;

    public static UserRole fromString(String str) {
        if (str == null) return TEAM_OWNER;
        try {
            return UserRole.valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TEAM_OWNER;
        }
    }
}

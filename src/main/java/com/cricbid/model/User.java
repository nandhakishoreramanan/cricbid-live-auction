package com.cricbid.model;

import java.io.Serializable;
import java.util.Objects;


public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    private int userId;
    private String username;
    private String passwordHash;
    private UserRole role;
    private Integer teamId; 

    public User() {}

    public User(int userId, String username, String passwordHash, UserRole role, Integer teamId) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role != null ? role : UserRole.TEAM_OWNER;
        this.teamId = teamId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return userId == user.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", username, role);
    }
}

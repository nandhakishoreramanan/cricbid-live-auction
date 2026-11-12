package com.cricbid.service;

import com.cricbid.dao.UserDAO;
import com.cricbid.dao.impl.UserDAOImpl;
import com.cricbid.exception.AuthenticationException;
import com.cricbid.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;


public class AuthService {
    private final UserDAO userDAO;
    private User currentUser;

    public AuthService() {
        this.userDAO = new UserDAOImpl();
    }

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    
    public static String hashPassword(String password) {
        if (password == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }

    
    public User login(String username, String password) throws AuthenticationException, SQLException {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new AuthenticationException("Username and password cannot be empty.");
        }

        String hashed = hashPassword(password);
        User user = userDAO.authenticate(username.trim(), hashed);
        if (user == null) {
            throw new AuthenticationException("Invalid username or password.");
        }

        this.currentUser = user;
        return user;
    }

    public void logout() {
        this.currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }
}

package com.cricbid.dao;

import com.cricbid.model.User;

import java.sql.SQLException;


public interface UserDAO {
    User findByUsername(String username) throws SQLException;
    User authenticate(String username, String passwordHash) throws SQLException;
}

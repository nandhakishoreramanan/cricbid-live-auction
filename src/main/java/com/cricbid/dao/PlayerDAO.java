package com.cricbid.dao;

import com.cricbid.model.Player;
import com.cricbid.model.PlayerRole;
import com.cricbid.model.PlayerStatus;

import java.sql.SQLException;
import java.util.List;


public interface PlayerDAO {
    List<Player> findAll() throws SQLException;
    Player findById(int playerId) throws SQLException;
    List<Player> findByStatus(PlayerStatus status) throws SQLException;
    List<Player> findByRole(PlayerRole role) throws SQLException;
    boolean updateStatus(int playerId, PlayerStatus status) throws SQLException;
    void resetAllStatus() throws SQLException;
    int countAvailable() throws SQLException;
}

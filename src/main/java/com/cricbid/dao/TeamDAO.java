package com.cricbid.dao;

import com.cricbid.model.BotStrategyType;
import com.cricbid.model.OwnerType;
import com.cricbid.model.Team;

import java.sql.SQLException;
import java.util.List;


public interface TeamDAO {
    List<Team> findAll() throws SQLException;
    Team findById(int teamId) throws SQLException;
    boolean updatePurse(int teamId, double newPurse) throws SQLException;
    boolean updateStrategy(int teamId, BotStrategyType strategy) throws SQLException;
    boolean updateOwnerType(int teamId, OwnerType ownerType) throws SQLException;
    void resetAllPurse(double initialPurse) throws SQLException;
}

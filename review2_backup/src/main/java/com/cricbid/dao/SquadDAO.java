package com.cricbid.dao;

import com.cricbid.model.SquadEntry;

import java.sql.SQLException;
import java.util.List;


public interface SquadDAO {
    boolean addSquadMember(int teamId, int playerId, double soldPrice) throws SQLException;
    boolean removeSquadMember(int teamId, int playerId) throws SQLException;
    List<SquadEntry> getSquadByTeam(int teamId) throws SQLException;
    void clearSquads() throws SQLException;
}

package com.cricbid.service;

import com.cricbid.dao.SquadDAO;
import com.cricbid.dao.TeamDAO;
import com.cricbid.dao.impl.SquadDAOImpl;
import com.cricbid.dao.impl.TeamDAOImpl;
import com.cricbid.model.BotStrategyType;
import com.cricbid.model.OwnerType;
import com.cricbid.model.SquadEntry;
import com.cricbid.model.Team;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;


public class TeamService {
    private final TeamDAO teamDAO;
    private final SquadDAO squadDAO;
    private final RuleValidator ruleValidator;

    public TeamService() {
        this.teamDAO = new TeamDAOImpl();
        this.squadDAO = new SquadDAOImpl();
        this.ruleValidator = new RuleValidator();
    }

    public TeamService(TeamDAO teamDAO, SquadDAO squadDAO) {
        this.teamDAO = teamDAO;
        this.squadDAO = squadDAO;
        this.ruleValidator = new RuleValidator();
    }

    
    public List<Team> getAllTeamsWithSquads() throws SQLException {
        List<Team> teams = teamDAO.findAll();
        for (Team team : teams) {
            loadSquadForTeam(team);
        }
        return teams;
    }

    
    public void loadSquadForTeam(Team team) throws SQLException {
        if (team == null) return;
        team.clearSquad();
        List<SquadEntry> entries = squadDAO.getSquadByTeam(team.getTeamId());
        for (SquadEntry entry : entries) {
            team.addSquadEntry(entry);
        }
    }

    public Team getTeamById(int teamId) throws SQLException {
        Team team = teamDAO.findById(teamId);
        if (team != null) {
            loadSquadForTeam(team);
        }
        return team;
    }

    public boolean updateBotStrategy(int teamId, BotStrategyType strategy) throws SQLException {
        return teamDAO.updateStrategy(teamId, strategy);
    }

    public void updatePurse(int teamId, double newPurse) throws SQLException {
        teamDAO.updatePurse(teamId, newPurse);
    }

    public void updateOwnerType(int teamId, OwnerType ownerType) throws SQLException {
        teamDAO.updateOwnerType(teamId, ownerType);
    }

    public void resetAllTeams(double startingPurse, int humanTeamId) throws SQLException {
        resetAllTeams(startingPurse, Collections.singleton(humanTeamId));
    }

    public void resetAllTeams(double startingPurse, Set<Integer> humanTeamIds) throws SQLException {
        squadDAO.clearSquads();
        List<Team> teams = teamDAO.findAll();
        for (Team t : teams) {
            OwnerType type = (humanTeamIds != null && humanTeamIds.contains(t.getTeamId()))
                    ? OwnerType.HUMAN : OwnerType.BOT;
            t.setPurseRemaining(startingPurse);
            t.setOwnerType(type);
            teamDAO.updatePurse(t.getTeamId(), startingPurse);
            teamDAO.updateOwnerType(t.getTeamId(), type);
        }
    }

    public RuleValidator getRuleValidator() {
        return ruleValidator;
    }
}

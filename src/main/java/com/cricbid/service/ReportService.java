package com.cricbid.service;

import com.cricbid.dao.SquadDAO;
import com.cricbid.dao.TeamDAO;
import com.cricbid.dao.impl.SquadDAOImpl;
import com.cricbid.dao.impl.TeamDAOImpl;
import com.cricbid.model.Player;
import com.cricbid.model.PlayerRole;
import com.cricbid.model.SquadEntry;
import com.cricbid.model.Team;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ReportService {

    private final TeamDAO teamDAO;
    private final SquadDAO squadDAO;

    public ReportService() {
        this.teamDAO = new TeamDAOImpl();
        this.squadDAO = new SquadDAOImpl();
    }

    public ReportService(TeamDAO teamDAO, SquadDAO squadDAO) {
        this.teamDAO = teamDAO;
        this.squadDAO = squadDAO;
    }

    
    public Map<String, Double> getSpendingByTeam() throws SQLException {
        Map<String, Double> result = new HashMap<>();
        List<Team> teams = teamDAO.findAll();
        for (Team team : teams) {
            List<SquadEntry> squad = squadDAO.getSquadByTeam(team.getTeamId());
            double spent = squad.stream().mapToDouble(SquadEntry::getSoldPrice).sum();
            result.put(team.getName(), Math.round(spent * 100.0) / 100.0);
        }
        return result;
    }

    
    public Map<PlayerRole, Integer> getRoleDistribution() throws SQLException {
        Map<PlayerRole, Integer> distribution = new EnumMap<>(PlayerRole.class);
        for (PlayerRole role : PlayerRole.values()) {
            distribution.put(role, 0);
        }

        List<Team> teams = teamDAO.findAll();
        for (Team team : teams) {
            List<SquadEntry> squad = squadDAO.getSquadByTeam(team.getTeamId());
            for (SquadEntry entry : squad) {
                if (entry.getPlayer() != null) {
                    PlayerRole role = entry.getPlayer().getRole();
                    distribution.put(role, distribution.getOrDefault(role, 0) + 1);
                }
            }
        }
        return distribution;
    }

    
    public List<SquadEntry> getTopBuys(int limit) throws SQLException {
        List<SquadEntry> allBuys = new ArrayList<>();
        List<Team> teams = teamDAO.findAll();
        for (Team team : teams) {
            allBuys.addAll(squadDAO.getSquadByTeam(team.getTeamId()));
        }
        allBuys.sort(Comparator.comparingDouble(SquadEntry::getSoldPrice).reversed());
        return allBuys.stream().limit(limit).collect(java.util.stream.Collectors.toList());
    }

    
    public SquadEntry getMostExpensivePlayer() throws SQLException {
        List<SquadEntry> top = getTopBuys(1);
        return top.isEmpty() ? null : top.get(0);
    }

    
    public SquadEntry getBestValueBuy() throws SQLException {
        List<SquadEntry> allBuys = new ArrayList<>();
        List<Team> teams = teamDAO.findAll();
        for (Team team : teams) {
            allBuys.addAll(squadDAO.getSquadByTeam(team.getTeamId()));
        }
        return allBuys.stream()
                .filter(e -> e.getPlayer() != null && e.getSoldPrice() > 0)
                .max(Comparator.comparingDouble(e -> (e.getPlayer().getRating() / e.getSoldPrice())))
                .orElse(null);
    }

    
    public void exportAuctionResultsToCSV(File targetFile) throws IOException, SQLException {
        List<Team> teams = teamDAO.findAll();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile))) {
            writer.write("Team,Player ID,Player Name,Role,Overseas,Rating,Sold Price (Cr),Base Price (Cr)");
            writer.newLine();

            for (Team team : teams) {
                List<SquadEntry> squad = squadDAO.getSquadByTeam(team.getTeamId());
                for (SquadEntry entry : squad) {
                    Player p = entry.getPlayer();
                    if (p != null) {
                        writer.write(String.format("\"%s\",%d,\"%s\",\"%s\",%b,%d,%.2f,%.2f",
                                team.getName(),
                                p.getPlayerId(),
                                p.getName().replace("\"", "\"\""),
                                p.getRole().name(),
                                p.isOverseas(),
                                p.getRating(),
                                entry.getSoldPrice(),
                                p.getBasePrice()));
                        writer.newLine();
                    }
                }
            }
        }
    }
}

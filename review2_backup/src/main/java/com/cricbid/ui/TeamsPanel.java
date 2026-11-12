package com.cricbid.ui;

import com.cricbid.model.Player;
import com.cricbid.model.PlayerRole;
import com.cricbid.model.SquadEntry;
import com.cricbid.model.Team;
import com.cricbid.service.RuleValidator;
import com.cricbid.service.TeamService;
import com.cricbid.ui.renderer.SquadListCellRenderer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.List;


public class TeamsPanel extends JPanel {

    private final TeamService teamService;
    private final JTabbedPane tabbedPane;

    public TeamsPanel(TeamService teamService) {
        this.teamService = teamService;
        setLayout(new BorderLayout());

        tabbedPane = new JTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);
    }

    public void refreshTeams() {
        tabbedPane.removeAll();
        try {
            List<Team> teams = teamService.getAllTeamsWithSquads();
            for (Team team : teams) {
                tabbedPane.addTab(team.getName(), createTeamTab(team));
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load team squads: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createTeamTab(Team team) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        
        JPanel statsHeader = new JPanel(new GridLayout(2, 4, 10, 6));
        statsHeader.setBorder(BorderFactory.createTitledBorder("Franchise Balance & Quotas"));

        JLabel purseLabel = new JLabel(String.format("Purse Left: %.2f Cr", team.getPurseRemaining()));
        purseLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        purseLabel.setForeground(new Color(25, 120, 40));

        int minSquad = RuleValidator.getActiveMinSquadSize();
        int minBowlers = RuleValidator.getActiveMinBowlers();

        JLabel squadCountLabel = new JLabel(String.format("Squad Size: %d/25 (Min %d)", team.getSquadSize(), minSquad));
        squadCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JLabel overseasCountLabel = new JLabel(String.format("Overseas: %d/8", team.getOverseasCount()));
        overseasCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        if (team.getOverseasCount() > 8) overseasCountLabel.setForeground(Color.RED);

        JLabel bowlerCountLabel = new JLabel(String.format("Bowlers: %d (Min %d)", team.getBowlerCount(), minBowlers));
        bowlerCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        if (team.getBowlerCount() < minBowlers) bowlerCountLabel.setForeground(new Color(180, 40, 40));

        JLabel keeperCountLabel = new JLabel(String.format("Keepers: %d (Min 1)", team.getWicketKeeperCount()));
        keeperCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        if (team.getWicketKeeperCount() < 1) keeperCountLabel.setForeground(new Color(180, 40, 40));

        JLabel ownerTypeLabel = new JLabel("Control: " + (team.isHuman() ? "HUMAN" : "BOT (" + team.getBotStrategy() + ")"));
        ownerTypeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        
        int purseUsedPercent = (int) Math.min(100, Math.round((team.getSpentAmount() / 100.0) * 100.0));
        JProgressBar purseProgressBar = new JProgressBar(0, 100);
        purseProgressBar.setValue(purseUsedPercent);
        purseProgressBar.setStringPainted(true);
        purseProgressBar.setString(String.format("Purse Used: %d%% (%.2f Cr)", purseUsedPercent, team.getSpentAmount()));
        purseProgressBar.setForeground(new Color(45, 95, 175));

        statsHeader.add(purseLabel);
        statsHeader.add(squadCountLabel);
        statsHeader.add(overseasCountLabel);
        statsHeader.add(bowlerCountLabel);
        statsHeader.add(keeperCountLabel);
        statsHeader.add(ownerTypeLabel);
        statsHeader.add(new JLabel("Budget Utilization:"));
        statsHeader.add(purseProgressBar);

        panel.add(statsHeader, BorderLayout.NORTH);

        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.4);

        
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBorder(BorderFactory.createTitledBorder("Roster List"));
        DefaultListModel<SquadEntry> listModel = new DefaultListModel<>();
        for (SquadEntry e : team.getSquad()) {
            listModel.addElement(e);
        }
        JList<SquadEntry> squadList = new JList<>(listModel);
        squadList.setCellRenderer(new SquadListCellRenderer());
        listPanel.add(new JScrollPane(squadList), BorderLayout.CENTER);
        splitPane.setLeftComponent(listPanel);

        
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Contract Details Table"));

        String[] cols = {"Player ID", "Player Name", "Role", "Overseas", "Rating", "Sold Price (Cr)"};
        DefaultTableModel tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        for (SquadEntry e : team.getSquad()) {
            Player p = e.getPlayer();
            if (p != null) {
                tableModel.addRow(new Object[]{
                        p.getPlayerId(),
                        p.getName(),
                        p.getRole().getDisplayName(),
                        p.isOverseas() ? "Yes" : "No",
                        p.getRating(),
                        String.format("%.2f", e.getSoldPrice())
                });
            }
        }
        JTable purchasesTable = new JTable(tableModel);
        purchasesTable.setRowHeight(22);
        tablePanel.add(new JScrollPane(purchasesTable), BorderLayout.CENTER);
        splitPane.setRightComponent(tablePanel);

        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }
}

package com.cricbid.ui;

import com.cricbid.exception.CricBidException;
import com.cricbid.model.Player;
import com.cricbid.model.SquadEntry;
import com.cricbid.model.Team;
import com.cricbid.service.TeamService;
import com.cricbid.service.TradeService;
import com.cricbid.ui.renderer.SquadListCellRenderer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class TradeWindowPanel extends JPanel {

    private final TeamService teamService;
    private final TradeService tradeService;

    private JComboBox<String> teamACombo;
    private JComboBox<String> teamBCombo;

    private JList<SquadEntry> listA;
    private JList<SquadEntry> listB;
    private DefaultListModel<SquadEntry> modelA;
    private DefaultListModel<SquadEntry> modelB;

    private JSpinner cashSpinner;
    private JButton executeTradeBtn;

    private final List<Team> teams = new ArrayList<>();

    public TradeWindowPanel(TeamService teamService, TradeService tradeService) {
        this.teamService = teamService;
        this.tradeService = tradeService;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        initComponents();
    }

    private void initComponents() {
        
        JPanel centerGrid = new JPanel(new GridLayout(1, 2, 16, 0));

        
        JPanel panelA = new JPanel(new BorderLayout(6, 6));
        panelA.setBorder(BorderFactory.createTitledBorder("Team A (Sending Player)"));
        teamACombo = new JComboBox<>();
        teamACombo.addActionListener(e -> updateListA());
        panelA.add(teamACombo, BorderLayout.NORTH);

        modelA = new DefaultListModel<>();
        listA = new JList<>(modelA);
        listA.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listA.setCellRenderer(new SquadListCellRenderer());
        panelA.add(new JScrollPane(listA), BorderLayout.CENTER);
        centerGrid.add(panelA);

        
        JPanel panelB = new JPanel(new BorderLayout(6, 6));
        panelB.setBorder(BorderFactory.createTitledBorder("Team B (Receiving Player)"));
        teamBCombo = new JComboBox<>();
        teamBCombo.addActionListener(e -> updateListB());
        panelB.add(teamBCombo, BorderLayout.NORTH);

        modelB = new DefaultListModel<>();
        listB = new JList<>(modelB);
        listB.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listB.setCellRenderer(new SquadListCellRenderer());
        panelB.add(new JScrollPane(listB), BorderLayout.CENTER);
        centerGrid.add(panelB);

        add(centerGrid, BorderLayout.CENTER);

        
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        bottomPanel.add(new JLabel("Cash from Team A to Team B (Cr):"));
        cashSpinner = new JSpinner(new SpinnerNumberModel(0.0, -50.0, 50.0, 0.25));
        cashSpinner.setPreferredSize(new Dimension(100, 30));
        bottomPanel.add(cashSpinner);

        executeTradeBtn = new JButton("▶  Propose & Execute Trade");
        executeTradeBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        executeTradeBtn.setBackground(new Color(245, 158, 11));
        executeTradeBtn.setForeground(new Color(15, 23, 42));
        executeTradeBtn.addActionListener(e -> handleTradeExecution());
        bottomPanel.add(executeTradeBtn);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void loadTeams() {
        try {
            teams.clear();
            teams.addAll(teamService.getAllTeamsWithSquads());

            teamACombo.removeAllItems();
            teamBCombo.removeAllItems();

            for (Team t : teams) {
                teamACombo.addItem(t.getName());
                teamBCombo.addItem(t.getName());
            }

            if (teams.size() > 1) {
                teamBCombo.setSelectedIndex(1);
            }

            updateListA();
            updateListB();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load trade rosters: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateListA() {
        int idx = teamACombo.getSelectedIndex();
        modelA.clear();
        if (idx >= 0 && idx < teams.size()) {
            Team t = teams.get(idx);
            for (SquadEntry e : t.getSquad()) {
                modelA.addElement(e);
            }
        }
    }

    private void updateListB() {
        int idx = teamBCombo.getSelectedIndex();
        modelB.clear();
        if (idx >= 0 && idx < teams.size()) {
            Team t = teams.get(idx);
            for (SquadEntry e : t.getSquad()) {
                modelB.addElement(e);
            }
        }
    }

    private void handleTradeExecution() {
        int idxA = teamACombo.getSelectedIndex();
        int idxB = teamBCombo.getSelectedIndex();

        if (idxA < 0 || idxB < 0 || idxA == idxB) {
            JOptionPane.showMessageDialog(this, "Please select two different teams for the trade.",
                    "Invalid Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        SquadEntry entryA = listA.getSelectedValue();
        SquadEntry entryB = listB.getSelectedValue();

        if (entryA == null || entryB == null) {
            JOptionPane.showMessageDialog(this, "Please select one player from each team to swap.",
                    "Selection Missing", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Team teamA = teams.get(idxA);
        Team teamB = teams.get(idxB);
        Player playerA = entryA.getPlayer();
        Player playerB = entryB.getPlayer();
        double cash = ((Double) cashSpinner.getValue());

        String message = String.format(
                "Confirm Trade Agreement:\n\n" +
                "%s sends: %s (%.2f Cr)\n" +
                "%s sends: %s (%.2f Cr)\n" +
                "Cash Adjustment: %.2f Cr from %s to %s\n\n" +
                "Do you wish to execute this trade transaction?",
                teamA.getName(), playerA.getName(), entryA.getSoldPrice(),
                teamB.getName(), playerB.getName(), entryB.getSoldPrice(),
                cash, teamA.getName(), teamB.getName());

        int choice = JOptionPane.showConfirmDialog(this, message, "Confirm Franchise Trade",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            try {
                tradeService.executeTrade(teamA, playerA, teamB, playerB, cash);
                JOptionPane.showMessageDialog(this, "Trade executed and recorded successfully!",
                        "Trade Successful", JOptionPane.INFORMATION_MESSAGE);
                loadTeams();
            } catch (CricBidException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Trade Rejected", JOptionPane.WARNING_MESSAGE);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Database transaction error: " + ex.getMessage(),
                        "DB Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}

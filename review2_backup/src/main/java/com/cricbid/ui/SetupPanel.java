package com.cricbid.ui;

import com.cricbid.db.DBConnection;
import com.cricbid.model.OwnerType;
import com.cricbid.model.Team;
import com.cricbid.model.User;
import com.cricbid.network.NetworkUtils;
import com.cricbid.service.AuctionService;
import com.cricbid.service.TeamService;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;


public class SetupPanel extends JPanel {

    private final AuctionService auctionService;
    private final TeamService teamService;
    private final Consumer<Boolean> onAuctionStarted;

    private JLabel hostInfoLabel;
    private JLabel connectedClientsLabel;
    private JLabel teamComboLabel;
    private JComboBox<String> teamComboBox;
    private JRadioButton fullModeRadio;
    private JRadioButton quickModeRadio;
    private JSlider timerSlider;
    private JSpinner purseSpinner;
    private JPanel humanTeamsPanel;
    private JCheckBox enableBotsCheckBox;

    private final List<JCheckBox> humanTeamCheckBoxes = new ArrayList<>();
    private final List<Team> loadedTeams = new ArrayList<>();
    private User currentUser;

    public SetupPanel(AuctionService auctionService, TeamService teamService, Consumer<Boolean> onAuctionStarted) {
        this.auctionService = auctionService;
        this.teamService = teamService;
        this.onAuctionStarted = onAuctionStarted;

        setLayout(new BorderLayout());
        setBackground(new Color(245, 247, 250));
        initComponents();
    }

    public void initUserSession(User user) {
        this.currentUser = user;
        boolean isAdmin = user != null && user.isAdmin();
        teamComboLabel.setVisible(!isAdmin);
        teamComboBox.setVisible(!isAdmin);

        String lanIp = NetworkUtils.getLocalLanIp();
        int port = DBConnection.getInstance().getServerPort();
        hostInfoLabel.setText(String.format("📡 Auctioneer Host Ready — Enter IP  %s  on Participant Laptops (Port %d)", lanIp, port));
        refreshTeamList();
    }

    public void updateConnectedClients(List<String> clientLabels) {
        if (clientLabels == null || clientLabels.isEmpty()) {
            connectedClientsLabel.setText("Connected Laptops (0): Waiting for owner1 / owner2 to log in...");
            connectedClientsLabel.setForeground(new Color(160, 95, 10));
        } else {
            connectedClientsLabel.setText(String.format("🟢 Connected Laptops (%d): %s",
                    clientLabels.size(), String.join("  |  ", clientLabels)));
            connectedClientsLabel.setForeground(new Color(20, 120, 45));
        }
    }

    private void initComponents() {
        JPanel container = new JPanel(new GridBagLayout());
        container.setBackground(Color.WHITE);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(215, 225, 235), 1),
                BorderFactory.createEmptyBorder(20, 28, 20, 28)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Auction Tournament Configuration (Host Controller)");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 19));
        titleLabel.setForeground(new Color(25, 45, 85));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        container.add(titleLabel, gbc);

        // Network status box for Multi-Laptop Review
        JPanel netBox = new JPanel(new GridLayout(2, 1, 4, 4));
        netBox.setBackground(new Color(238, 246, 255));
        netBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 210, 240)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        hostInfoLabel = new JLabel("📡 Auctioneer Host IP: " + NetworkUtils.getLocalLanIp());
        hostInfoLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        hostInfoLabel.setForeground(new Color(20, 65, 130));

        connectedClientsLabel = new JLabel("Connected Laptops (0): Waiting for owner1 / owner2 to log in...");
        connectedClientsLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        connectedClientsLabel.setForeground(new Color(160, 95, 10));

        netBox.add(hostInfoLabel);
        netBox.add(connectedClientsLabel);
        gbc.gridy = 1;
        container.add(netBox, gbc);

        // Optional single-player team combo (hidden for ADMIN Auctioneer)
        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        teamComboLabel = new JLabel("Select Your Human Team:");
        container.add(teamComboLabel, gbc);

        gbc.gridx = 1;
        teamComboBox = new JComboBox<>();
        teamComboBox.setPreferredSize(new Dimension(240, 28));
        container.add(teamComboBox, gbc);

        // Auction Mode radio buttons
        gbc.gridy = 3;
        gbc.gridx = 0;
        container.add(new JLabel("Auction Mode:"), gbc);

        gbc.gridx = 1;
        JPanel radioPanel = new JPanel();
        radioPanel.setOpaque(false);
        quickModeRadio = new JRadioButton("Quick Auction (Top 20 | Min Squad 8)", true);
        fullModeRadio = new JRadioButton("Full Auction (48 Players | Min Squad 18)");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(quickModeRadio);
        modeGroup.add(fullModeRadio);
        radioPanel.add(quickModeRadio);
        radioPanel.add(fullModeRadio);
        container.add(radioPanel, gbc);

        // Round timer slider
        gbc.gridy = 4;
        gbc.gridx = 0;
        JLabel sliderLabel = new JLabel("Round Timer: 15s");
        container.add(sliderLabel, gbc);

        gbc.gridx = 1;
        timerSlider = new JSlider(5, 30, 15);
        timerSlider.setMajorTickSpacing(5);
        timerSlider.setMinorTickSpacing(1);
        timerSlider.setPaintTicks(true);
        timerSlider.setPaintLabels(true);
        timerSlider.addChangeListener(e -> sliderLabel.setText(String.format("Round Timer: %ds", timerSlider.getValue())));
        container.add(timerSlider, gbc);

        // Starting purse spinner
        gbc.gridy = 5;
        gbc.gridx = 0;
        container.add(new JLabel("Starting Team Purse (Cr):"), gbc);

        gbc.gridx = 1;
        purseSpinner = new JSpinner(new SpinnerNumberModel(100.0, 50.0, 200.0, 5.0));
        purseSpinner.setPreferredSize(new Dimension(100, 28));
        container.add(purseSpinner, gbc);

        // Participating Human Franchises Checkboxes
        gbc.gridy = 6;
        gbc.gridx = 0;
        container.add(new JLabel("Participating Laptops:"), gbc);

        gbc.gridx = 1;
        humanTeamsPanel = new JPanel(new GridLayout(3, 2, 6, 4));
        humanTeamsPanel.setBorder(BorderFactory.createTitledBorder("Check Franchises Controlled by Human Laptops"));
        humanTeamsPanel.setOpaque(false);
        container.add(humanTeamsPanel, gbc);

        // Optional AI Bots checkbox (OFF by default for 3-laptop mode)
        gbc.gridy = 7;
        gbc.gridx = 0;
        container.add(new JLabel("AI Bots (Other Teams):"), gbc);

        gbc.gridx = 1;
        enableBotsCheckBox = new JCheckBox("Enable Automated AI Bots for Unchecked Teams (Leave OFF for Human-Only)", false);
        enableBotsCheckBox.setOpaque(false);
        container.add(enableBotsCheckBox, gbc);

        // Start button
        gbc.gridy = 8;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        JButton startBtn = new JButton("Start Live Multi-Laptop Auction");
        startBtn.setFont(new Font("SansSerif", Font.BOLD, 15));
        startBtn.setBackground(new Color(25, 120, 55));
        startBtn.setForeground(Color.BLACK);
        startBtn.setPreferredSize(new Dimension(260, 40));
        startBtn.addActionListener(e -> handleStartAuction());
        container.add(startBtn, gbc);

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(container);
        add(wrapper, BorderLayout.CENTER);
    }

    private void refreshTeamList() {
        try {
            loadedTeams.clear();
            loadedTeams.addAll(teamService.getAllTeamsWithSquads());
            teamComboBox.removeAllItems();
            humanTeamsPanel.removeAll();
            humanTeamCheckBoxes.clear();

            for (int i = 0; i < loadedTeams.size(); i++) {
                Team t = loadedTeams.get(i);
                teamComboBox.addItem(t.getName());

                // Default: Team 1 (owner1) and Team 2 (owner2) checked as Human
                boolean defaultHuman = (t.getOwnerType() == OwnerType.HUMAN) || (i < 2);
                JCheckBox cb = new JCheckBox(String.format("%s (owner%d)", t.getName(), t.getTeamId()), defaultHuman);
                cb.setOpaque(false);
                humanTeamCheckBoxes.add(cb);
                humanTeamsPanel.add(cb);
            }

            if (currentUser != null && currentUser.getTeamId() != null) {
                for (int i = 0; i < loadedTeams.size(); i++) {
                    if (loadedTeams.get(i).getTeamId() == currentUser.getTeamId()) {
                        teamComboBox.setSelectedIndex(i);
                        break;
                    }
                }
            }

            humanTeamsPanel.revalidate();
            humanTeamsPanel.repaint();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load teams: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleStartAuction() {
        Set<Integer> humanTeamIds = new HashSet<>();
        for (int i = 0; i < humanTeamCheckBoxes.size() && i < loadedTeams.size(); i++) {
            if (humanTeamCheckBoxes.get(i).isSelected()) {
                humanTeamIds.add(loadedTeams.get(i).getTeamId());
            }
        }

        if (currentUser != null && !currentUser.isAdmin()) {
            int selectedTeamIdx = teamComboBox.getSelectedIndex();
            if (selectedTeamIdx >= 0 && selectedTeamIdx < loadedTeams.size()) {
                humanTeamIds.add(loadedTeams.get(selectedTeamIdx).getTeamId());
            }
        }

        if (humanTeamIds.isEmpty() && !enableBotsCheckBox.isSelected()) {
            JOptionPane.showMessageDialog(this,
                    "Please select at least one Participating Human Franchise (e.g., Thunder Hawks & Iron Wolves).",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double startingPurse = ((Double) purseSpinner.getValue());
        int timerDuration = timerSlider.getValue();
        boolean isQuickMode = quickModeRadio.isSelected();
        boolean botsOn = enableBotsCheckBox.isSelected();

        try {
            teamService.resetAllTeams(startingPurse, humanTeamIds);
            new com.cricbid.dao.impl.PlayerDAOImpl().resetAllStatus();
            auctionService.setBotsEnabled(botsOn);

            if (onAuctionStarted != null) {
                onAuctionStarted.accept(isQuickMode);
            }

            auctionService.startAuction(timerDuration, isQuickMode);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to initiate auction in database: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

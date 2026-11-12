package com.cricbid.ui;

import com.cricbid.Main;
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
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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

    private final List<JCheckBox> humanTeamCheckBoxes = new ArrayList<>();
    private final List<Team> loadedTeams = new ArrayList<>();
    private User currentUser;

    public SetupPanel(AuctionService auctionService, TeamService teamService, Consumer<Boolean> onAuctionStarted) {
        this.auctionService = auctionService;
        this.teamService = teamService;
        this.onAuctionStarted = onAuctionStarted;

        setLayout(new BorderLayout());
        setBackground(new Color(11, 19, 43));
        initComponents();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        BufferedImage bg = Main.getStadiumBackgroundImage();
        if (bg != null) {
            g2.drawImage(bg, 0, 0, getWidth(), getHeight(), null);
            g2.setColor(new Color(7, 14, 30, 150));
            g2.fillRect(0, 0, getWidth(), getHeight());
        } else {
            GradientPaint gp = new GradientPaint(
                    0, 0, new Color(8, 15, 34),
                    getWidth(), getHeight(), new Color(20, 38, 78));
            g2.setPaint(gp);
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
        g2.dispose();
    }

    public void initUserSession(User user) {
        this.currentUser = user;
        boolean isAdmin = user != null && user.isAdmin();
        teamComboLabel.setVisible(!isAdmin);
        teamComboBox.setVisible(!isAdmin);

        String lanIp = NetworkUtils.getLocalLanIp();
        int port = DBConnection.getInstance().getServerPort();
        hostInfoLabel.setText(String.format("[HOST READY] App IP: %s:%d   |   Browser Web Portal: http://%s:8080", lanIp, port, lanIp));
        refreshTeamList();
    }

    public void updateConnectedClients(List<String> clientLabels) {
        if (clientLabels == null || clientLabels.isEmpty()) {
            connectedClientsLabel.setText("Connected Laptops (0): Waiting for owner1 / owner2 to log in...");
            connectedClientsLabel.setForeground(new Color(251, 191, 36));
        } else {
            connectedClientsLabel.setText(String.format("[ONLINE] Connected Laptops (%d): %s",
                    clientLabels.size(), String.join("  |  ", clientLabels)));
            connectedClientsLabel.setForeground(new Color(74, 222, 128));
        }
    }

    private void initComponents() {
        JPanel container = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(14, 24, 48, 232));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        container.setOpaque(false);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(245, 158, 11), 2),
                BorderFactory.createEmptyBorder(24, 34, 24, 34)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("*  AUCTION TOURNAMENT CONTROL CENTER (HOST)  *", SwingConstants.CENTER);
        titleLabel.setFont(Main.displayFont(Font.BOLD, 24));
        titleLabel.setForeground(new Color(251, 191, 36));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        container.add(titleLabel, gbc);

        // Network status box for Multi-Laptop Review
        JPanel netBox = new JPanel(new GridLayout(2, 1, 4, 4));
        netBox.setBackground(new Color(11, 19, 43));
        netBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(56, 189, 248), 1),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));
        hostInfoLabel = new JLabel("[HOST] Auctioneer Host IP: " + NetworkUtils.getLocalLanIp());
        hostInfoLabel.setFont(Main.uiFont(Font.BOLD, 13));
        hostInfoLabel.setForeground(new Color(56, 189, 248));

        connectedClientsLabel = new JLabel("Connected Laptops (0): Waiting for owner1 / owner2 to log in...");
        connectedClientsLabel.setFont(Main.uiFont(Font.BOLD, 12));
        connectedClientsLabel.setForeground(new Color(251, 191, 36));

        netBox.add(hostInfoLabel);
        netBox.add(connectedClientsLabel);
        gbc.gridy = 1;
        container.add(netBox, gbc);

        // Optional single-player team combo (hidden for ADMIN Auctioneer)
        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        teamComboLabel = createFieldLabel("Select Your Human Team:");
        container.add(teamComboLabel, gbc);

        gbc.gridx = 1;
        teamComboBox = new JComboBox<>();
        teamComboBox.setPreferredSize(new Dimension(240, 30));
        container.add(teamComboBox, gbc);

        // Auction Mode radio buttons
        gbc.gridy = 3;
        gbc.gridx = 0;
        container.add(createFieldLabel("Auction Mode:"), gbc);

        gbc.gridx = 1;
        JPanel radioPanel = new JPanel();
        radioPanel.setOpaque(false);
        quickModeRadio = new JRadioButton("Quick Auction (Top 20 | Min Squad 8)", true);
        quickModeRadio.setForeground(Color.WHITE);
        quickModeRadio.setFont(Main.uiFont(Font.BOLD, 12));
        quickModeRadio.setOpaque(false);

        fullModeRadio = new JRadioButton("Full Auction (48 Players | Min Squad 18)");
        fullModeRadio.setForeground(Color.WHITE);
        fullModeRadio.setFont(Main.uiFont(Font.BOLD, 12));
        fullModeRadio.setOpaque(false);

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(quickModeRadio);
        modeGroup.add(fullModeRadio);
        radioPanel.add(quickModeRadio);
        radioPanel.add(fullModeRadio);
        container.add(radioPanel, gbc);

        // Round timer slider
        gbc.gridy = 4;
        gbc.gridx = 0;
        JLabel sliderLabel = createFieldLabel("Round Timer: 15s");
        container.add(sliderLabel, gbc);

        gbc.gridx = 1;
        timerSlider = new JSlider(5, 30, 15);
        timerSlider.setOpaque(false);
        timerSlider.setForeground(new Color(203, 213, 225));
        timerSlider.setMajorTickSpacing(5);
        timerSlider.setMinorTickSpacing(1);
        timerSlider.setPaintTicks(true);
        timerSlider.setPaintLabels(true);
        timerSlider.addChangeListener(e -> sliderLabel.setText(String.format("Round Timer: %ds", timerSlider.getValue())));
        container.add(timerSlider, gbc);

        // Starting purse spinner
        gbc.gridy = 5;
        gbc.gridx = 0;
        container.add(createFieldLabel("Starting Team Purse (Cr):"), gbc);

        gbc.gridx = 1;
        purseSpinner = new JSpinner(new SpinnerNumberModel(100.0, 50.0, 200.0, 5.0));
        purseSpinner.setPreferredSize(new Dimension(110, 30));
        container.add(purseSpinner, gbc);

        // Participating Human Franchises Checkboxes
        gbc.gridy = 6;
        gbc.gridx = 0;
        container.add(createFieldLabel("Participating Laptops:"), gbc);

        gbc.gridx = 1;
        humanTeamsPanel = new JPanel(new GridLayout(3, 2, 8, 6));
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85), 1),
                "Select Franchises Controlled by Human Laptops");
        tb.setTitleColor(new Color(251, 191, 36));
        tb.setTitleFont(Main.uiFont(Font.BOLD, 12));
        humanTeamsPanel.setBorder(BorderFactory.createCompoundBorder(tb, BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        humanTeamsPanel.setBackground(new Color(15, 23, 42));
        container.add(humanTeamsPanel, gbc);

        // Start button
        gbc.gridy = 7;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        JButton startBtn = new JButton(">>  START LIVE MULTI-LAPTOP AUCTION  <<");
        startBtn.setFont(Main.displayFont(Font.BOLD, 18));
        startBtn.setBackground(new Color(245, 158, 11));
        startBtn.setForeground(new Color(15, 23, 42));
        startBtn.setPreferredSize(new Dimension(280, 44));
        startBtn.addActionListener(e -> handleStartAuction());
        container.add(startBtn, gbc);

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(container);
        add(wrapper, BorderLayout.CENTER);
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(Main.uiFont(Font.BOLD, 13));
        lbl.setForeground(new Color(226, 232, 240));
        return lbl;
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
                cb.setForeground(Color.WHITE);
                cb.setFont(Main.uiFont(Font.BOLD, 12));
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

        if (humanTeamIds.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select at least one Participating Human Franchise (e.g., Thunder Hawks & Iron Wolves).",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double startingPurse = ((Double) purseSpinner.getValue());
        int timerDuration = timerSlider.getValue();
        boolean isQuickMode = quickModeRadio.isSelected();

        try {
            teamService.resetAllTeams(startingPurse, humanTeamIds);
            new com.cricbid.dao.impl.PlayerDAOImpl().resetAllStatus();
            auctionService.setBotsEnabled(false);

            if (onAuctionStarted != null) {
                onAuctionStarted.accept(isQuickMode);
            }

            auctionService.startAuction(timerDuration, isQuickMode);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to initialize auction: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

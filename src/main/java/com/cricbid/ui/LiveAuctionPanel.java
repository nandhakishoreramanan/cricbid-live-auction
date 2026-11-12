package com.cricbid.ui;

import com.cricbid.Main;
import com.cricbid.db.DBConnection;
import com.cricbid.exception.CricBidException;
import com.cricbid.model.Bid;
import com.cricbid.model.Player;
import com.cricbid.model.PlayerRole;
import com.cricbid.model.Team;
import com.cricbid.model.User;
import com.cricbid.network.AuctionClient;
import com.cricbid.network.AuctionServer;
import com.cricbid.network.NetworkMessage;
import com.cricbid.network.NetworkUtils;
import com.cricbid.observer.AuctionObserver;
import com.cricbid.service.AuctionService;
import com.cricbid.service.RuleValidator;
import com.cricbid.service.SoundEffectService;
import com.cricbid.service.TeamService;
import com.cricbid.ui.dialog.SoldPopupDialog;
import com.cricbid.ui.renderer.PlayerTableRenderer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class LiveAuctionPanel extends JPanel implements AuctionObserver {

    private final AuctionService auctionService;
    private final TeamService teamService;
    private final JFrame parentFrame;

    private JPanel statusBannerPanel;
    private JLabel statusBannerLabel;
    private JPanel scoreboardStripPanel;

    private JLabel playerNameLabel;
    private JLabel playerRoleBadgeLabel;
    private JLabel playerOverseasBadgeLabel;
    private JLabel playerBasePriceLabel;
    private JTextArea playerStatsArea;
    private BroadcastStatsBarPanel statsBarPanel;
    private CircularTimerDial timerDial;

    private JProgressBar countdownProgressBar;
    private JLabel currentPriceLabel;
    private JLabel leadingTeamLabel;
    private JLabel userPurseTitleLabel;
    private JLabel userPurseLabel;
    private JLabel bidAmountTitleLabel;

    private JSpinner bidSpinner;
    private JPanel quickStepRow;
    private JPanel bidderBtnRow;
    private JButton placeBidBtn;
    private JButton passBtn;
    private JButton autoSuggestBtn;

    private JPanel adminControlRow;
    private JButton adminPauseResumeBtn;
    private JButton adminSkipBtn;
    private JToggleButton botToggle;

    private JTextArea bidLogTextArea;
    private JTextField chatInputField;
    private JTable upcomingPoolTable;
    private DefaultTableModel upcomingPoolModel;

    private Team humanTeam;
    private User currentUser;
    private AuctionClient auctionClient;
    private AuctionServer auctionServer;
    private Player activePlayer;
    private Integer activeLeadingTeamId = null;
    private int activeRoundDurationSec = 15;
    private int activeSecondsRemaining = 15;
    private List<Player> syncedUpcomingPool = null;

    public LiveAuctionPanel(JFrame parentFrame, AuctionService auctionService, TeamService teamService) {
        this.parentFrame = parentFrame;
        this.auctionService = auctionService;
        this.teamService = teamService;

        setLayout(new BorderLayout(0, 0));
        setBackground(new Color(11, 19, 43));
        initComponents();

        this.auctionService.getAuctionSubject().addObserver(this);
    }

    public void setAuctionServer(AuctionServer server) {
        this.auctionServer = server;
    }

    public void setSession(User user, Team team, AuctionClient client) {
        this.currentUser = user;
        this.humanTeam = team;
        this.auctionClient = client;

        boolean isAdmin = user != null && user.isAdmin();

        bidAmountTitleLabel.setVisible(!isAdmin);
        bidSpinner.setVisible(!isAdmin);
        if (quickStepRow != null) quickStepRow.setVisible(!isAdmin);
        bidderBtnRow.setVisible(!isAdmin);
        adminControlRow.setVisible(isAdmin);

        if (isAdmin) {
            userPurseTitleLabel.setText("Session Role:");
            userPurseLabel.setText("* Auctioneer Host (Controls Start / Pause / Next)");
            userPurseLabel.setForeground(new Color(56, 189, 248));
            updateHostBanner(null);
        } else {
            String teamTitle = (humanTeam != null) ? humanTeam.getName() : "Unassigned";
            userPurseTitleLabel.setText("Your Purse (" + teamTitle + "):");
            userPurseLabel.setForeground(new Color(74, 222, 128));
            updateUserPurseDisplay();
            if (client != null) {
                updateClientBanner(client.isConnected(),
                        "Connecting to Host " + client.getHost() + ":" + client.getPort() + "...");
            }
        }
        refreshScoreboardStrip();
    }

    public void updateHostBanner(List<String> connectedClients) {
        if (currentUser == null || !currentUser.isAdmin()) return;
        String lanIp = NetworkUtils.getLocalLanIp();
        int port = DBConnection.getInstance().getServerPort();
        String clientsInfo = (connectedClients == null || connectedClients.isEmpty())
                ? "0 Laptops Connected (Waiting for owner1 / owner2)"
                : connectedClients.size() + " Connected: " + String.join(", ", connectedClients);
        statusBannerPanel.setBackground(new Color(30, 58, 138));
        statusBannerLabel.setForeground(Color.WHITE);
        statusBannerLabel.setText(String.format("* HOST CONSOLE   |   App: %s:%d   |   Web Portal: http://%s:8080   |   %s", lanIp, port, lanIp, clientsInfo));
    }

    public void updateClientBanner(boolean connected, String statusMessage) {
        if (currentUser != null && currentUser.isAdmin()) return;
        String teamTitle = (humanTeam != null) ? humanTeam.getName() : "Spectator";
        if (connected) {
            statusBannerPanel.setBackground(new Color(20, 83, 45));
            statusBannerLabel.setForeground(Color.WHITE);
            statusBannerLabel.setText(String.format("* [LIVE] %s   |   Bidding Live as Franchise: %s", statusMessage, teamTitle));
        } else {
            statusBannerPanel.setBackground(new Color(146, 64, 14));
            statusBannerLabel.setForeground(Color.WHITE);
            statusBannerLabel.setText(String.format("* [CONNECTING] %s   |   Franchise: %s", statusMessage, teamTitle));
        }
    }

    public void setSyncedUpcomingPool(List<Player> upcoming) {
        this.syncedUpcomingPool = (upcoming != null) ? new ArrayList<>(upcoming) : new ArrayList<>();
        refreshUpcomingPoolTable();
    }

    public void handleAuctionReset(boolean quickMode) {
        RuleValidator.setQuickModeActive(quickMode);
        activeLeadingTeamId = null;
        bidLogTextArea.setText("");
        logMessage(String.format("=== NEW AUCTION STARTED (%s Mode | Min Squad: %d) ===",
                quickMode ? "Quick" : "Full", RuleValidator.getActiveMinSquadSize()));
        updateUserPurseDisplay();
        refreshScoreboardStrip();
    }

    public void applySyncState(NetworkMessage sync) {
        if (sync == null) return;
        RuleValidator.setQuickModeActive(sync.isQuickMode());
        if (sync.getUpcomingPool() != null) {
            this.syncedUpcomingPool = new ArrayList<>(sync.getUpcomingPool());
            refreshUpcomingPoolTable();
        }
        updateUserPurseDisplay();
        refreshScoreboardStrip();

        Player p = sync.getPlayer();
        if (!sync.isRunning() || p == null) {
            playerNameLabel.setText("Waiting for Admin to start auction...");
            leadingTeamLabel.setText("Connected - Standby for Round 1");
            return;
        }

        this.activePlayer = p;
        this.activeRoundDurationSec = Math.max(5, sync.getRoundDurationSec());
        populatePlayerCard(p, sync.getAmount(), activeRoundDurationSec);
        onTimerTick(sync.getSecondsRemaining());

        if (sync.isRoundHasBids() && sync.getTeam() != null) {
            Team lead = sync.getTeam();
            this.activeLeadingTeamId = lead.getTeamId();
            leadingTeamLabel.setText(formatTeamBadge(lead));
            double nextValid = RuleValidator.getNextValidBidAmount(sync.getAmount(), true);
            bidSpinner.setValue(nextValid);
            refreshScoreboardStrip();
        }
        if (sync.isPaused()) {
            logMessage("[SYNC] Joined live round (currently PAUSED).");
        } else {
            logMessage("[SYNC] Synced with live auction block: " + p.getName());
        }
    }

    public void appendExternalLog(String message) {
        logMessage(message);
    }

    public void showBidRejected(String reason) {
        SoundEffectService.playUnsoldTone();
        JOptionPane.showMessageDialog(this, reason, "Bid Rejected", JOptionPane.WARNING_MESSAGE);
    }

    private void initComponents() {
        // Top wrapper: Status Banner + Live Franchise Purse Scoreboard Strip
        JPanel northWrapper = new JPanel(new BorderLayout(0, 0));

        statusBannerPanel = new JPanel(new BorderLayout());
        statusBannerPanel.setBackground(new Color(30, 58, 138));
        statusBannerPanel.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        statusBannerLabel = new JLabel("* Live Auction Broadcast Arena");
        statusBannerLabel.setFont(Main.uiFont(Font.BOLD, 12));
        statusBannerLabel.setForeground(Color.WHITE);
        statusBannerPanel.add(statusBannerLabel, BorderLayout.CENTER);
        northWrapper.add(statusBannerPanel, BorderLayout.NORTH);

        scoreboardStripPanel = new JPanel(new GridLayout(1, 6, 8, 0));
        scoreboardStripPanel.setBackground(new Color(15, 23, 42));
        scoreboardStripPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 2, 0, new Color(30, 41, 59)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        northWrapper.add(scoreboardStripPanel, BorderLayout.SOUTH);

        add(northWrapper, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.56);
        splitPane.setDividerLocation(640);
        splitPane.setBackground(new Color(11, 19, 43));
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBackground(new Color(11, 19, 43));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 8));

        JPanel cardPanel = createPlayerCardPanel();
        leftPanel.add(cardPanel, BorderLayout.NORTH);

        JPanel actionPanel = createBiddingActionPanel();
        leftPanel.add(actionPanel, BorderLayout.CENTER);

        splitPane.setLeftComponent(leftPanel);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        rightSplit.setResizeWeight(0.54);
        rightSplit.setDividerLocation(340);
        rightSplit.setBackground(new Color(11, 19, 43));
        rightSplit.setBorder(BorderFactory.createEmptyBorder(10, 4, 10, 12));

        // Top-right: Live Bid Activity & Multi-Laptop Chat Console
        JPanel bidLogPanel = new JPanel(new BorderLayout(4, 4));
        bidLogPanel.setBackground(new Color(17, 28, 53));
        bidLogPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85), 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JLabel logHeader = new JLabel("[LIVE]  Live Bidding Feed & Multi-Laptop Chat");
        logHeader.setFont(Main.uiFont(Font.BOLD, 13));
        logHeader.setForeground(new Color(251, 191, 36));
        bidLogPanel.add(logHeader, BorderLayout.NORTH);

        bidLogTextArea = new JTextArea();
        bidLogTextArea.setEditable(false);
        bidLogTextArea.setFont(Main.monoFont(Font.PLAIN, 12));
        bidLogTextArea.setBackground(new Color(10, 16, 32));
        bidLogTextArea.setForeground(new Color(226, 232, 240));
        bidLogTextArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        bidLogPanel.add(new JScrollPane(bidLogTextArea), BorderLayout.CENTER);

        bidLogPanel.add(createChatAndReactionBar(), BorderLayout.SOUTH);
        rightSplit.setTopComponent(bidLogPanel);

        // Bottom-right: Upcoming Player Pool Table
        JPanel upcomingPanel = new JPanel(new BorderLayout(4, 4));
        upcomingPanel.setBackground(new Color(17, 28, 53));
        upcomingPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85), 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JLabel poolHeader = new JLabel(">>  Upcoming Player Pool Queue");
        poolHeader.setFont(Main.uiFont(Font.BOLD, 13));
        poolHeader.setForeground(new Color(251, 191, 36));
        upcomingPanel.add(poolHeader, BorderLayout.NORTH);

        String[] cols = {"ID", "Name", "Role", "Key Stats (SR / RPO / Econ)", "Rating", "Base (Cr)", "Status"};
        upcomingPoolModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        upcomingPoolTable = new JTable(upcomingPoolModel);
        upcomingPoolTable.setDefaultRenderer(Object.class, new PlayerTableRenderer());
        upcomingPoolTable.setBackground(new Color(15, 23, 42));
        upcomingPoolTable.setForeground(new Color(241, 245, 249));
        int[] colWidths = {34, 130, 82, 185, 52, 65, 82};
        for (int i = 0; i < colWidths.length && i < upcomingPoolTable.getColumnCount(); i++) {
            upcomingPoolTable.getColumnModel().getColumn(i).setPreferredWidth(colWidths[i]);
        }
        upcomingPanel.add(new JScrollPane(upcomingPoolTable), BorderLayout.CENTER);
        rightSplit.setBottomComponent(upcomingPanel);

        splitPane.setRightComponent(rightSplit);

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createChatAndReactionBar() {
        JPanel chatContainer = new JPanel(new BorderLayout(4, 4));
        chatContainer.setOpaque(false);

        // Quick 1-click banter reaction pills
        JPanel quickRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        quickRow.setOpaque(false);
        JLabel reactLbl = new JLabel("Quick Banter:");
        reactLbl.setFont(Main.uiFont(Font.BOLD, 12));
        reactLbl.setForeground(new Color(148, 163, 184));
        quickRow.add(reactLbl);

        String[] reactions = {"Steal!", "Too Pricey!", "All In!", "Great Buy!"};
        for (String r : reactions) {
            JButton rBtn = new JButton(r);
            rBtn.setFont(Main.uiFont(Font.BOLD, 12));
            rBtn.setBackground(new Color(30, 41, 59));
            rBtn.setForeground(new Color(251, 191, 36));
            rBtn.setMargin(new Insets(2, 8, 2, 8));
            rBtn.setFocusPainted(false);
            rBtn.addActionListener(e -> sendChatOrReaction(r));
            quickRow.add(rBtn);
        }
        chatContainer.add(quickRow, BorderLayout.NORTH);

        // Custom text chat input row
        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.setOpaque(false);
        chatInputField = new JTextField();
        chatInputField.setToolTipText("Type a message to broadcast to all connected laptops...");
        chatInputField.addActionListener(e -> {
            sendChatOrReaction(chatInputField.getText());
            chatInputField.setText("");
        });

        JButton sendBtn = new JButton("Send Chat");
        sendBtn.setFont(Main.uiFont(Font.BOLD, 12));
        sendBtn.setBackground(new Color(37, 99, 235));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFocusPainted(false);
        sendBtn.addActionListener(e -> {
            sendChatOrReaction(chatInputField.getText());
            chatInputField.setText("");
        });

        inputRow.add(chatInputField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        chatContainer.add(inputRow, BorderLayout.SOUTH);

        return chatContainer;
    }

    private void sendChatOrReaction(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String senderLabel;
        if (currentUser != null && currentUser.isAdmin()) {
            senderLabel = "Auctioneer Host";
        } else if (humanTeam != null) {
            senderLabel = humanTeam.getName();
        } else if (currentUser != null) {
            senderLabel = currentUser.getUsername();
        } else {
            senderLabel = "Participant";
        }

        if (auctionClient != null) {
            auctionClient.sendChatMessage(senderLabel, text.trim());
        } else if (auctionServer != null) {
            auctionServer.broadcastChatMessage(senderLabel, text.trim());
        } else {
            logMessage(String.format("[CHAT - %s]: %s", senderLabel, text.trim()));
        }
    }

    public void refreshScoreboardStrip() {
        if (scoreboardStripPanel == null || currentUser == null) return;
        scoreboardStripPanel.removeAll();
        try {
            List<Team> teams = teamService.getAllTeamsWithSquads();
            for (Team t : teams) {
                if (humanTeam != null && t.getTeamId() == humanTeam.getTeamId()) {
                    humanTeam.setPurseRemaining(t.getPurseRemaining());
                }
                boolean isLeading = (activeLeadingTeamId != null && activeLeadingTeamId == t.getTeamId());
                boolean isYou = (humanTeam != null && humanTeam.getTeamId() == t.getTeamId());

                JPanel badgeCard = new JPanel(new BorderLayout(2, 1));
                badgeCard.setBackground(isLeading ? new Color(30, 58, 138) : new Color(30, 41, 59));
                Color borderColor = isLeading
                        ? new Color(251, 191, 36)
                        : (isYou ? new Color(56, 189, 248) : new Color(51, 65, 85));
                badgeCard.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(borderColor, isLeading ? 2 : 1),
                        BorderFactory.createEmptyBorder(4, 7, 4, 7)
                ));

                String tag = isYou ? " [YOU]" : (t.isHuman() ? " [LIVE]" : "");
                JLabel nameLbl = new JLabel((isLeading ? "* " : "") + t.getName() + tag);
                nameLbl.setFont(Main.uiFont(Font.BOLD, 12));
                nameLbl.setForeground(isLeading ? new Color(251, 191, 36) : Color.WHITE);

                JLabel purseLbl = new JLabel(String.format("%.2f Cr  |  %d/25", t.getPurseRemaining(), t.getSquadSize()));
                purseLbl.setFont(Main.uiFont(Font.PLAIN, 11));
                purseLbl.setForeground(new Color(148, 163, 184));

                badgeCard.add(nameLbl, BorderLayout.NORTH);
                badgeCard.add(purseLbl, BorderLayout.SOUTH);
                scoreboardStripPanel.add(badgeCard);
            }
        } catch (SQLException ignored) {
        }
        scoreboardStripPanel.revalidate();
        scoreboardStripPanel.repaint();
    }

    private JPanel createPlayerCardPanel() {
        // Dark Navy & Gold Broadcast Spotlight Card
        JPanel panel = new JPanel(new BorderLayout(12, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, new Color(15, 23, 42), getWidth(), getHeight(), new Color(30, 58, 138)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(245, 158, 11), 2),
                BorderFactory.createEmptyBorder(14, 16, 12, 16)
        ));

        // Left/Center player spotlight details
        JPanel infoCol = new JPanel();
        infoCol.setLayout(new BoxLayout(infoCol, BoxLayout.Y_AXIS));
        infoCol.setOpaque(false);

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badgeRow.setOpaque(false);

        playerRoleBadgeLabel = new JLabel("AWAITING ROUND");
        playerRoleBadgeLabel.setOpaque(true);
        playerRoleBadgeLabel.setBackground(new Color(245, 158, 11));
        playerRoleBadgeLabel.setForeground(new Color(15, 23, 42));
        playerRoleBadgeLabel.setFont(Main.uiFont(Font.BOLD, 12));
        playerRoleBadgeLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        playerOverseasBadgeLabel = new JLabel("DOMESTIC");
        playerOverseasBadgeLabel.setOpaque(true);
        playerOverseasBadgeLabel.setBackground(new Color(30, 41, 59));
        playerOverseasBadgeLabel.setForeground(new Color(56, 189, 248));
        playerOverseasBadgeLabel.setFont(Main.uiFont(Font.BOLD, 12));
        playerOverseasBadgeLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        playerBasePriceLabel = new JLabel("Base Price: -- Cr");
        playerBasePriceLabel.setFont(Main.uiFont(Font.BOLD, 12));
        playerBasePriceLabel.setForeground(new Color(251, 191, 36));

        badgeRow.add(playerRoleBadgeLabel);
        badgeRow.add(playerOverseasBadgeLabel);
        badgeRow.add(Box.createHorizontalStrut(8));
        badgeRow.add(playerBasePriceLabel);
        badgeRow.setAlignmentX(LEFT_ALIGNMENT);

        playerNameLabel = new JLabel("Waiting for Auctioneer to Start...", SwingConstants.LEFT);
        playerNameLabel.setFont(Main.displayFont(Font.BOLD, 28));
        playerNameLabel.setForeground(Color.WHITE);
        playerNameLabel.setAlignmentX(LEFT_ALIGNMENT);

        playerStatsArea = new JTextArea(2, 28);
        playerStatsArea.setEditable(false);
        playerStatsArea.setOpaque(false);
        playerStatsArea.setFont(Main.uiFont(Font.BOLD, 12));
        playerStatsArea.setForeground(new Color(203, 213, 225));
        playerStatsArea.setAlignmentX(LEFT_ALIGNMENT);

        statsBarPanel = new BroadcastStatsBarPanel();
        statsBarPanel.setAlignmentX(LEFT_ALIGNMENT);

        infoCol.add(badgeRow);
        infoCol.add(Box.createVerticalStrut(6));
        infoCol.add(playerNameLabel);
        infoCol.add(Box.createVerticalStrut(4));
        infoCol.add(playerStatsArea);
        infoCol.add(Box.createVerticalStrut(6));
        infoCol.add(statsBarPanel);

        panel.add(infoCol, BorderLayout.CENTER);

        // Right side: Glowing Circular Countdown Ring
        timerDial = new CircularTimerDial();
        panel.add(timerDial, BorderLayout.EAST);

        // Bottom linear progress bar synced with circular dial
        JPanel timerPanel = new JPanel(new BorderLayout(8, 2));
        timerPanel.setOpaque(false);
        JLabel timerTitle = new JLabel("Round Clock:");
        timerTitle.setFont(Main.uiFont(Font.BOLD, 12));
        timerTitle.setForeground(new Color(148, 163, 184));
        timerPanel.add(timerTitle, BorderLayout.WEST);

        countdownProgressBar = new JProgressBar(0, 15);
        countdownProgressBar.setValue(15);
        countdownProgressBar.setStringPainted(true);
        countdownProgressBar.setString("15s");
        countdownProgressBar.setForeground(new Color(34, 197, 94));
        timerPanel.add(countdownProgressBar, BorderLayout.CENTER);
        panel.add(timerPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createBiddingActionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(17, 28, 53));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85), 1),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        JLabel curBidTitle = new JLabel("CURRENT HIGHEST BID:");
        curBidTitle.setFont(Main.uiFont(Font.BOLD, 12));
        curBidTitle.setForeground(new Color(148, 163, 184));
        panel.add(curBidTitle, gbc);

        gbc.gridx = 1;
        currentPriceLabel = new JLabel("0.00 Cr");
        currentPriceLabel.setFont(Main.displayFont(Font.BOLD, 28));
        currentPriceLabel.setForeground(new Color(251, 191, 36));
        panel.add(currentPriceLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        JLabel leadTitle = new JLabel("LEADING FRANCHISE:");
        leadTitle.setFont(Main.uiFont(Font.BOLD, 12));
        leadTitle.setForeground(new Color(148, 163, 184));
        panel.add(leadTitle, gbc);

        gbc.gridx = 1;
        leadingTeamLabel = new JLabel("None (Awaiting Opening Bid)");
        leadingTeamLabel.setFont(Main.uiFont(Font.BOLD, 15));
        leadingTeamLabel.setForeground(new Color(56, 189, 248));
        panel.add(leadingTeamLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        userPurseTitleLabel = new JLabel("Your Remaining Purse:");
        userPurseTitleLabel.setFont(Main.uiFont(Font.BOLD, 12));
        userPurseTitleLabel.setForeground(new Color(226, 232, 240));
        panel.add(userPurseTitleLabel, gbc);

        gbc.gridx = 1;
        userPurseLabel = new JLabel("100.00 Cr");
        userPurseLabel.setFont(Main.uiFont(Font.BOLD, 14));
        userPurseLabel.setForeground(new Color(74, 222, 128));
        panel.add(userPurseLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        bidAmountTitleLabel = new JLabel("Your Bid Amount (Cr):");
        bidAmountTitleLabel.setFont(Main.uiFont(Font.BOLD, 12));
        bidAmountTitleLabel.setForeground(new Color(226, 232, 240));
        panel.add(bidAmountTitleLabel, gbc);

        gbc.gridx = 1;
        JPanel spinnerAndQuickPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        spinnerAndQuickPanel.setOpaque(false);

        bidSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.20, 100.0, 0.25));
        bidSpinner.setPreferredSize(new Dimension(115, 32));
        bidSpinner.setFont(Main.uiFont(Font.BOLD, 14));
        spinnerAndQuickPanel.add(bidSpinner);

        quickStepRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        quickStepRow.setOpaque(false);
        double[] increments = {0.25, 0.50, 1.00};
        for (double inc : increments) {
            JButton stepBtn = new JButton(String.format("+%.2f", inc));
            stepBtn.setFont(Main.uiFont(Font.BOLD, 12));
            stepBtn.setBackground(new Color(30, 41, 59));
            stepBtn.setForeground(new Color(56, 189, 248));
            stepBtn.setMargin(new Insets(3, 6, 3, 6));
            stepBtn.setFocusPainted(false);
            stepBtn.setToolTipText("Add +" + inc + " Cr to spinner");
            stepBtn.addActionListener(e -> {
                double cur = (Double) bidSpinner.getValue();
                bidSpinner.setValue(Math.min(100.0, Math.round((cur + inc) * 100.0) / 100.0));
            });
            quickStepRow.add(stepBtn);
        }
        spinnerAndQuickPanel.add(quickStepRow);
        panel.add(spinnerAndQuickPanel, gbc);

        // Team Owner bidding buttons row
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 2;
        bidderBtnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        bidderBtnRow.setOpaque(false);

        placeBidBtn = new JButton(">>  PLACE BID NOW");
        placeBidBtn.setFont(Main.displayFont(Font.BOLD, 17));
        placeBidBtn.setBackground(new Color(245, 158, 11));
        placeBidBtn.setForeground(new Color(15, 23, 42));
        placeBidBtn.setPreferredSize(new Dimension(195, 40));
        placeBidBtn.addActionListener(e -> handlePlaceHumanBid());
        bidderBtnRow.add(placeBidBtn);

        passBtn = new JButton(">>  Pass Round");
        passBtn.setFont(Main.uiFont(Font.BOLD, 13));
        passBtn.setBackground(new Color(51, 65, 85));
        passBtn.setForeground(Color.WHITE);
        passBtn.setPreferredSize(new Dimension(140, 40));
        passBtn.addActionListener(e -> handlePass());
        bidderBtnRow.add(passBtn);

        panel.add(bidderBtnRow, gbc);

        // Admin Auctioneer control row (shown only to ADMIN on Laptop 1)
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.gridwidth = 2;
        adminControlRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        adminControlRow.setOpaque(false);

        adminPauseResumeBtn = new JButton("||  Pause / Resume Round");
        adminPauseResumeBtn.setFont(Main.uiFont(Font.BOLD, 12));
        adminPauseResumeBtn.setBackground(new Color(30, 41, 59));
        adminPauseResumeBtn.setForeground(Color.WHITE);
        adminPauseResumeBtn.setPreferredSize(new Dimension(190, 38));
        adminPauseResumeBtn.addActionListener(e -> {
            if (auctionService.isPaused()) {
                auctionService.resumeAuction();
            } else {
                auctionService.pauseAuction();
            }
        });
        adminControlRow.add(adminPauseResumeBtn);

        adminSkipBtn = new JButton(">>  Skip / Next Player");
        adminSkipBtn.setFont(Main.uiFont(Font.BOLD, 12));
        adminSkipBtn.setBackground(new Color(37, 99, 235));
        adminSkipBtn.setForeground(Color.WHITE);
        adminSkipBtn.setPreferredSize(new Dimension(175, 38));
        adminSkipBtn.addActionListener(e -> auctionService.presentNextPlayer());
        adminControlRow.add(adminSkipBtn);
        adminControlRow.setVisible(false);

        panel.add(adminControlRow, gbc);

        return panel;
    }

    private void handlePlaceHumanBid() {
        if (humanTeam == null) {
            JOptionPane.showMessageDialog(this, "No human team associated with this account.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double bidAmount = ((Double) bidSpinner.getValue());
        if (auctionClient != null) {
            try {
                auctionClient.sendBid(humanTeam.getTeamId(), bidAmount);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Network Error", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        try {
            auctionService.placeHumanBid(humanTeam, bidAmount);
            updateUserPurseDisplay();
        } catch (CricBidException ex) {
            SoundEffectService.playUnsoldTone();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Bid Rejected", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database error: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handlePass() {
        if (auctionClient != null && humanTeam != null) {
            auctionClient.sendPass(humanTeam.getName());
        } else {
            logMessage("You passed on the current player.");
        }
    }

    private void handleAutoSuggest() {
        Player target = (activePlayer != null) ? activePlayer : auctionService.getCurrentPlayer();
        if (humanTeam == null || target == null) return;
        updateUserPurseDisplay();
        double suggestion = auctionService.suggestBidForPlayer(humanTeam, target);
        JOptionPane.showMessageDialog(this,
                String.format("AI Suggestion for %s:\nMax Recommended Bid: %.2f Cr\n(Derived from rating %d, squad needs, and min squad %d reserve rule)",
                        target.getName(), suggestion, target.getRating(), RuleValidator.getActiveMinSquadSize()),
                "Auto-Suggest Bid", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateUserPurseDisplay() {
        if (humanTeam != null) {
            try {
                teamService.loadSquadForTeam(humanTeam);
                Team t = teamService.getTeamById(humanTeam.getTeamId());
                if (t != null) {
                    humanTeam.setPurseRemaining(t.getPurseRemaining());
                }
            } catch (SQLException ignored) {}
            userPurseLabel.setText(String.format("%.2f Cr   (Squad: %d / 25)",
                    humanTeam.getPurseRemaining(), humanTeam.getSquadSize()));
        }
    }

    private String formatTeamBadge(Team team) {
        if (team == null) return "None";
        if (humanTeam != null && team.getTeamId() == humanTeam.getTeamId()) {
            return "* " + team.getName() + " [You]";
        }
        return "* " + team.getName() + (team.isHuman() ? " [Participant]" : "");
    }

    private String formatRoleIcon(PlayerRole role) {
        if (role == null) return "PLAYER";
        switch (role) {
            case BATSMAN:      return "BATSMAN";
            case BOWLER:       return "BOWLER";
            case ALLROUNDER:   return "ALL-ROUNDER";
            case WICKETKEEPER: return "WICKETKEEPER";
            default:           return role.getDisplayName().toUpperCase();
        }
    }

    private String formatPlayerBroadcastStats(Player p) {
        if (p == null) return "";
        double runRate = (p.getStrikeRate() * 6.0) / 100.0;
        switch (p.getRole()) {
            case BATSMAN:
                return String.format("Batting Stats   |   Strike Rate: %.2f   |   Run Rate: %.2f RPO   |   Bat Avg: %.2f",
                        p.getStrikeRate(), runRate, p.getBattingAvg());
            case BOWLER:
                return String.format("Bowling Stats   |   Economy: %.2f RPO   |   Career Wickets: %d   |   Rating: %d/100",
                        p.getEconomy(), p.getWickets(), p.getRating());
            case ALLROUNDER:
                return String.format("All-Round Stats   |   SR: %.1f (%.2f RPO)   |   Economy: %.2f RPO (%d Wkts)   |   Avg: %.1f",
                        p.getStrikeRate(), runRate, p.getEconomy(), p.getWickets(), p.getBattingAvg());
            case WICKETKEEPER:
                return String.format("Keeper-Bat Stats   |   Strike Rate: %.1f   |   Run Rate: %.2f RPO   |   Avg: %.1f   |   Dismissals: %d",
                        p.getStrikeRate(), runRate, p.getBattingAvg(), p.getDismissals());
            default:
                return p.describe();
        }
    }

    private String formatShortTableStat(Player p) {
        if (p == null) return "--";
        double rpo = (p.getStrikeRate() * 6.0) / 100.0;
        switch (p.getRole()) {
            case BATSMAN:
                return String.format("SR %.1f | %.2f RPO | Avg %.1f", p.getStrikeRate(), rpo, p.getBattingAvg());
            case BOWLER:
                return String.format("Econ %.2f RPO | %d Wkts", p.getEconomy(), p.getWickets());
            case ALLROUNDER:
                return String.format("SR %.1f (%.1f RPO) | Econ %.2f", p.getStrikeRate(), rpo, p.getEconomy());
            case WICKETKEEPER:
                return String.format("SR %.1f | %.2f RPO | %d Dism", p.getStrikeRate(), rpo, p.getDismissals());
            default:
                return p.describe();
        }
    }

    private void populatePlayerCard(Player player, double currentPrice, int roundDurationSec) {
        this.activeLeadingTeamId = null;
        this.activeSecondsRemaining = roundDurationSec;

        playerNameLabel.setText(player.getName());
        playerRoleBadgeLabel.setText(formatRoleIcon(player.getRole()));
        if (player.isOverseas()) {
            playerOverseasBadgeLabel.setText("OVERSEAS");
            playerOverseasBadgeLabel.setForeground(new Color(251, 191, 36));
        } else {
            playerOverseasBadgeLabel.setText("DOMESTIC");
            playerOverseasBadgeLabel.setForeground(new Color(56, 189, 248));
        }
        playerBasePriceLabel.setText(String.format("Base Price: %.2f Cr", player.getBasePrice()));
        playerStatsArea.setText(formatPlayerBroadcastStats(player));

        statsBarPanel.setPlayer(player);
        timerDial.setTime(roundDurationSec, roundDurationSec);

        currentPriceLabel.setText(String.format("%.2f Cr", currentPrice));
        leadingTeamLabel.setText("None (Awaiting Opening Bid)");

        countdownProgressBar.setMaximum(roundDurationSec);
        countdownProgressBar.setValue(roundDurationSec);
        countdownProgressBar.setString(roundDurationSec + "s");
        countdownProgressBar.setForeground(new Color(34, 197, 94));

        double nextBid = RuleValidator.getNextValidBidAmount(currentPrice, false);
        bidSpinner.setValue(nextBid);
        refreshScoreboardStrip();
    }

    private void logMessage(String message) {
        bidLogTextArea.append(message + "\n");
        bidLogTextArea.setCaretPosition(bidLogTextArea.getDocument().getLength());
    }

    public void refreshUpcomingPoolTable() {
        upcomingPoolModel.setRowCount(0);
        List<Player> pool;
        int startIndex = 0;

        if (auctionClient != null && syncedUpcomingPool != null) {
            pool = syncedUpcomingPool;
        } else {
            pool = auctionService.getAuctionPool();
            startIndex = auctionService.getCurrentPoolIndex();
        }

        for (int i = startIndex; i < pool.size(); i++) {
            Player p = pool.get(i);
            upcomingPoolModel.addRow(new Object[]{
                    p.getPlayerId(),
                    p.getName() + (p.isOverseas() ? " (OS)" : ""),
                    p.getRole().getDisplayName(),
                    formatShortTableStat(p),
                    p.getRating(),
                    String.format("%.2f", p.getBasePrice()),
                    p.getStatus().name()
            });
        }
    }

    @Override
    public void onPlayerUpForAuction(Player player, double currentPrice, int roundDurationSec) {
        this.activePlayer = player;
        this.activeRoundDurationSec = roundDurationSec;
        populatePlayerCard(player, currentPrice, roundDurationSec);

        logMessage(String.format(">>> * ON THE BLOCK: %s (%s) | Base: %.2f Cr",
                player.getName(), player.getRole().getDisplayName(), player.getBasePrice()));

        refreshUpcomingPoolTable();
    }

    @Override
    public void onBidPlaced(Bid bid, Team team, Player player) {
        SoundEffectService.playBidChime();
        this.activeLeadingTeamId = (team != null) ? team.getTeamId() : null;

        currentPriceLabel.setText(String.format("%.2f Cr", bid.getAmount()));
        leadingTeamLabel.setText(formatTeamBadge(team));

        // Reset both circular timer dial & progress bar immediately when a bid resets countdown
        this.activeSecondsRemaining = activeRoundDurationSec;
        timerDial.setTime(activeRoundDurationSec, activeRoundDurationSec);
        countdownProgressBar.setValue(activeRoundDurationSec);
        countdownProgressBar.setString(activeRoundDurationSec + "s");
        countdownProgressBar.setForeground(new Color(34, 197, 94));

        boolean userIsTyping = bidSpinner.hasFocus() ||
                (bidSpinner.getEditor() != null && bidSpinner.getEditor().hasFocus());
        if (!userIsTyping) {
            double nextValid = RuleValidator.getNextValidBidAmount(bid.getAmount(), true);
            bidSpinner.setValue(nextValid);
        }

        logMessage(String.format("  >> [BID] %s bid %.2f Cr for %s", team.getName(), bid.getAmount(), player.getName()));
        updateUserPurseDisplay();
        refreshScoreboardStrip();
    }

    @Override
    public void onPlayerSold(Player player, Team team, double finalPrice) {
        logMessage(String.format(">>> * SOLD: %s acquired by %s for %.2f Cr!\n", player.getName(), team.getName(), finalPrice));
        if (humanTeam != null && team != null && humanTeam.getTeamId() == team.getTeamId()) {
            humanTeam.setPurseRemaining(team.getPurseRemaining());
        }
        updateUserPurseDisplay();
        refreshScoreboardStrip();

        SoldPopupDialog dialog = new SoldPopupDialog(parentFrame, player, team, finalPrice, true);
        dialog.setVisible(true);

        if (auctionClient == null) {
            auctionService.presentNextPlayer();
        }
    }

    @Override
    public void onPlayerUnsold(Player player) {
        logMessage(String.format(">>> [!] UNSOLD: %s received no bids.\n", player.getName()));
        refreshScoreboardStrip();

        SoldPopupDialog dialog = new SoldPopupDialog(parentFrame, player, null, 0.0, false);
        dialog.setVisible(true);

        if (auctionClient == null) {
            auctionService.presentNextPlayer();
        }
    }

    @Override
    public void onTimerTick(int secondsRemaining) {
        this.activeSecondsRemaining = secondsRemaining;
        timerDial.setTime(secondsRemaining, activeRoundDurationSec);
        countdownProgressBar.setValue(secondsRemaining);
        countdownProgressBar.setString(secondsRemaining + "s");

        if (secondsRemaining <= 5) {
            countdownProgressBar.setForeground(new Color(239, 68, 68));
            if (secondsRemaining <= 3 && secondsRemaining > 0) {
                SoundEffectService.playTimerWarningBeep();
            }
        } else if (secondsRemaining <= 10) {
            countdownProgressBar.setForeground(new Color(245, 158, 11));
        } else {
            countdownProgressBar.setForeground(new Color(34, 197, 94));
        }
    }

    @Override
    public void onAuctionPaused(boolean isPaused) {
        logMessage(isPaused ? "--- || AUCTION PAUSED ---" : "--- >> AUCTION RESUMED ---");
        countdownProgressBar.setString(isPaused ? "PAUSED" : countdownProgressBar.getValue() + "s");
    }

    @Override
    public void onAuctionCompleted() {
        logMessage("=== AUCTION COMPLETED! Check Analytics & Final Summary ===");
        playerNameLabel.setText("Auction Completed!");
        leadingTeamLabel.setText("All Players Processed");
        countdownProgressBar.setValue(0);
        countdownProgressBar.setString("DONE");
        timerDial.setTime(0, activeRoundDurationSec);
        refreshUpcomingPoolTable();
        refreshScoreboardStrip();
    }

    @Override
    public void onRuleViolation(String message) {
        logMessage("  [!] RULE NOTICE: " + message);
    }

    /**
     * Custom Graphics2D Broadcast Cricket Stat Progress Bars (Strike Rate / Run Rate / Economy / Rating).
     */
    private static class BroadcastStatsBarPanel extends JPanel {
        private String row1Label = "STRIKE RATE:";
        private double row1Ratio = 0.0;
        private String row1ValueText = "--";

        private String row2Label = "RUN RATE / AVG:";
        private double row2Ratio = 0.0;
        private String row2ValueText = "--";

        private int rating = 0;

        BroadcastStatsBarPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(430, 62));
        }

        void setPlayer(Player p) {
            if (p == null) {
                this.rating = 0;
                this.row1Ratio = 0;
                this.row2Ratio = 0;
                repaint();
                return;
            }
            this.rating = Math.max(0, Math.min(100, p.getRating()));
            double rpo = (p.getStrikeRate() * 6.0) / 100.0;

            switch (p.getRole()) {
                case BOWLER:
                    this.row1Label = "BOWL ECONOMY:";
                    // Lower economy (e.g. 6.5 RPO) = stronger bar (scaled from 11.5 down to 5.5)
                    this.row1Ratio = Math.max(0.15, Math.min(1.0, (11.5 - p.getEconomy()) / 6.0));
                    this.row1ValueText = String.format("%.2f RPO", p.getEconomy());

                    this.row2Label = "CAREER WICKETS:";
                    this.row2Ratio = Math.max(0.15, Math.min(1.0, p.getWickets() / 160.0));
                    this.row2ValueText = String.format("%d Wkts", p.getWickets());
                    break;

                case ALLROUNDER:
                    this.row1Label = "STRIKE RATE:";
                    this.row1Ratio = Math.max(0.15, Math.min(1.0, p.getStrikeRate() / 185.0));
                    this.row1ValueText = String.format("%.1f (%.2f RPO)", p.getStrikeRate(), rpo);

                    this.row2Label = "BOWL ECONOMY:";
                    this.row2Ratio = Math.max(0.15, Math.min(1.0, (11.5 - p.getEconomy()) / 6.0));
                    this.row2ValueText = String.format("%.2f RPO (%dW)", p.getEconomy(), p.getWickets());
                    break;

                case WICKETKEEPER:
                    this.row1Label = "STRIKE RATE:";
                    this.row1Ratio = Math.max(0.15, Math.min(1.0, p.getStrikeRate() / 185.0));
                    this.row1ValueText = String.format("%.1f SR", p.getStrikeRate());

                    this.row2Label = "RUN RATE / AVG:";
                    this.row2Ratio = Math.max(0.15, Math.min(1.0, p.getBattingAvg() / 55.0));
                    this.row2ValueText = String.format("%.2f RPO (Avg %.1f)", rpo, p.getBattingAvg());
                    break;

                case BATSMAN:
                default:
                    this.row1Label = "STRIKE RATE:";
                    this.row1Ratio = Math.max(0.15, Math.min(1.0, p.getStrikeRate() / 185.0));
                    this.row1ValueText = String.format("%.2f SR", p.getStrikeRate());

                    this.row2Label = "RUN RATE / AVG:";
                    this.row2Ratio = Math.max(0.15, Math.min(1.0, p.getBattingAvg() / 58.0));
                    this.row2ValueText = String.format("%.2f RPO (Avg %.1f)", rpo, p.getBattingAvg());
                    break;
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int barX = 118;
            int barW = Math.max(80, w - barX - 135);

            g2.setFont(Main.uiFont(Font.BOLD, 11));

            // Row 1: Strike Rate or Bowl Economy
            g2.setColor(new Color(148, 163, 184));
            g2.drawString(row1Label, 0, 13);
            g2.setColor(new Color(30, 41, 59));
            g2.fillRoundRect(barX, 3, barW, 10, 8, 8);
            int fill1 = (int) (barW * row1Ratio);
            g2.setPaint(new GradientPaint(barX, 0, new Color(56, 189, 248), barX + barW, 0, new Color(34, 197, 94)));
            g2.fillRoundRect(barX, 3, fill1, 10, 8, 8);
            g2.setColor(new Color(56, 189, 248));
            g2.drawString(row1ValueText, barX + barW + 8, 13);

            // Row 2: Run Rate / Avg or Wickets / Economy
            g2.setColor(new Color(148, 163, 184));
            g2.drawString(row2Label, 0, 32);
            g2.setColor(new Color(30, 41, 59));
            g2.fillRoundRect(barX, 22, barW, 10, 8, 8);
            int fill2 = (int) (barW * row2Ratio);
            g2.setPaint(new GradientPaint(barX, 0, new Color(245, 158, 11), barX + barW, 0, new Color(251, 191, 36)));
            g2.fillRoundRect(barX, 22, fill2, 10, 8, 8);
            g2.setColor(new Color(251, 191, 36));
            g2.drawString(row2ValueText, barX + barW + 8, 32);

            // Row 3: Overall Rating Bar (0-100)
            g2.setColor(new Color(148, 163, 184));
            g2.drawString("OVR RATING:", 0, 51);
            g2.setColor(new Color(30, 41, 59));
            g2.fillRoundRect(barX, 41, barW, 10, 8, 8);
            int fill3 = (int) (barW * (rating / 100.0));
            g2.setPaint(new GradientPaint(barX, 0, new Color(34, 197, 94), barX + barW, 0, new Color(74, 222, 128)));
            g2.fillRoundRect(barX, 41, fill3, 10, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawString(rating + " / 100", barX + barW + 8, 51);

            g2.dispose();
        }
    }

    /**
     * Custom Graphics2D Circular Broadcast Countdown Ring.
     */
    private static class CircularTimerDial extends JPanel {
        private int seconds = 15;
        private int maxSeconds = 15;

        CircularTimerDial() {
            setOpaque(false);
            setPreferredSize(new Dimension(112, 112));
        }

        void setTime(int seconds, int maxSeconds) {
            this.seconds = Math.max(0, seconds);
            this.maxSeconds = Math.max(1, maxSeconds);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = Math.min(getWidth(), getHeight()) - 14;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            // Background ring
            g2.setColor(new Color(30, 41, 59));
            g2.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawOval(x, y, size, size);

            // Foreground arc
            Color ringColor;
            if (seconds <= 5) {
                ringColor = new Color(239, 68, 68);
            } else if (seconds <= 10) {
                ringColor = new Color(245, 158, 11);
            } else {
                ringColor = new Color(34, 197, 94);
            }
            g2.setColor(ringColor);
            int angle = (int) Math.round(360.0 * seconds / maxSeconds);
            g2.drawArc(x, y, size, size, 90, -angle);

            // Seconds text in center
            String secText = seconds + "s";
            g2.setFont(Main.displayFont(Font.BOLD, 24));
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(secText)) / 2;
            int ty = (getHeight() / 2) + 4;
            g2.drawString(secText, tx, ty);

            g2.setFont(Main.uiFont(Font.BOLD, 10));
            g2.setColor(new Color(148, 163, 184));
            String sub = "TIMER";
            int sx = (getWidth() - g2.getFontMetrics().stringWidth(sub)) / 2;
            g2.drawString(sub, sx, ty + 15);

            g2.dispose();
        }
    }
}

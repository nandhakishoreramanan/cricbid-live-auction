package com.cricbid.ui;

import com.cricbid.db.DBConnection;
import com.cricbid.exception.CricBidException;
import com.cricbid.model.Bid;
import com.cricbid.model.Player;
import com.cricbid.model.Team;
import com.cricbid.model.User;
import com.cricbid.network.AuctionClient;
import com.cricbid.network.NetworkMessage;
import com.cricbid.network.NetworkUtils;
import com.cricbid.observer.AuctionObserver;
import com.cricbid.service.AuctionService;
import com.cricbid.service.RuleValidator;
import com.cricbid.service.TeamService;
import com.cricbid.ui.dialog.SoldPopupDialog;
import com.cricbid.ui.renderer.PlayerTableRenderer;

import javax.swing.BorderFactory;
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
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
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

    private JLabel playerNameLabel;
    private JLabel playerRoleLabel;
    private JLabel playerRatingLabel;
    private JLabel playerBasePriceLabel;
    private JTextArea playerStatsArea;

    private JProgressBar countdownProgressBar;
    private JLabel currentPriceLabel;
    private JLabel leadingTeamLabel;
    private JLabel userPurseTitleLabel;
    private JLabel userPurseLabel;
    private JLabel bidAmountTitleLabel;

    private JSpinner bidSpinner;
    private JPanel bidderBtnRow;
    private JButton placeBidBtn;
    private JButton passBtn;
    private JButton autoSuggestBtn;

    private JPanel adminControlRow;
    private JButton adminPauseResumeBtn;
    private JButton adminSkipBtn;
    private JToggleButton botToggle;

    private JTextArea bidLogTextArea;
    private JTable upcomingPoolTable;
    private DefaultTableModel upcomingPoolModel;

    private Team humanTeam;
    private User currentUser;
    private AuctionClient auctionClient;
    private Player activePlayer;
    private int activeRoundDurationSec = 15;
    private List<Player> syncedUpcomingPool = null;

    public LiveAuctionPanel(JFrame parentFrame, AuctionService auctionService, TeamService teamService) {
        this.parentFrame = parentFrame;
        this.auctionService = auctionService;
        this.teamService = teamService;

        setLayout(new BorderLayout());
        initComponents();

        this.auctionService.getAuctionSubject().addObserver(this);
    }

    public void setSession(User user, Team team, AuctionClient client) {
        this.currentUser = user;
        this.humanTeam = team;
        this.auctionClient = client;

        boolean isAdmin = user != null && user.isAdmin();

        // Toggle Bidder controls vs Auctioneer controls
        bidAmountTitleLabel.setVisible(!isAdmin);
        bidSpinner.setVisible(!isAdmin);
        bidderBtnRow.setVisible(!isAdmin);
        adminControlRow.setVisible(isAdmin);

        if (isAdmin) {
            userPurseTitleLabel.setText("Session Role:");
            userPurseLabel.setText("Auctioneer Host (Controls Start / Pause / Next)");
            userPurseLabel.setForeground(new Color(20, 65, 140));
            botToggle.setSelected(auctionService.isBotsEnabled());
            updateBotToggleStyle(auctionService.isBotsEnabled());
            updateHostBanner(null);
        } else {
            String teamTitle = (humanTeam != null) ? humanTeam.getName() : "Unassigned";
            userPurseTitleLabel.setText("Your Purse (" + teamTitle + "):");
            userPurseLabel.setForeground(new Color(25, 120, 40));
            updateUserPurseDisplay();
            if (client != null) {
                updateClientBanner(client.isConnected(),
                        "Connecting to Host " + client.getHost() + ":" + client.getPort() + "...");
            }
        }
    }

    public void updateHostBanner(List<String> connectedClients) {
        if (currentUser == null || !currentUser.isAdmin()) return;
        String lanIp = NetworkUtils.getLocalLanIp();
        int port = DBConnection.getInstance().getServerPort();
        String clientsInfo = (connectedClients == null || connectedClients.isEmpty())
                ? "0 Laptops Connected (Waiting for owner1 / owner2)"
                : connectedClients.size() + " Connected: " + String.join(", ", connectedClients);
        statusBannerPanel.setBackground(new Color(228, 240, 255));
        statusBannerLabel.setForeground(new Color(15, 55, 115));
        statusBannerLabel.setText(String.format("🎙 AUCTIONEER HOST  |  Host IP: %s:%d  |  %s", lanIp, port, clientsInfo));
    }

    public void updateClientBanner(boolean connected, String statusMessage) {
        if (currentUser != null && currentUser.isAdmin()) return;
        String teamTitle = (humanTeam != null) ? humanTeam.getName() : "Spectator";
        if (connected) {
            statusBannerPanel.setBackground(new Color(225, 248, 230));
            statusBannerLabel.setForeground(new Color(15, 105, 35));
            statusBannerLabel.setText(String.format("🟢 %s  |  Bidding as Franchise: %s", statusMessage, teamTitle));
        } else {
            statusBannerPanel.setBackground(new Color(255, 244, 220));
            statusBannerLabel.setForeground(new Color(165, 90, 10));
            statusBannerLabel.setText(String.format("🟡 %s  |  Franchise: %s", statusMessage, teamTitle));
        }
    }

    public void setSyncedUpcomingPool(List<Player> upcoming) {
        this.syncedUpcomingPool = (upcoming != null) ? new ArrayList<>(upcoming) : new ArrayList<>();
        refreshUpcomingPoolTable();
    }

    public void handleAuctionReset(boolean quickMode) {
        RuleValidator.setQuickModeActive(quickMode);
        bidLogTextArea.setText("");
        logMessage(String.format("=== NEW AUCTION STARTED (%s Mode | Min Squad: %d) ===",
                quickMode ? "Quick" : "Full", RuleValidator.getActiveMinSquadSize()));
        updateUserPurseDisplay();
    }

    public void applySyncState(NetworkMessage sync) {
        if (sync == null) return;
        RuleValidator.setQuickModeActive(sync.isQuickMode());
        if (sync.getUpcomingPool() != null) {
            this.syncedUpcomingPool = new ArrayList<>(sync.getUpcomingPool());
            refreshUpcomingPoolTable();
        }
        updateUserPurseDisplay();

        Player p = sync.getPlayer();
        if (!sync.isRunning() || p == null) {
            playerNameLabel.setText("Waiting for Admin to start auction...");
            leadingTeamLabel.setText("Connected — Standby for Round 1");
            return;
        }

        this.activePlayer = p;
        this.activeRoundDurationSec = Math.max(5, sync.getRoundDurationSec());
        populatePlayerCard(p, sync.getAmount(), activeRoundDurationSec);
        onTimerTick(sync.getSecondsRemaining());

        if (sync.isRoundHasBids() && sync.getTeam() != null) {
            Team lead = sync.getTeam();
            leadingTeamLabel.setText(formatTeamBadge(lead));
            double nextValid = RuleValidator.getNextValidBidAmount(sync.getAmount(), true);
            bidSpinner.setValue(nextValid);
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
        JOptionPane.showMessageDialog(this, reason, "Bid Rejected", JOptionPane.WARNING_MESSAGE);
    }

    private void initComponents() {
        // Top status strip
        statusBannerPanel = new JPanel(new BorderLayout());
        statusBannerPanel.setBackground(new Color(235, 242, 250));
        statusBannerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 212, 228)),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
        statusBannerLabel = new JLabel("Live Auction Room");
        statusBannerLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        statusBannerPanel.add(statusBannerLabel, BorderLayout.CENTER);
        add(statusBannerPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.55);
        splitPane.setDividerLocation(520);

        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel cardPanel = createPlayerCardPanel();
        leftPanel.add(cardPanel, BorderLayout.NORTH);

        JPanel actionPanel = createBiddingActionPanel();
        leftPanel.add(actionPanel, BorderLayout.CENTER);

        splitPane.setLeftComponent(leftPanel);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        rightSplit.setResizeWeight(0.45);
        rightSplit.setDividerLocation(260);

        JPanel bidLogPanel = new JPanel(new BorderLayout());
        bidLogPanel.setBorder(BorderFactory.createTitledBorder("Live Bid Activity Log"));
        bidLogTextArea = new JTextArea();
        bidLogTextArea.setEditable(false);
        bidLogTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        bidLogTextArea.setBackground(new Color(250, 252, 255));
        bidLogPanel.add(new JScrollPane(bidLogTextArea), BorderLayout.CENTER);
        rightSplit.setTopComponent(bidLogPanel);

        JPanel upcomingPanel = new JPanel(new BorderLayout());
        upcomingPanel.setBorder(BorderFactory.createTitledBorder("Upcoming Player Pool"));

        String[] cols = {"ID", "Name", "Role", "Rating", "Base (Cr)", "Status"};
        upcomingPoolModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        upcomingPoolTable = new JTable(upcomingPoolModel);
        upcomingPoolTable.setDefaultRenderer(Object.class, new PlayerTableRenderer());
        upcomingPanel.add(new JScrollPane(upcomingPoolTable), BorderLayout.CENTER);
        rightSplit.setBottomComponent(upcomingPanel);

        splitPane.setRightComponent(rightSplit);

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createPlayerCardPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Currently on the Auction Block"));
        panel.setBackground(Color.WHITE);

        JPanel topInfo = new JPanel(new GridLayout(2, 2, 8, 4));
        topInfo.setOpaque(false);

        playerNameLabel = new JLabel("Waiting for auction...", SwingConstants.LEFT);
        playerNameLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        playerNameLabel.setForeground(new Color(25, 45, 85));

        playerRatingLabel = new JLabel("Rating: --", SwingConstants.RIGHT);
        playerRatingLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        playerRoleLabel = new JLabel("Role: --", SwingConstants.LEFT);
        playerRoleLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        playerBasePriceLabel = new JLabel("Base: -- Cr", SwingConstants.RIGHT);
        playerBasePriceLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        topInfo.add(playerNameLabel);
        topInfo.add(playerRatingLabel);
        topInfo.add(playerRoleLabel);
        topInfo.add(playerBasePriceLabel);
        panel.add(topInfo, BorderLayout.NORTH);

        playerStatsArea = new JTextArea(4, 30);
        playerStatsArea.setEditable(false);
        playerStatsArea.setOpaque(false);
        playerStatsArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        playerStatsArea.setForeground(Color.DARK_GRAY);
        panel.add(playerStatsArea, BorderLayout.CENTER);

        JPanel timerPanel = new JPanel(new BorderLayout(6, 4));
        timerPanel.setOpaque(false);
        JLabel timerTitle = new JLabel("Bidding Timer:");
        timerTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        timerPanel.add(timerTitle, BorderLayout.WEST);

        countdownProgressBar = new JProgressBar(0, 15);
        countdownProgressBar.setValue(15);
        countdownProgressBar.setStringPainted(true);
        countdownProgressBar.setString("15s");
        countdownProgressBar.setForeground(new Color(40, 160, 60));
        timerPanel.add(countdownProgressBar, BorderLayout.CENTER);
        panel.add(timerPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createBiddingActionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Live Bidding Controls"));
        panel.setBackground(Color.WHITE);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Current Highest Price:"), gbc);

        gbc.gridx = 1;
        currentPriceLabel = new JLabel("0.00 Cr");
        currentPriceLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        currentPriceLabel.setForeground(new Color(180, 30, 30));
        panel.add(currentPriceLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Leading Bidder:"), gbc);

        gbc.gridx = 1;
        leadingTeamLabel = new JLabel("None (Awaiting Bids)");
        leadingTeamLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        leadingTeamLabel.setForeground(new Color(20, 60, 130));
        panel.add(leadingTeamLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        userPurseTitleLabel = new JLabel("Your Remaining Purse:");
        panel.add(userPurseTitleLabel, gbc);

        gbc.gridx = 1;
        userPurseLabel = new JLabel("100.00 Cr");
        userPurseLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        userPurseLabel.setForeground(new Color(25, 120, 40));
        panel.add(userPurseLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        bidAmountTitleLabel = new JLabel("Your Bid Amount (Cr):");
        panel.add(bidAmountTitleLabel, gbc);

        gbc.gridx = 1;
        bidSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.20, 100.0, 0.25));
        bidSpinner.setPreferredSize(new Dimension(140, 30));
        panel.add(bidSpinner, gbc);

        // Team Owner bidding buttons row
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 2;
        bidderBtnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        bidderBtnRow.setOpaque(false);

        placeBidBtn = new JButton("Place Bid");
        placeBidBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        placeBidBtn.setBackground(new Color(30, 90, 180));
        placeBidBtn.setForeground(Color.BLACK);
        placeBidBtn.addActionListener(e -> handlePlaceHumanBid());
        bidderBtnRow.add(placeBidBtn);

        passBtn = new JButton("Pass");
        passBtn.addActionListener(e -> handlePass());
        bidderBtnRow.add(passBtn);

        autoSuggestBtn = new JButton("Auto-Suggest Bid");
        autoSuggestBtn.setToolTipText("Calculates an optimal max valuation for this player based on rating and purse");
        autoSuggestBtn.addActionListener(e -> handleAutoSuggest());
        bidderBtnRow.add(autoSuggestBtn);

        panel.add(bidderBtnRow, gbc);

        // Admin Auctioneer control row (shown only to ADMIN on Laptop 1)
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.gridwidth = 2;
        adminControlRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        adminControlRow.setOpaque(false);

        adminPauseResumeBtn = new JButton("Pause / Resume Round");
        adminPauseResumeBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        adminPauseResumeBtn.addActionListener(e -> {
            if (auctionService.isPaused()) {
                auctionService.resumeAuction();
            } else {
                auctionService.pauseAuction();
            }
        });
        adminControlRow.add(adminPauseResumeBtn);

        adminSkipBtn = new JButton("Skip / Next Player");
        adminSkipBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        adminSkipBtn.addActionListener(e -> auctionService.presentNextPlayer());
        adminControlRow.add(adminSkipBtn);

        botToggle = new JToggleButton("🤖 AI Bots: OFF (Click to Enable)", false);
        botToggle.setFont(new Font("SansSerif", Font.BOLD, 12));
        botToggle.setPreferredSize(new Dimension(240, 32));
        updateBotToggleStyle(false);
        botToggle.addActionListener(e -> {
            boolean on = botToggle.isSelected();
            auctionService.setBotsEnabled(on);
            updateBotToggleStyle(on);
        });
        adminControlRow.add(botToggle);
        adminControlRow.setVisible(false);

        panel.add(adminControlRow, gbc);

        return panel;
    }

    private void updateBotToggleStyle(boolean on) {
        botToggle.setText(on ? "🤖 AI Bots: ON (Click to Disable)" : "🤖 AI Bots: OFF (Human-Only)");
        botToggle.setBackground(on ? new Color(220, 255, 220) : new Color(242, 244, 248));
        botToggle.setForeground(on ? new Color(20, 120, 20) : new Color(90, 95, 105));
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
                userPurseLabel.setText(String.format("%.2f Cr  (Squad: %d/25)",
                        humanTeam.getPurseRemaining(), humanTeam.getSquadSize()));
            } catch (SQLException ignored) {}
        }
    }

    private String formatTeamBadge(Team team) {
        if (team == null) return "None";
        if (humanTeam != null && team.getTeamId() == humanTeam.getTeamId()) {
            return team.getName() + " [You]";
        }
        return team.getName() + (team.isHuman() ? " [Participant]" : " [Bot]");
    }

    private void populatePlayerCard(Player player, double currentPrice, int roundDurationSec) {
        playerNameLabel.setText(player.getName() + (player.isOverseas() ? " (Overseas)" : ""));
        playerRoleLabel.setText("Role: " + player.getRole().getDisplayName());
        playerRatingLabel.setText("Rating: " + player.getRating() + "/100");
        playerBasePriceLabel.setText(String.format("Base: %.2f Cr", player.getBasePrice()));
        playerStatsArea.setText(player.describe() + "\nValuation: ~" + String.format("%.2f", player.calculateValue()) + " Cr");

        currentPriceLabel.setText(String.format("%.2f Cr", currentPrice));
        leadingTeamLabel.setText("None (Awaiting Bids)");

        countdownProgressBar.setMaximum(roundDurationSec);
        countdownProgressBar.setValue(roundDurationSec);
        countdownProgressBar.setString(roundDurationSec + "s");
        countdownProgressBar.setForeground(new Color(40, 160, 60));

        double nextBid = RuleValidator.getNextValidBidAmount(currentPrice, false);
        bidSpinner.setValue(nextBid);
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

        logMessage(String.format(">>> UP FOR AUCTION: %s (%s) | Base: %.2f Cr",
                player.getName(), player.getRole().getDisplayName(), player.getBasePrice()));

        refreshUpcomingPoolTable();
    }

    @Override
    public void onBidPlaced(Bid bid, Team team, Player player) {
        currentPriceLabel.setText(String.format("%.2f Cr", bid.getAmount()));
        leadingTeamLabel.setText(formatTeamBadge(team));

        // Reset visual timer bar immediately when a bid resets the countdown
        countdownProgressBar.setValue(activeRoundDurationSec);
        countdownProgressBar.setString(activeRoundDurationSec + "s");
        countdownProgressBar.setForeground(new Color(40, 160, 60));

        boolean userIsTyping = bidSpinner.hasFocus() ||
                (bidSpinner.getEditor() != null && bidSpinner.getEditor().hasFocus());
        if (!userIsTyping) {
            double nextValid = RuleValidator.getNextValidBidAmount(bid.getAmount(), true);
            bidSpinner.setValue(nextValid);
        }

        logMessage(String.format("  [BID] %s bid %.2f Cr for %s", team.getName(), bid.getAmount(), player.getName()));
        updateUserPurseDisplay();
    }

    @Override
    public void onPlayerSold(Player player, Team team, double finalPrice) {
        logMessage(String.format(">>> SOLD: %s sold to %s for %.2f Cr\n", player.getName(), team.getName(), finalPrice));
        updateUserPurseDisplay();

        SoldPopupDialog dialog = new SoldPopupDialog(parentFrame, player, team, finalPrice, true);
        dialog.setVisible(true);

        // Only the Host (non-client) advances the auction engine to the next player
        if (auctionClient == null) {
            auctionService.presentNextPlayer();
        }
    }

    @Override
    public void onPlayerUnsold(Player player) {
        logMessage(String.format(">>> UNSOLD: %s received no bids.\n", player.getName()));

        SoldPopupDialog dialog = new SoldPopupDialog(parentFrame, player, null, 0.0, false);
        dialog.setVisible(true);

        if (auctionClient == null) {
            auctionService.presentNextPlayer();
        }
    }

    @Override
    public void onTimerTick(int secondsRemaining) {
        countdownProgressBar.setValue(secondsRemaining);
        countdownProgressBar.setString(secondsRemaining + "s");

        if (secondsRemaining <= 3) {
            countdownProgressBar.setForeground(Color.RED);
        } else if (secondsRemaining <= 6) {
            countdownProgressBar.setForeground(new Color(220, 140, 20));
        } else {
            countdownProgressBar.setForeground(new Color(40, 160, 60));
        }
    }

    @Override
    public void onAuctionPaused(boolean isPaused) {
        logMessage(isPaused ? "[AUCTION PAUSED BY HOST]" : "[AUCTION RESUMED BY HOST]");
    }

    @Override
    public void onAuctionCompleted() {
        this.activePlayer = null;
        logMessage("\n======================================");
        logMessage("AUCTION TOURNAMENT COMPLETED!");
        logMessage("======================================\n");
        JOptionPane.showMessageDialog(this,
                "All players in the auction pool have been processed!\nNavigate to the Reports or Summary tab to inspect results.",
                "Auction Completed", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void onRuleViolation(String message) {
        logMessage("[RULE WARNING] " + message);
    }
}

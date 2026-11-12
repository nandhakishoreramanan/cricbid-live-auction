package com.cricbid.ui;

import com.cricbid.Main;
import com.cricbid.db.DBConnection;
import com.cricbid.model.Team;
import com.cricbid.model.User;
import com.cricbid.network.AuctionClient;
import com.cricbid.network.AuctionServer;
import com.cricbid.network.NetworkUtils;
import com.cricbid.network.WebAuctionPortalServer;
import com.cricbid.observer.AuctionSubject;
import com.cricbid.service.AuctionService;
import com.cricbid.service.AuthService;
import com.cricbid.service.ReportService;
import com.cricbid.service.SoundEffectService;
import com.cricbid.service.TeamService;
import com.cricbid.service.TradeService;
import com.cricbid.ui.dialog.AboutDialog;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.sql.SQLException;


public class MainFrame extends JFrame {

    public static final String CARD_LOGIN = "LOGIN";
    public static final String CARD_SETUP = "SETUP";
    public static final String CARD_LIVE = "LIVE_AUCTION";
    public static final String CARD_POOL = "PLAYER_POOL";
    public static final String CARD_TEAMS = "TEAMS";
    public static final String CARD_BOTS = "BOT_CONTROL";
    public static final String CARD_TRADE = "TRADE_WINDOW";
    public static final String CARD_REPORTS = "REPORTS";
    public static final String CARD_SUMMARY = "SUMMARY";

    private final AuthService authService;
    private final TeamService teamService;
    private final TradeService tradeService;
    private final ReportService reportService;
    private final AuctionSubject auctionSubject;
    private final AuctionService auctionService;

    private final CardLayout cardLayout;
    private final JPanel cardsPanel;
    private JToolBar mainToolBar;

    private JMenuItem newAuctionItem;
    private JMenu auctionMenu;
    private JCheckBoxMenuItem toggleBotsItem;
    private JRadioButtonMenuItem botViewMenuItem;

    private JButton liveRoomBtn;
    private JButton startBtn;
    private JButton pauseBtn;
    private JButton nextBtn;
    private JToggleButton soundToggleBtn;

    private LoginPanel loginPanel;
    private SetupPanel setupPanel;
    private LiveAuctionPanel liveAuctionPanel;
    private PlayerPoolPanel playerPoolPanel;
    private TeamsPanel teamsPanel;
    private BotControlPanel botControlPanel;
    private TradeWindowPanel tradeWindowPanel;
    private ReportsPanel reportsPanel;
    private FinalSummaryPanel summaryPanel;

    private User currentUser;
    private Team currentHumanTeam;
    private AuctionServer auctionServer;
    private AuctionClient auctionClient;
    private WebAuctionPortalServer webPortalServer;

    public MainFrame() {
        super("CricBid: Live Cricket Player Auction System");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1240, 810);
        setMinimumSize(new Dimension(1020, 680));
        setLocationRelativeTo(null);

        this.authService = new AuthService();
        this.teamService = new TeamService();
        this.tradeService = new TradeService();
        this.reportService = new ReportService();
        this.auctionSubject = new AuctionSubject();
        this.auctionService = new AuctionService(auctionSubject);

        this.cardLayout = new CardLayout();
        this.cardsPanel = new JPanel(cardLayout);

        initScreens();
        initMenuBar();
        initToolBar();
        setToolbarAndMenusForSession(false, false);

        add(mainToolBar, BorderLayout.NORTH);
        add(cardsPanel, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleExit();
            }
        });

        showCard(CARD_LOGIN);
    }

    private void initScreens() {
        loginPanel = new LoginPanel(authService, this::onLoginSuccess);
        setupPanel = new SetupPanel(auctionService, teamService, this::onAuctionStarted);
        liveAuctionPanel = new LiveAuctionPanel(this, auctionService, teamService);
        playerPoolPanel = new PlayerPoolPanel();
        teamsPanel = new TeamsPanel(teamService);
        botControlPanel = new BotControlPanel(auctionService, teamService);
        tradeWindowPanel = new TradeWindowPanel(teamService, tradeService);
        reportsPanel = new ReportsPanel(reportService);
        summaryPanel = new FinalSummaryPanel(teamService, reportService);

        cardsPanel.add(loginPanel, CARD_LOGIN);
        cardsPanel.add(setupPanel, CARD_SETUP);
        cardsPanel.add(liveAuctionPanel, CARD_LIVE);
        cardsPanel.add(playerPoolPanel, CARD_POOL);
        cardsPanel.add(teamsPanel, CARD_TEAMS);
        cardsPanel.add(botControlPanel, CARD_BOTS);
        cardsPanel.add(tradeWindowPanel, CARD_TRADE);
        cardsPanel.add(reportsPanel, CARD_REPORTS);
        cardsPanel.add(summaryPanel, CARD_SUMMARY);
    }

    private void initMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        newAuctionItem = new JMenuItem("New Auction Setup", KeyEvent.VK_N);
        newAuctionItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        newAuctionItem.addActionListener(e -> showCard(CARD_SETUP));
        fileMenu.add(newAuctionItem);

        JMenuItem logoutItem = new JMenuItem("Switch Account / Disconnect");
        logoutItem.addActionListener(e -> handleLogout());
        fileMenu.add(logoutItem);

        JMenuItem customizeColorItem = new JMenuItem("Choose Theme Accent Color (JColorChooser)");
        customizeColorItem.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Select Application Theme Accent", new Color(15, 23, 42));
            if (chosen != null) {
                mainToolBar.setBackground(chosen);
            }
        });
        fileMenu.add(customizeColorItem);

        fileMenu.add(new JSeparator());

        JMenuItem exitItem = new JMenuItem("Exit Application", KeyEvent.VK_Q);
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        exitItem.addActionListener(e -> handleExit());
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);

        auctionMenu = new JMenu("Auction");
        auctionMenu.setMnemonic(KeyEvent.VK_A);

        JMenuItem startItem = new JMenuItem("Start / Resume Live Round");
        startItem.addActionListener(e -> auctionService.resumeAuction());
        auctionMenu.add(startItem);

        JMenuItem pauseItem = new JMenuItem("Pause Live Round");
        pauseItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));
        pauseItem.addActionListener(e -> auctionService.pauseAuction());
        auctionMenu.add(pauseItem);

        JMenuItem nextPlayerItem = new JMenuItem("Skip / Next Player");
        nextPlayerItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK));
        nextPlayerItem.addActionListener(e -> auctionService.presentNextPlayer());
        auctionMenu.add(nextPlayerItem);

        menuBar.add(auctionMenu);

        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);

        ButtonGroup viewGroup = new ButtonGroup();
        String[] viewNames = {"Live Bidding Block", "Player Pool Database", "Team Rosters", "Trade Desk", "Analytics & Charts", "Tournament Summary"};
        String[] cardKeys = {CARD_LIVE, CARD_POOL, CARD_TEAMS, CARD_TRADE, CARD_REPORTS, CARD_SUMMARY};

        for (int i = 0; i < viewNames.length; i++) {
            final String targetCard = cardKeys[i];
            JRadioButtonMenuItem rBtn = new JRadioButtonMenuItem(viewNames[i], i == 0);
            viewGroup.add(rBtn);
            rBtn.addActionListener(e -> showCard(targetCard));
            viewMenu.add(rBtn);
        }

        menuBar.add(viewMenu);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem aboutItem = new JMenuItem("About CricBid & AOOP Docs");
        aboutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        aboutItem.addActionListener(e -> {
            AboutDialog dialog = new AboutDialog(this);
            dialog.setVisible(true);
        });
        helpMenu.add(aboutItem);

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    private void initToolBar() {
        mainToolBar = new JToolBar("Auction Navigation Bar");
        mainToolBar.setFloatable(false);
        mainToolBar.setBackground(new Color(15, 23, 42));
        mainToolBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(245, 158, 11)),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        liveRoomBtn = createToolBarButton("* Live Arena", "Return to the live bidding block",
                new Color(245, 158, 11), new Color(15, 23, 42), e -> showCard(CARD_LIVE));

        startBtn = createToolBarButton(">> Resume", "Resume countdown (Admin Host only)",
                new Color(22, 163, 74), Color.WHITE, e -> {
            showCard(CARD_LIVE);
            auctionService.resumeAuction();
        });

        pauseBtn = createToolBarButton("|| Pause", "Halt the live countdown timer (Admin Host only)",
                new Color(51, 65, 85), Color.WHITE, e -> auctionService.pauseAuction());

        nextBtn = createToolBarButton(">> Next Player", "Skip to next available auction player (Admin Host only)",
                new Color(37, 99, 235), Color.WHITE, e -> auctionService.presentNextPlayer());

        JButton poolBtn = createToolBarButton("Player Pool", "Search and inspect all cricket players",
                new Color(30, 41, 59), Color.WHITE, e -> showCard(CARD_POOL));
        JButton teamsBtn = createToolBarButton("Franchises", "View franchise rosters and budget charts",
                new Color(30, 41, 59), Color.WHITE, e -> showCard(CARD_TEAMS));
        JButton tradeBtn = createToolBarButton("Trade Desk", "Swap players between two teams",
                new Color(30, 41, 59), Color.WHITE, e -> showCard(CARD_TRADE));
        JButton reportsBtn = createToolBarButton("Analytics", "View spending bar chart, pie chart and CSV export",
                new Color(30, 41, 59), Color.WHITE, e -> showCard(CARD_REPORTS));
        JButton summaryBtn = createToolBarButton("Summary", "Audit rules compliance and view top buys",
                new Color(30, 41, 59), Color.WHITE, e -> showCard(CARD_SUMMARY));
        JButton webPortalBtn = createToolBarButton("Web Portal (8080)", "Open or copy the Live Java Web Browser Portal URL",
                new Color(14, 116, 144), Color.WHITE, e -> showWebPortalDialog());

        soundToggleBtn = new JToggleButton("Sound: ON", true);
        soundToggleBtn.setFont(Main.uiFont(Font.BOLD, 12));
        soundToggleBtn.setFocusPainted(false);
        soundToggleBtn.setBackground(new Color(22, 101, 52));
        soundToggleBtn.setForeground(Color.WHITE);
        soundToggleBtn.addActionListener(e -> {
            boolean on = soundToggleBtn.isSelected();
            SoundEffectService.setSoundEnabled(on);
            soundToggleBtn.setText(on ? "Sound: ON" : "Sound: OFF");
            soundToggleBtn.setBackground(on ? new Color(22, 101, 52) : new Color(71, 85, 105));
        });

        mainToolBar.add(liveRoomBtn);
        mainToolBar.add(Box.createHorizontalStrut(4));
        mainToolBar.add(startBtn);
        mainToolBar.add(Box.createHorizontalStrut(4));
        mainToolBar.add(pauseBtn);
        mainToolBar.add(Box.createHorizontalStrut(4));
        mainToolBar.add(nextBtn);
        mainToolBar.addSeparator(new Dimension(12, 24));
        mainToolBar.add(poolBtn);
        mainToolBar.add(Box.createHorizontalStrut(4));
        mainToolBar.add(teamsBtn);
        mainToolBar.add(Box.createHorizontalStrut(4));
        mainToolBar.add(tradeBtn);
        mainToolBar.add(Box.createHorizontalStrut(4));
        mainToolBar.add(reportsBtn);
        mainToolBar.add(Box.createHorizontalStrut(4));
        mainToolBar.add(summaryBtn);
        mainToolBar.add(Box.createHorizontalStrut(4));
        mainToolBar.add(webPortalBtn);
        mainToolBar.add(Box.createHorizontalGlue());
        mainToolBar.add(soundToggleBtn);
    }

    private void showWebPortalDialog() {
        String cloudUrl = "https://nandhakishoreramanan.github.io/cricbid-live-auction/";
        String lanUrl = "http://" + NetworkUtils.getLocalLanIp() + ":8080";
        String msg = "CricBid Live Web Portal & 24/7 Cloud Deployment:\n\n"
                + "1. 24/7 PERMANENT CLOUD DEPLOYMENT (Works even when your laptop is OFF):\n"
                + "   " + cloudUrl + "\n\n"
                + "2. LIVE JAVA HTTP SERVER ON WI-FI / HOTSPOT (Synced with this Swing Window):\n"
                + "   " + lanUrl + "\n\n"
                + "3. LOCAL JAVA SERVER ON THIS LAPTOP:\n"
                + "   http://localhost:8080";
        Object[] options = {"Open 24/7 Cloud URL", "Open Local Java Web Server (8080)", "Copy Cloud URL", "Close"};
        int choice = JOptionPane.showOptionDialog(this, msg, "CricBid Live Web & Cloud Deployment",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (choice == 0) {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(cloudUrl));
            } catch (Exception ignored) {}
        } else if (choice == 1) {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("http://localhost:8080"));
            } catch (Exception ignored) {}
        } else if (choice == 2) {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(cloudUrl), null);
            JOptionPane.showMessageDialog(this, "Copied 24/7 Cloud URL to clipboard:\n" + cloudUrl,
                    "URL Copied", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void refreshCombinedClientList() {
        java.util.List<String> combined = new java.util.ArrayList<>();
        if (auctionServer != null) {
            combined.addAll(auctionServer.getConnectedClientLabels());
        }
        if (webPortalServer != null) {
            for (String w : webPortalServer.getActiveWebParticipantLabels()) {
                if (!combined.contains(w)) {
                    combined.add(w);
                }
            }
        }
        setupPanel.updateConnectedClients(combined);
        liveAuctionPanel.updateHostBanner(combined);
    }

    private JButton createToolBarButton(String text, String tooltip, Color bg, Color fg, java.awt.event.ActionListener listener) {
        JButton btn = new JButton(text);
        btn.setToolTipText(tooltip);
        btn.setFont(Main.uiFont(Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.addActionListener(listener);
        return btn;
    }

    private void setToolbarAndMenusForSession(boolean loggedIn, boolean isAdmin) {
        mainToolBar.setVisible(loggedIn);
        newAuctionItem.setEnabled(loggedIn && isAdmin);
        auctionMenu.setEnabled(loggedIn && isAdmin);
        if (botViewMenuItem != null) {
            botViewMenuItem.setEnabled(loggedIn && isAdmin);
        }
        startBtn.setVisible(isAdmin);
        pauseBtn.setVisible(isAdmin);
        nextBtn.setVisible(isAdmin);
    }

    public void showCard(String cardName) {
        if (currentUser != null) {
            switch (cardName) {
                case CARD_LIVE:
                    liveAuctionPanel.refreshScoreboardStrip();
                    break;
                case CARD_POOL:
                    playerPoolPanel.refreshPlayers();
                    break;
                case CARD_TEAMS:
                    teamsPanel.refreshTeams();
                    break;
                case CARD_BOTS:
                    botControlPanel.refreshBots();
                    break;
                case CARD_TRADE:
                    tradeWindowPanel.loadTeams();
                    break;
                case CARD_REPORTS:
                    reportsPanel.refreshReports();
                    break;
                case CARD_SUMMARY:
                    summaryPanel.refreshSummary();
                    break;
                default:
                    break;
            }
        }
        cardLayout.show(cardsPanel, cardName);
    }

    private void onLoginSuccess(User user, String hostIp) {
        stopNetworking();
        this.currentUser = user;
        boolean isAdmin = user.isAdmin();
        setToolbarAndMenusForSession(true, isAdmin);

        int serverPort = DBConnection.getInstance().getServerPort();

        if (isAdmin) {
            this.currentHumanTeam = null;
            this.auctionServer = new AuctionServer(serverPort, auctionService, teamService);
            this.auctionServer.setClientListListener(clients -> refreshCombinedClientList());
            this.auctionServer.setActivityLogListener(liveAuctionPanel::appendExternalLog);
            liveAuctionPanel.setAuctionServer(this.auctionServer);

            try {
                this.auctionServer.start();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Could not start Host Socket Server on TCP port " + serverPort + ":\n" + ex.getMessage() +
                        "\n\nMake sure another Admin Host window isn't already running on this laptop.",
                        "Host Server Port Busy", JOptionPane.WARNING_MESSAGE);
            }

            // Start the built-in Pure Java HTTP Web Portal on port 8080
            this.webPortalServer = new WebAuctionPortalServer(8080, authService, auctionService, teamService);
            this.webPortalServer.setAuctionServer(this.auctionServer);
            this.webPortalServer.setExternalLogCallback(liveAuctionPanel::appendExternalLog);
            this.webPortalServer.setClientListRefreshCallback(this::refreshCombinedClientList);
            try {
                this.webPortalServer.start();
            } catch (IOException ex) {
                System.err.println("[WebPortal] Could not bind port 8080: " + ex.getMessage());
            }

            setTitle(String.format("CricBid [HOST] — %s  |  App: %s:%d  |  Web: http://%s:8080",
                    user.getUsername(), NetworkUtils.getLocalLanIp(), serverPort, NetworkUtils.getLocalLanIp()));
            setupPanel.initUserSession(user);
            showCard(CARD_SETUP);
        } else {
            try {
                int teamId = (user.getTeamId() != null) ? user.getTeamId() : 1;
                this.currentHumanTeam = teamService.getTeamById(teamId);
            } catch (SQLException ignored) {}

            String teamName = (currentHumanTeam != null) ? currentHumanTeam.getName() : "Franchise";
            Integer teamId = (currentHumanTeam != null) ? currentHumanTeam.getTeamId() : user.getTeamId();

            this.auctionClient = new AuctionClient(hostIp, serverPort, user.getUsername(), teamId, teamName, auctionSubject);
            this.auctionClient.setConnectionStatusListener(liveAuctionPanel::updateClientBanner);
            this.auctionClient.setBidRejectedListener(liveAuctionPanel::showBidRejected);
            this.auctionClient.setUpcomingPoolListener(liveAuctionPanel::setSyncedUpcomingPool);
            this.auctionClient.setSyncStateListener(liveAuctionPanel::applySyncState);
            this.auctionClient.setAuctionResetListener(liveAuctionPanel::handleAuctionReset);
            this.auctionClient.setLogBroadcastListener(liveAuctionPanel::appendExternalLog);

            liveAuctionPanel.setAuctionServer(null);
            liveAuctionPanel.setSession(user, currentHumanTeam, auctionClient);
            this.auctionClient.start();

            setTitle(String.format("CricBid [PARTICIPANT: %s] — %s @ %s:%d",
                    teamName, user.getUsername(), hostIp, serverPort));
            showCard(CARD_LIVE);
        }
    }

    private void onAuctionStarted(boolean quickMode) {
        if (toggleBotsItem != null) {
            toggleBotsItem.setSelected(auctionService.isBotsEnabled());
        }
        if (auctionServer != null) {
            auctionServer.broadcastAuctionReset(quickMode);
        }
        if (currentUser != null && !currentUser.isAdmin() && currentUser.getTeamId() != null) {
            try {
                this.currentHumanTeam = teamService.getTeamById(currentUser.getTeamId());
            } catch (SQLException ignored) {}
        }

        liveAuctionPanel.setAuctionServer(auctionServer);
        liveAuctionPanel.setSession(currentUser, currentHumanTeam, null);
        refreshCombinedClientList();
        showCard(CARD_LIVE);
    }

    private void handleLogout() {
        stopNetworking();
        auctionService.shutdown();
        this.currentUser = null;
        this.currentHumanTeam = null;
        setToolbarAndMenusForSession(false, false);
        setTitle("CricBid: Live Cricket Player Auction System");
        showCard(CARD_LOGIN);
    }

    private void stopNetworking() {
        if (auctionClient != null) {
            auctionClient.stop();
            auctionClient = null;
        }
        if (auctionServer != null) {
            auctionServer.stop();
            auctionServer = null;
        }
        if (webPortalServer != null) {
            webPortalServer.stop();
            webPortalServer = null;
        }
    }

    private void handleExit() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to stop the auction and exit CricBid?",
                "Confirm Exit", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            stopNetworking();
            auctionService.shutdown();
            dispose();
            System.exit(0);
        }
    }
}

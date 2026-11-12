package com.cricbid.network;

import com.cricbid.dao.PlayerDAO;
import com.cricbid.dao.TeamDAO;
import com.cricbid.dao.impl.PlayerDAOImpl;
import com.cricbid.dao.impl.TeamDAOImpl;
import com.cricbid.exception.AuthenticationException;
import com.cricbid.exception.CricBidException;
import com.cricbid.model.Bid;
import com.cricbid.model.OwnerType;
import com.cricbid.model.Player;
import com.cricbid.model.Team;
import com.cricbid.model.User;
import com.cricbid.observer.AuctionObserver;
import com.cricbid.service.AuctionService;
import com.cricbid.service.AuthService;
import com.cricbid.service.RuleValidator;
import com.cricbid.service.TeamService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.swing.SwingUtilities;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Built-in Pure Java HTTP Web Server (com.sun.net.httpserver.HttpServer) on port 8080.
 * Allows any laptop or phone to join and bid in the live auction via a Web Browser
 * (LAN URL or Free Public Cloudflare Tunnel URL) while syncing in real time with
 * the Java Swing Desktop GUI and MySQL JDBC Database.
 */
public class WebAuctionPortalServer implements AuctionObserver {

    private final int httpPort;
    private final AuthService authService;
    private final AuctionService auctionService;
    private final TeamService teamService;
    private AuctionServer auctionServer;

    private HttpServer httpServer;
    private volatile boolean active = false;
    private volatile String publicTunnelUrl = null;
    private Process tunnelProcess = null;

    private final LinkedList<String> recentFeed = new LinkedList<>();
    private final Map<String, Long> activeWebUsers = new ConcurrentHashMap<>();
    private Consumer<String> externalLogCallback;
    private Runnable clientListRefreshCallback;

    private volatile String lastBannerEvent = "";
    private volatile long lastBannerTimestamp = 0L;

    public WebAuctionPortalServer(int httpPort,
                                  AuthService authService,
                                  AuctionService auctionService,
                                  TeamService teamService) {
        this.httpPort = httpPort;
        this.authService = authService;
        this.auctionService = auctionService;
        this.teamService = teamService;
    }

    public void setAuctionServer(AuctionServer auctionServer) {
        this.auctionServer = auctionServer;
    }

    public void setExternalLogCallback(Consumer<String> callback) {
        this.externalLogCallback = callback;
    }

    public void setClientListRefreshCallback(Runnable callback) {
        this.clientListRefreshCallback = callback;
    }

    public void setPublicTunnelUrl(String url) {
        this.publicTunnelUrl = url;
    }

    public String getPublicTunnelUrl() {
        return publicTunnelUrl;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public String getLanWebUrl() {
        return "http://" + NetworkUtils.getLocalLanIp() + ":" + httpPort;
    }

    public List<String> getActiveWebParticipantLabels() {
        long cutoff = System.currentTimeMillis() - 15000L;
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, Long> entry : activeWebUsers.entrySet()) {
            if (entry.getValue() >= cutoff) {
                list.add(entry.getKey() + " [Web]");
            } else {
                activeWebUsers.remove(entry.getKey());
            }
        }
        return list;
    }

    public void start() throws IOException {
        if (active) return;

        httpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", httpPort), 0);
        httpServer.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "CricBid-WebPortal-Worker");
            t.setDaemon(true);
            return t;
        }));

        httpServer.createContext("/", this::handleIndexPage);
        httpServer.createContext("/bg.png", this::handleStadiumBg);
        httpServer.createContext("/api/login", this::handleApiLogin);
        httpServer.createContext("/api/state", this::handleApiState);
        httpServer.createContext("/api/bid", this::handleApiBid);
        httpServer.createContext("/api/pass", this::handleApiPass);
        httpServer.createContext("/api/chat", this::handleApiChat);
        httpServer.createContext("/api/admin", this::handleApiAdmin);

        auctionService.getAuctionSubject().addObserver(this);
        appendFeed("[WEB PORTAL] Live Java HTTP Broadcast Server active on port " + httpPort);

        httpServer.start();
        active = true;
        startPublicInternetTunnel();
    }

    public void startPublicInternetTunnel() {
        if (tunnelProcess != null && tunnelProcess.isAlive()) return;
        Thread t = new Thread(() -> {
            try {
                String cfBin = new java.io.File("/opt/homebrew/bin/cloudflared").exists()
                        ? "/opt/homebrew/bin/cloudflared" : "cloudflared";
                ProcessBuilder pb = new ProcessBuilder(
                        cfBin, "tunnel", "--url", "http://localhost:" + httpPort
                );
                pb.redirectErrorStream(true);
                tunnelProcess = pb.start();
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(tunnelProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    java.util.regex.Pattern urlPat = java.util.regex.Pattern.compile("(https://[a-zA-Z0-9-]+\\.trycloudflare\\.com)");
                    while (active && (line = br.readLine()) != null) {
                        java.util.regex.Matcher m = urlPat.matcher(line);
                        if (m.find()) {
                            publicTunnelUrl = m.group(1);
                            String msg = "[PUBLIC WEB URL] Live Internet Website Ready: " + publicTunnelUrl;
                            appendFeed(msg);
                            if (externalLogCallback != null) {
                                SwingUtilities.invokeLater(() -> externalLogCallback.accept(msg));
                            }
                            publishTunnelToCloudRepo(publicTunnelUrl);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }, "CricBid-PublicTunnel-Thread");
        t.setDaemon(true);
        t.start();
    }

    private void publishTunnelToCloudRepo(String tunnelUrl) {
        Thread pub = new Thread(() -> {
            try {
                java.io.File f = new java.io.File("docs/tunnel.json");
                if (f.getParentFile() != null && f.getParentFile().exists()) {
                    java.nio.file.Files.writeString(f.toPath(),
                            "{\"url\":\"" + tunnelUrl + "\"}\n", StandardCharsets.UTF_8);
                    ProcessBuilder pb = new ProcessBuilder("sh", "-c",
                            "export PATH=/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:$PATH; git add docs/tunnel.json && git commit -m 'Update live laptop tunnel URL' && git push origin main");
                    pb.start();
                }
            } catch (Exception ignored) {}
        }, "CricBid-TunnelPublisher");
        pub.setDaemon(true);
        pub.start();
    }

    public void stop() {
        active = false;
        auctionService.getAuctionSubject().removeObserver(this);
        if (tunnelProcess != null) {
            tunnelProcess.destroyForcibly();
            tunnelProcess = null;
        }
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    public synchronized void appendFeed(String line) {
        if (line == null || line.isBlank()) return;
        recentFeed.addLast(line);
        while (recentFeed.size() > 60) {
            recentFeed.removeFirst();
        }
    }

    private void handleStadiumBg(HttpExchange ex) throws IOException {
        try (InputStream is = WebAuctionPortalServer.class.getResourceAsStream("/images/cricket_stadium_bg.png")) {
            if (is == null) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = readAllBytes(is);
            ex.getResponseHeaders().set("Content-Type", "image/png");
            ex.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private void handleIndexPage(HttpExchange ex) throws IOException {
        byte[] htmlBytes = buildWebPortalHtml().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, htmlBytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(htmlBytes);
        }
    }

    private void handleApiLogin(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 200, "{\"ok\":true}");
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormBody(ex);
        String username = params.getOrDefault("username", "").trim();
        String password = params.getOrDefault("password", "").trim();

        try {
            User user = authService.login(username, password);
            String teamName = "Auctioneer Host";
            int teamId = 0;
            if (!user.isAdmin() && user.getTeamId() != null) {
                teamId = user.getTeamId();
                Team t = teamService.getTeamById(teamId);
                if (t != null) {
                    teamName = t.getName();
                }
            }
            if (!user.isAdmin()) {
                String label = teamName + " (" + user.getUsername() + ")";
                activeWebUsers.put(label, System.currentTimeMillis());
                if (clientListRefreshCallback != null) {
                    SwingUtilities.invokeLater(clientListRefreshCallback);
                }
            }
            String json = String.format(
                    "{\"ok\":true,\"username\":\"%s\",\"role\":\"%s\",\"isAdmin\":%b,\"teamId\":%d,\"teamName\":\"%s\"}",
                    esc(user.getUsername()), esc(user.getRole().name()), user.isAdmin(), teamId, esc(teamName)
            );
            sendJson(ex, 200, json);
        } catch (AuthenticationException | SQLException e) {
            sendJson(ex, 200, String.format("{\"ok\":false,\"error\":\"%s\"}", esc(e.getMessage())));
        }
    }

    private void handleApiState(HttpExchange ex) throws IOException {
        Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
        String who = query.get("who");
        if (who != null && !who.isBlank()) {
            activeWebUsers.put(who, System.currentTimeMillis());
            if (clientListRefreshCallback != null) {
                SwingUtilities.invokeLater(clientListRefreshCallback);
            }
        }

        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\"ok\":true");
        sb.append(",\"running\":").append(auctionService.isRunning());
        sb.append(",\"paused\":").append(auctionService.isPaused());
        sb.append(",\"seconds\":").append(auctionService.getSecondsRemaining());
        sb.append(",\"maxSeconds\":").append(Math.max(5, auctionService.getRoundDurationSec()));
        sb.append(",\"currentPrice\":").append(String.format("%.2f", auctionService.getCurrentPrice()));
        sb.append(",\"roundHasBids\":").append(auctionService.isRoundHasBids());

        double nextValid = RuleValidator.getNextValidBidAmount(
                auctionService.getCurrentPrice(), auctionService.isRoundHasBids());
        sb.append(",\"nextBid\":").append(String.format("%.2f", nextValid));

        Team lead = auctionService.getCurrentLeadingTeam();
        sb.append(",\"leadingTeamId\":").append(lead != null ? lead.getTeamId() : 0);
        sb.append(",\"leadingTeamName\":\"").append(lead != null ? esc(lead.getName()) : "None (Awaiting Opening Bid)").append("\"");

        long ageMs = System.currentTimeMillis() - lastBannerTimestamp;
        sb.append(",\"bannerEvent\":\"").append(ageMs < 4000 ? esc(lastBannerEvent) : "").append("\"");

        Player p = auctionService.getCurrentPlayer();
        if (p != null) {
            double rpo = (p.getStrikeRate() * 6.0) / 100.0;
            String row1Label;
            double row1Pct;
            String row1Val;
            String row2Label;
            double row2Pct;
            String row2Val;
            String summaryLine;

            switch (p.getRole()) {
                case BOWLER:
                    row1Label = "BOWL ECONOMY";
                    row1Pct = Math.max(15, Math.min(100, ((11.5 - p.getEconomy()) / 6.0) * 100.0));
                    row1Val = String.format("%.2f RPO", p.getEconomy());
                    row2Label = "CAREER WICKETS";
                    row2Pct = Math.max(15, Math.min(100, (p.getWickets() / 160.0) * 100.0));
                    row2Val = p.getWickets() + " Wkts";
                    summaryLine = String.format("Bowling Stats  |  Economy: %.2f RPO  |  Career Wickets: %d  |  Rating: %d/100",
                            p.getEconomy(), p.getWickets(), p.getRating());
                    break;
                case ALLROUNDER:
                    row1Label = "STRIKE RATE";
                    row1Pct = Math.max(15, Math.min(100, (p.getStrikeRate() / 185.0) * 100.0));
                    row1Val = String.format("%.1f (%.2f RPO)", p.getStrikeRate(), rpo);
                    row2Label = "BOWL ECONOMY";
                    row2Pct = Math.max(15, Math.min(100, ((11.5 - p.getEconomy()) / 6.0) * 100.0));
                    row2Val = String.format("%.2f RPO (%dW)", p.getEconomy(), p.getWickets());
                    summaryLine = String.format("All-Round Stats  |  SR: %.1f (%.2f RPO)  |  Economy: %.2f RPO (%d Wkts)  |  Avg: %.1f",
                            p.getStrikeRate(), rpo, p.getEconomy(), p.getWickets(), p.getBattingAvg());
                    break;
                case WICKETKEEPER:
                    row1Label = "STRIKE RATE";
                    row1Pct = Math.max(15, Math.min(100, (p.getStrikeRate() / 185.0) * 100.0));
                    row1Val = String.format("%.1f SR", p.getStrikeRate());
                    row2Label = "RUN RATE / AVG";
                    row2Pct = Math.max(15, Math.min(100, (p.getBattingAvg() / 55.0) * 100.0));
                    row2Val = String.format("%.2f RPO (Avg %.1f)", rpo, p.getBattingAvg());
                    summaryLine = String.format("Keeper-Bat Stats  |  Strike Rate: %.1f  |  Run Rate: %.2f RPO  |  Avg: %.1f  |  Dismissals: %d",
                            p.getStrikeRate(), rpo, p.getBattingAvg(), p.getDismissals());
                    break;
                case BATSMAN:
                default:
                    row1Label = "STRIKE RATE";
                    row1Pct = Math.max(15, Math.min(100, (p.getStrikeRate() / 185.0) * 100.0));
                    row1Val = String.format("%.2f SR", p.getStrikeRate());
                    row2Label = "RUN RATE / AVG";
                    row2Pct = Math.max(15, Math.min(100, (p.getBattingAvg() / 58.0) * 100.0));
                    row2Val = String.format("%.2f RPO (Avg %.1f)", rpo, p.getBattingAvg());
                    summaryLine = String.format("Batting Stats  |  Strike Rate: %.2f  |  Run Rate: %.2f RPO  |  Bat Avg: %.2f",
                            p.getStrikeRate(), rpo, p.getBattingAvg());
                    break;
            }

            sb.append(",\"player\":{");
            sb.append("\"id\":").append(p.getPlayerId());
            sb.append(",\"name\":\"").append(esc(p.getName())).append("\"");
            sb.append(",\"role\":\"").append(esc(p.getRole().getDisplayName().toUpperCase())).append("\"");
            sb.append(",\"overseas\":").append(p.isOverseas());
            sb.append(",\"basePrice\":").append(String.format("%.2f", p.getBasePrice()));
            sb.append(",\"rating\":").append(p.getRating());
            sb.append(",\"summary\":\"").append(esc(summaryLine)).append("\"");
            sb.append(",\"row1Label\":\"").append(esc(row1Label)).append("\"");
            sb.append(",\"row1Pct\":").append((int) row1Pct);
            sb.append(",\"row1Val\":\"").append(esc(row1Val)).append("\"");
            sb.append(",\"row2Label\":\"").append(esc(row2Label)).append("\"");
            sb.append(",\"row2Pct\":").append((int) row2Pct);
            sb.append(",\"row2Val\":\"").append(esc(row2Val)).append("\"");
            sb.append("}");
        } else {
            sb.append(",\"player\":null");
        }

        // Teams scoreboard strip
        sb.append(",\"teams\":[");
        try {
            List<Team> teams = teamService.getAllTeamsWithSquads();
            for (int i = 0; i < teams.size(); i++) {
                Team t = teams.get(i);
                if (i > 0) sb.append(",");
                sb.append("{\"id\":").append(t.getTeamId())
                  .append(",\"name\":\"").append(esc(t.getName())).append("\"")
                  .append(",\"purse\":").append(String.format("%.2f", t.getPurseRemaining()))
                  .append(",\"squad\":").append(t.getSquadSize())
                  .append("}");
            }
        } catch (SQLException ignored) {}
        sb.append("]");

        // Upcoming pool (first 15 upcoming)
        sb.append(",\"upcoming\":[");
        List<Player> pool = auctionService.getAuctionPool();
        int startIdx = auctionService.getCurrentPoolIndex();
        int count = 0;
        for (int i = startIdx; i < pool.size() && count < 15; i++, count++) {
            Player up = pool.get(i);
            if (count > 0) sb.append(",");
            double rpo = (up.getStrikeRate() * 6.0) / 100.0;
            String keyStat;
            switch (up.getRole()) {
                case BOWLER:
                    keyStat = String.format("Econ %.2f RPO | %d Wkts", up.getEconomy(), up.getWickets());
                    break;
                case ALLROUNDER:
                    keyStat = String.format("SR %.1f (%.1f RPO) | Econ %.2f", up.getStrikeRate(), rpo, up.getEconomy());
                    break;
                default:
                    keyStat = String.format("SR %.1f | %.2f RPO | Avg %.1f", up.getStrikeRate(), rpo, up.getBattingAvg());
                    break;
            }
            sb.append("{\"id\":").append(up.getPlayerId())
              .append(",\"name\":\"").append(esc(up.getName() + (up.isOverseas() ? " (OS)" : ""))).append("\"")
              .append(",\"role\":\"").append(esc(up.getRole().getDisplayName())).append("\"")
              .append(",\"stats\":\"").append(esc(keyStat)).append("\"")
              .append(",\"rating\":").append(up.getRating())
              .append(",\"base\":").append(String.format("%.2f", up.getBasePrice()))
              .append("}");
        }
        sb.append("]");

        // Feed lines
        sb.append(",\"feed\":[");
        synchronized (this) {
            int idx = 0;
            for (String line : recentFeed) {
                if (idx++ > 0) sb.append(",");
                sb.append("\"").append(esc(line)).append("\"");
            }
        }
        sb.append("]}");

        sendJson(ex, 200, sb.toString());
    }

    private void handleApiBid(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 200, "{\"ok\":true}");
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"Method not allowed\"}");
            return;
        }
        Map<String, String> params = parseFormBody(ex);
        int teamId;
        double amount;
        try {
            teamId = Integer.parseInt(params.getOrDefault("teamId", "0"));
            amount = Double.parseDouble(params.getOrDefault("amount", "0"));
        } catch (NumberFormatException e) {
            sendJson(ex, 200, "{\"ok\":false,\"error\":\"Invalid bid amount.\"}");
            return;
        }

        final String[] errorHolder = new String[1];
        try {
            Runnable action = () -> {
                try {
                    Team team = teamService.getTeamById(teamId);
                    if (team == null) {
                        errorHolder[0] = "Franchise not found.";
                        return;
                    }
                    auctionService.placeHumanBid(team, amount);
                } catch (CricBidException | SQLException e) {
                    errorHolder[0] = e.getMessage();
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                action.run();
            } else {
                SwingUtilities.invokeAndWait(action);
            }
        } catch (Exception e) {
            errorHolder[0] = e.getMessage();
        }

        if (errorHolder[0] != null) {
            sendJson(ex, 200, String.format("{\"ok\":false,\"error\":\"%s\"}", esc(errorHolder[0])));
        } else {
            sendJson(ex, 200, "{\"ok\":true}");
        }
    }

    private void handleApiPass(HttpExchange ex) throws IOException {
        Map<String, String> params = parseFormBody(ex);
        String teamName = params.getOrDefault("teamName", "Franchise");
        String msg = "  >> [PASS] " + teamName + " passed on current player.";
        appendFeed(msg);
        if (externalLogCallback != null) {
            SwingUtilities.invokeLater(() -> externalLogCallback.accept(msg));
        }
        sendJson(ex, 200, "{\"ok\":true}");
    }

    private void handleApiChat(HttpExchange ex) throws IOException {
        Map<String, String> params = parseFormBody(ex);
        String sender = params.getOrDefault("sender", "Participant");
        String text = params.getOrDefault("text", "").trim();
        if (!text.isEmpty()) {
            String formatted = String.format("[CHAT - %s]: %s", sender, text);
            appendFeed(formatted);
            if (auctionServer != null) {
                auctionServer.broadcastChatMessage(sender, text);
            } else if (externalLogCallback != null) {
                SwingUtilities.invokeLater(() -> externalLogCallback.accept(formatted));
            }
        }
        sendJson(ex, 200, "{\"ok\":true}");
    }

    private void handleApiAdmin(HttpExchange ex) throws IOException {
        Map<String, String> params = parseFormBody(ex);
        String cmd = params.getOrDefault("cmd", "");
        SwingUtilities.invokeLater(() -> {
            try {
                switch (cmd) {
                    case "start":
                    case "resume":
                        auctionService.setBotsEnabled(false);
                        auctionService.continueOrStartAuction(15, true);
                        break;
                    case "reset":
                        java.util.Set<Integer> humanIds = new java.util.HashSet<>(java.util.Arrays.asList(1, 2, 3, 4, 5, 6));
                        auctionService.resetAuctionToInitialState(100.0, humanIds, 15, true);
                        if (auctionServer != null) {
                            auctionServer.broadcastAuctionReset(true);
                        }
                        appendFeed("=== [RESET] AUCTION RESET TO INITIAL STATE (All Teams 100 Cr | Player #1) ===");
                        break;
                    case "pause":
                        if (auctionService.isPaused()) {
                            auctionService.resumeAuction();
                        } else {
                            auctionService.pauseAuction();
                        }
                        break;
                    case "next":
                        auctionService.presentNextPlayer();
                        break;
                    default:
                        break;
                }
            } catch (SQLException ignored) {}
        });
        sendJson(ex, 200, "{\"ok\":true}");
    }

    // --- AuctionObserver hooks ---

    @Override
    public void onPlayerUpForAuction(Player player, double currentPrice, int roundDurationSec) {
        appendFeed(String.format(">>> * ON THE BLOCK: %s (%s) | Base: %.2f Cr",
                player.getName(), player.getRole().getDisplayName(), player.getBasePrice()));
    }

    @Override
    public void onBidPlaced(Bid bid, Team team, Player player) {
        appendFeed(String.format("  >> [BID] %s bid %.2f Cr for %s",
                team.getName(), bid.getAmount(), player.getName()));
    }

    @Override
    public void onPlayerSold(Player player, Team team, double finalPrice) {
        String msg = String.format("SOLD! %s acquired by %s for %.2f Cr!",
                player.getName(), team.getName(), finalPrice);
        this.lastBannerEvent = msg;
        this.lastBannerTimestamp = System.currentTimeMillis();
        appendFeed(">>> * " + msg);
    }

    @Override
    public void onPlayerUnsold(Player player) {
        String msg = String.format("UNSOLD: %s received no bids.", player.getName());
        this.lastBannerEvent = msg;
        this.lastBannerTimestamp = System.currentTimeMillis();
        appendFeed(">>> [!] " + msg);
    }

    @Override
    public void onTimerTick(int secondsRemaining) {
    }

    @Override
    public void onAuctionPaused(boolean isPaused) {
        appendFeed(isPaused ? "--- || AUCTION PAUSED ---" : "--- >> AUCTION RESUMED ---");
    }

    @Override
    public void onAuctionCompleted() {
        this.lastBannerEvent = "AUCTION COMPLETED! All players processed.";
        this.lastBannerTimestamp = System.currentTimeMillis();
        appendFeed("=== AUCTION COMPLETED! ===");
    }

    @Override
    public void onRuleViolation(String message) {
        appendFeed("  [!] RULE NOTICE: " + message);
    }

    // --- Helpers ---

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().set("Access-Control-Allow-Private-Network", "true");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseFormBody(HttpExchange ex) throws IOException {
        String body = new String(readAllBytes(ex.getRequestBody()), StandardCharsets.UTF_8);
        return parseQuery(body);
    }

    private static Map<String, String> parseQuery(String raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyMap();
        Map<String, String> map = new HashMap<>();
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int n;
        while ((n = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "");
    }

    private String buildWebPortalHtml() {
        return "<!DOCTYPE html>\n"
            + "<html lang='en'>\n"
            + "<head>\n"
            + "<meta charset='UTF-8'>\n"
            + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>\n"
            + "<title>CricBid Live Auction Arena — Web Broadcast Portal</title>\n"
            + "<link href='https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@500&family=Poppins:wght@500;600;700&family=Rajdhani:wght@700&display=swap' rel='stylesheet'>\n"
            + "<style>\n"
            + "*{box-sizing:border-box;margin:0;padding:0;}\n"
            + "body{font-family:'Poppins',sans-serif;background:#0b132b url('/bg.png') center/cover fixed no-repeat;color:#f1f5f9;min-height:100vh;}\n"
            + ".overlay{background:rgba(9,15,34,0.82);min-height:100vh;display:flex;flex-direction:column;}\n"
            + ".topbar{background:#0f172a;border-bottom:2px solid #f59e0b;padding:10px 18px;display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;}\n"
            + ".brand{font-family:'Rajdhani',sans-serif;font-size:24px;color:#fbbf24;letter-spacing:1px;}\n"
            + ".status-pill{background:#14532d;color:#fff;padding:5px 12px;border-radius:6px;font-size:12px;font-weight:600;}\n"
            + "#loginView{flex:1;display:flex;align-items:center;justify-content:center;padding:20px;}\n"
            + ".login-card{background:rgba(15,23,42,0.92);border:2px solid #f59e0b;border-radius:12px;max-width:480px;width:100%;padding:28px;box-shadow:0 20px 50px rgba(0,0,0,0.6);}\n"
            + ".login-card h1{font-family:'Rajdhani',sans-serif;font-size:32px;text-align:center;color:#fff;margin-bottom:4px;}\n"
            + ".login-card .sub{text-align:center;color:#fbbf24;font-size:12px;margin-bottom:20px;}\n"
            + ".field{margin-bottom:14px;}\n"
            + ".field label{display:block;font-size:12px;color:#cbd5e1;margin-bottom:5px;}\n"
            + ".field input{width:100%;padding:10px 12px;background:#1e293b;border:1px solid #475569;color:#fff;border-radius:6px;font-family:'Poppins',sans-serif;font-size:14px;}\n"
            + ".quick-row{display:flex;gap:6px;flex-wrap:wrap;margin:12px 0;}\n"
            + ".qbtn{flex:1;padding:7px 8px;border:none;border-radius:6px;font-family:'Poppins',sans-serif;font-size:11px;font-weight:600;cursor:pointer;color:#fff;}\n"
            + ".btn-gold{width:100%;padding:12px;background:#f59e0b;color:#0f172a;border:none;border-radius:8px;font-family:'Rajdhani',sans-serif;font-size:20px;font-weight:700;cursor:pointer;margin-top:8px;}\n"
            + ".btn-gold:hover{background:#fbbf24;}\n"
            + "#arenaView{display:none;flex-direction:column;gap:10px;padding:12px 16px;max-width:1400px;width:100%;margin:0 auto;}\n"
            + ".strip{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:8px;}\n"
            + ".tcard{background:#1e293b;border:1px solid #334155;border-radius:8px;padding:8px 10px;}\n"
            + ".tcard.lead{background:#1e3a8a;border:2px solid #fbbf24;}\n"
            + ".tcard.you{border-color:#38bdf8;}\n"
            + ".tcard .tname{font-size:13px;font-weight:700;color:#fff;}\n"
            + ".tcard.lead .tname{color:#fbbf24;}\n"
            + ".tcard .tpurse{font-size:11px;color:#94a3b8;margin-top:2px;}\n"
            + ".grid{display:grid;grid-template-columns:1.15fr 0.85fr;gap:14px;}\n"
            + "@media(max-width:900px){.grid{grid-template-columns:1fr;}}\n"
            + ".pcard{background:linear-gradient(135deg,#0f172a,#1e3a8a);border:2px solid #f59e0b;border-radius:12px;padding:16px;display:flex;justify-content:space-between;gap:14px;align-items:center;}\n"
            + ".badges{display:flex;gap:8px;align-items:center;margin-bottom:8px;flex-wrap:wrap;}\n"
            + ".b-role{background:#f59e0b;color:#0f172a;padding:3px 9px;border-radius:4px;font-size:11px;font-weight:700;}\n"
            + ".b-os{background:#1e293b;color:#38bdf8;padding:3px 9px;border-radius:4px;font-size:11px;font-weight:700;}\n"
            + ".b-base{color:#fbbf24;font-size:12px;font-weight:700;}\n"
            + ".pname{font-family:'Rajdhani',sans-serif;font-size:34px;color:#fff;line-height:1.1;}\n"
            + ".psummary{font-size:12px;color:#cbd5e1;margin:6px 0 12px 0;}\n"
            + ".sbar{display:flex;align-items:center;gap:8px;margin-bottom:6px;font-size:11px;}\n"
            + ".sbar-lbl{width:115px;color:#94a3b8;font-weight:600;}\n"
            + ".sbar-track{flex:1;height:10px;background:#1e293b;border-radius:6px;overflow:hidden;}\n"
            + ".sbar-fill1{height:100%;background:linear-gradient(90deg,#38bdf8,#22c55e);}\n"
            + ".sbar-fill2{height:100%;background:linear-gradient(90deg,#f59e0b,#fbbf24);}\n"
            + ".sbar-fill3{height:100%;background:linear-gradient(90deg,#22c55e,#4ade80);}\n"
            + ".sbar-val{width:130px;font-weight:700;color:#38bdf8;}\n"
            + ".timer-ring{width:96px;height:96px;border-radius:50%;border:7px solid #22c55e;display:flex;flex-direction:column;align-items:center;justify-content:center;flex-shrink:0;background:rgba(15,23,42,0.7);}\n"
            + ".timer-ring .sec{font-family:'Rajdhani',sans-serif;font-size:28px;color:#fff;line-height:1;}\n"
            + ".timer-ring .lbl{font-size:9px;color:#94a3b8;}\n"
            + ".bid-box{background:rgba(17,28,53,0.94);border:1px solid #334155;border-radius:10px;padding:16px;margin-top:12px;}\n"
            + ".bid-row{display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;}\n"
            + ".cur-price{font-family:'Rajdhani',sans-serif;font-size:34px;color:#fbbf24;}\n"
            + ".lead-team{font-size:16px;color:#38bdf8;font-weight:700;}\n"
            + ".controls{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-top:12px;}\n"
            + ".controls input{width:110px;padding:9px;background:#0f172a;border:1px solid #475569;color:#fff;border-radius:6px;font-size:15px;font-weight:700;}\n"
            + ".step-btn{padding:8px 11px;background:#1e293b;border:1px solid #38bdf8;color:#38bdf8;border-radius:6px;font-weight:700;cursor:pointer;}\n"
            + ".action-row{display:flex;gap:10px;margin-top:12px;}\n"
            + ".place-btn{flex:2;padding:12px;background:#f59e0b;color:#0f172a;border:none;border-radius:8px;font-family:'Rajdhani',sans-serif;font-size:20px;font-weight:700;cursor:pointer;}\n"
            + ".pass-btn{flex:1;padding:12px;background:#334155;color:#fff;border:none;border-radius:8px;font-weight:600;cursor:pointer;}\n"
            + ".panel-box{background:rgba(17,28,53,0.94);border:1px solid #334155;border-radius:10px;padding:12px;display:flex;flex-direction:column;gap:8px;}\n"
            + ".panel-title{color:#fbbf24;font-size:13px;font-weight:700;}\n"
            + "#feedArea{background:#0a1020;border:1px solid #1e293b;border-radius:6px;padding:8px;height:185px;overflow-y:auto;font-family:'JetBrains Mono',monospace;font-size:12px;color:#e2e8f0;white-space:pre-wrap;}\n"
            + ".banter-row{display:flex;gap:6px;flex-wrap:wrap;}\n"
            + ".banter-btn{background:#1e293b;border:1px solid #475569;color:#fbbf24;padding:4px 10px;border-radius:5px;font-size:11px;font-weight:600;cursor:pointer;}\n"
            + ".chat-row{display:flex;gap:6px;}\n"
            + ".chat-row input{flex:1;padding:7px 10px;background:#0f172a;border:1px solid #475569;color:#fff;border-radius:6px;}\n"
            + ".chat-row button{padding:7px 14px;background:#2563eb;color:#fff;border:none;border-radius:6px;font-weight:600;cursor:pointer;}\n"
            + "table{width:100%;border-collapse:collapse;font-size:12px;}\n"
            + "th{background:#1e293b;color:#fbbf24;text-align:left;padding:6px 8px;}\n"
            + "td{padding:6px 8px;border-bottom:1px solid #1e293b;color:#e2e8f0;}\n"
            + "#bannerToast{display:none;background:linear-gradient(90deg,#15803d,#166534);border:2px solid #fbbf24;color:#fff;padding:10px 16px;border-radius:8px;font-family:'Rajdhani',sans-serif;font-size:22px;text-align:center;}\n"
            + "</style>\n"
            + "</head>\n"
            + "<body>\n"
            + "<div class='overlay'>\n"
            + "  <div class='topbar'>\n"
            + "    <div class='brand'>* CRICBID LIVE AUCTION ARENA — JAVA WEB PORTAL</div>\n"
            + "    <div id='userBadge' class='status-pill'>Java HTTP Server Connected</div>\n"
            + "  </div>\n"
            + "  <div id='loginView'>\n"
            + "    <div class='login-card'>\n"
            + "      <div class='sub'>* IPL BROADCAST EDITION | MULTI-DEVICE JAVA WEB PORTAL *</div>\n"
            + "      <h1>ENTER AUCTION ARENA</h1>\n"
            + "      <div class='field'><label>Username</label><input id='uInput' value='owner1'></div>\n"
            + "      <div class='field'><label>Password</label><input id='pInput' type='password' value='owner123'></div>\n"
            + "      <div class='quick-row'>\n"
            + "        <button class='qbtn' style='background:#2563eb' onclick=\"fillRole('owner1','owner123')\">Thunder Hawks (owner1)</button>\n"
            + "        <button class='qbtn' style='background:#16a34a' onclick=\"fillRole('owner2','owner123')\">Iron Wolves (owner2)</button>\n"
            + "        <button class='qbtn' style='background:#7c3aed' onclick=\"fillRole('owner3','owner123')\">Royal Titans (owner3)</button>\n"
            + "        <button class='qbtn' style='background:#475569' onclick=\"fillRole('admin','admin123')\">Admin Host</button>\n"
            + "      </div>\n"
            + "      <div id='loginErr' style='color:#f87171;font-size:12px;margin-bottom:6px;'></div>\n"
            + "      <button class='btn-gold' onclick='doLogin()'>>> CONNECT & ENTER LIVE AUCTION <<</button>\n"
            + "    </div>\n"
            + "  </div>\n"
            + "  <div id='arenaView'>\n"
            + "    <div id='bannerToast'></div>\n"
            + "    <div id='teamStrip' class='strip'></div>\n"
            + "    <div class='grid'>\n"
            + "      <div>\n"
            + "        <div class='pcard'>\n"
            + "          <div style='flex:1'>\n"
            + "            <div class='badges'>\n"
            + "              <span id='pRole' class='b-role'>AWAITING ROUND</span>\n"
            + "              <span id='pOs' class='b-os'>DOMESTIC</span>\n"
            + "              <span id='pBase' class='b-base'>Base Price: -- Cr</span>\n"
            + "            </div>\n"
            + "            <div id='pName' class='pname'>Waiting for Auctioneer Host...</div>\n"
            + "            <div id='pSummary' class='psummary'>Host can start the auction from the Java Swing App or Admin Web Controls.</div>\n"
            + "            <div class='sbar'><span id='r1Lbl' class='sbar-lbl'>STRIKE RATE:</span><div class='sbar-track'><div id='r1Fill' class='sbar-fill1' style='width:0%'></div></div><span id='r1Val' class='sbar-val'>--</span></div>\n"
            + "            <div class='sbar'><span id='r2Lbl' class='sbar-lbl'>RUN RATE / AVG:</span><div class='sbar-track'><div id='r2Fill' class='sbar-fill2' style='width:0%'></div></div><span id='r2Val' class='sbar-val' style='color:#fbbf24'>--</span></div>\n"
            + "            <div class='sbar'><span class='sbar-lbl'>OVR RATING:</span><div class='sbar-track'><div id='r3Fill' class='sbar-fill3' style='width:0%'></div></div><span id='r3Val' class='sbar-val' style='color:#fff'>--</span></div>\n"
            + "          </div>\n"
            + "          <div id='timerRing' class='timer-ring'><div id='tSec' class='sec'>15s</div><div class='lbl'>TIMER</div></div>\n"
            + "        </div>\n"
            + "        <div class='bid-box'>\n"
            + "          <div class='bid-row'><span style='color:#94a3b8;font-size:12px;font-weight:700'>CURRENT HIGHEST BID:</span><span id='curPrice' class='cur-price'>0.00 Cr</span></div>\n"
            + "          <div class='bid-row'><span style='color:#94a3b8;font-size:12px;font-weight:700'>LEADING FRANCHISE:</span><span id='leadTeam' class='lead-team'>None</span></div>\n"
            + "          <div id='bidderControls'>\n"
            + "            <div class='controls'>\n"
            + "              <span style='font-size:12px;color:#cbd5e1'>Your Bid (Cr):</span>\n"
            + "              <input id='bidInput' type='number' step='0.25' value='1.00'>\n"
            + "              <button class='step-btn' onclick='addInc(0.25)'>+0.25</button>\n"
            + "              <button class='step-btn' onclick='addInc(0.50)'>+0.50</button>\n"
            + "              <button class='step-btn' onclick='addInc(1.00)'>+1.00</button>\n"
            + "            </div>\n"
            + "            <div id='bidErr' style='color:#f87171;font-size:12px;margin-top:6px'></div>\n"
            + "            <div class='action-row'>\n"
            + "              <button class='place-btn' onclick='placeBid()'>>> PLACE BID NOW</button>\n"
            + "              <button class='pass-btn' onclick='passRound()'>>> Pass Round</button>\n"
            + "            </div>\n"
            + "          </div>\n"
            + "          <div id='adminControls' style='display:none;margin-top:12px;gap:8px;flex-wrap:wrap;'>\n"
            + "            <button class='place-btn' style='font-size:16px' onclick=\"adminCmd('start')\">>> Continue / Resume</button>\n"
            + "            <button class='pass-btn' onclick=\"adminCmd('pause')\">|| Pause</button>\n"
            + "            <button class='pass-btn' style='background:#2563eb' onclick=\"adminCmd('next')\">>> Next Player</button>\n"
            + "            <button class='pass-btn' style='background:#b45309' onclick=\"adminCmd('reset')\">[RESET] Initial State</button>\n"
            + "          </div>\n"
            + "        </div>\n"
            + "      </div>\n"
            + "      <div style='display:flex;flex-direction:column;gap:12px;'>\n"
            + "        <div class='panel-box'>\n"
            + "          <div class='panel-title'>[LIVE] Live Bidding Feed & Multi-Laptop Chat</div>\n"
            + "          <div id='feedArea'></div>\n"
            + "          <div class='banter-row'>\n"
            + "            <button class='banter-btn' onclick=\"sendChat('Steal!')\">Steal!</button>\n"
            + "            <button class='banter-btn' onclick=\"sendChat('Too Pricey!')\">Too Pricey!</button>\n"
            + "            <button class='banter-btn' onclick=\"sendChat('All In!')\">All In!</button>\n"
            + "            <button class='banter-btn' onclick=\"sendChat('Great Buy!')\">Great Buy!</button>\n"
            + "          </div>\n"
            + "          <div class='chat-row'>\n"
            + "            <input id='chatInput' placeholder='Broadcast message to all laptops & Swing app...' onkeydown=\"if(event.key==='Enter')sendCustomChat()\">\n"
            + "            <button onclick='sendCustomChat()'>Send Chat</button>\n"
            + "          </div>\n"
            + "        </div>\n"
            + "        <div class='panel-box'>\n"
            + "          <div class='panel-title'>>> Upcoming Player Pool Queue</div>\n"
            + "          <div style='max-height:210px;overflow-y:auto;'>\n"
            + "            <table><thead><tr><th>Name</th><th>Role</th><th>Key Stats (SR / RPO / Econ)</th><th>Rating</th><th>Base</th></tr></thead><tbody id='poolBody'></tbody></table>\n"
            + "          </div>\n"
            + "        </div>\n"
            + "      </div>\n"
            + "    </div>\n"
            + "  </div>\n"
            + "</div>\n"
            + "<script>\n"
            + "let session = null;\n"
            + "let lastPlayerId = -1;\n"
            + "let lastServerPrice = -1;\n"
            + "function fillRole(u,p){document.getElementById('uInput').value=u;document.getElementById('pInput').value=p;}\n"
            + "async function doLogin(){\n"
            + "  const u=document.getElementById('uInput').value;\n"
            + "  const p=document.getElementById('pInput').value;\n"
            + "  const res=await fetch('/api/login',{method:'POST',body:new URLSearchParams({username:u,password:p})});\n"
            + "  const data=await res.json();\n"
            + "  if(!data.ok){document.getElementById('loginErr').textContent=data.error;return;}\n"
            + "  session=data;\n"
            + "  document.getElementById('loginView').style.display='none';\n"
            + "  document.getElementById('arenaView').style.display='flex';\n"
            + "  document.getElementById('userBadge').textContent=data.isAdmin ? '* AUCTIONEER HOST ('+data.username+')' : '* LIVE FRANCHISE: '+data.teamName+' ('+data.username+')';\n"
            + "  if(data.isAdmin){\n"
            + "    document.getElementById('bidderControls').style.display='none';\n"
            + "    document.getElementById('adminControls').style.display='flex';\n"
            + "  }\n"
            + "  pollState();\n"
            + "  setInterval(pollState, 600);\n"
            + "}\n"
            + "function addInc(v){\n"
            + "  const inp=document.getElementById('bidInput');\n"
            + "  inp.value=(parseFloat(inp.value||0)+v).toFixed(2);\n"
            + "}\n"
            + "async function placeBid(){\n"
            + "  if(!session||session.isAdmin)return;\n"
            + "  document.getElementById('bidErr').textContent='';\n"
            + "  const amt=document.getElementById('bidInput').value;\n"
            + "  const res=await fetch('/api/bid',{method:'POST',body:new URLSearchParams({teamId:session.teamId,amount:amt})});\n"
            + "  const data=await res.json();\n"
            + "  if(!data.ok){document.getElementById('bidErr').textContent=data.error;}\n"
            + "  pollState();\n"
            + "}\n"
            + "async function passRound(){\n"
            + "  if(!session)return;\n"
            + "  await fetch('/api/pass',{method:'POST',body:new URLSearchParams({teamName:session.teamName})});\n"
            + "  pollState();\n"
            + "}\n"
            + "async function sendChat(txt){\n"
            + "  if(!session||!txt)return;\n"
            + "  await fetch('/api/chat',{method:'POST',body:new URLSearchParams({sender:session.teamName,text:txt})});\n"
            + "  pollState();\n"
            + "}\n"
            + "function sendCustomChat(){\n"
            + "  const inp=document.getElementById('chatInput');\n"
            + "  if(inp.value.trim()){sendChat(inp.value.trim());inp.value='';}\n"
            + "}\n"
            + "async function adminCmd(c){\n"
            + "  await fetch('/api/admin',{method:'POST',body:new URLSearchParams({cmd:c})});\n"
            + "  pollState();\n"
            + "}\n"
            + "async function pollState(){\n"
            + "  try{\n"
            + "    const who = session && !session.isAdmin ? (session.teamName+' ('+session.username+')') : '';\n"
            + "    const res=await fetch('/api/state?who='+encodeURIComponent(who));\n"
            + "    const s=await res.json();\n"
            + "    const toast=document.getElementById('bannerToast');\n"
            + "    if(s.bannerEvent){toast.style.display='block';toast.textContent='* '+s.bannerEvent;}else{toast.style.display='none';}\n"
            + "    document.getElementById('curPrice').textContent=s.currentPrice.toFixed(2)+' Cr';\n"
            + "    document.getElementById('leadTeam').textContent=s.leadingTeamName;\n"
            + "    document.getElementById('tSec').textContent=s.paused?'PAUSE':(s.seconds+'s');\n"
            + "    const ring=document.getElementById('timerRing');\n"
            + "    ring.style.borderColor = s.seconds<=5 ? '#ef4444' : (s.seconds<=10 ? '#f59e0b' : '#22c55e');\n"
            + "    if(s.player){\n"
            + "      document.getElementById('pRole').textContent=s.player.role;\n"
            + "      document.getElementById('pOs').textContent=s.player.overseas?'OVERSEAS':'DOMESTIC';\n"
            + "      document.getElementById('pBase').textContent='Base Price: '+s.player.basePrice.toFixed(2)+' Cr';\n"
            + "      document.getElementById('pName').textContent=s.player.name;\n"
            + "      document.getElementById('pSummary').textContent=s.player.summary;\n"
            + "      document.getElementById('r1Lbl').textContent=s.player.row1Label+':';\n"
            + "      document.getElementById('r1Fill').style.width=s.player.row1Pct+'%';\n"
            + "      document.getElementById('r1Val').textContent=s.player.row1Val;\n"
            + "      document.getElementById('r2Lbl').textContent=s.player.row2Label+':';\n"
            + "      document.getElementById('r2Fill').style.width=s.player.row2Pct+'%';\n"
            + "      document.getElementById('r2Val').textContent=s.player.row2Val;\n"
            + "      document.getElementById('r3Fill').style.width=s.player.rating+'%';\n"
            + "      document.getElementById('r3Val').textContent=s.player.rating+' / 100';\n"
            + "      if(s.player.id!==lastPlayerId || s.currentPrice!==lastServerPrice){\n"
            + "        lastPlayerId=s.player.id; lastServerPrice=s.currentPrice;\n"
            + "        if(document.activeElement!==document.getElementById('bidInput')){\n"
            + "          document.getElementById('bidInput').value=s.nextBid.toFixed(2);\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "    const strip=document.getElementById('teamStrip');\n"
            + "    strip.innerHTML=s.teams.map(t=>{\n"
            + "      const isLead = t.id===s.leadingTeamId;\n"
            + "      const isYou = session && t.id===session.teamId;\n"
            + "      return `<div class='tcard ${isLead?'lead':''} ${isYou?'you':''}'><div class='tname'>${isLead?'* ':''}${t.name}${isYou?' [YOU]':''}</div><div class='tpurse'>${t.purse.toFixed(2)} Cr | ${t.squad}/25</div></div>`;\n"
            + "    }).join('');\n"
            + "    const feed=document.getElementById('feedArea');\n"
            + "    const newFeed=s.feed.join('\\n');\n"
            + "    if(feed.textContent!==newFeed){feed.textContent=newFeed;feed.scrollTop=feed.scrollHeight;}\n"
            + "    const pb=document.getElementById('poolBody');\n"
            + "    pb.innerHTML=s.upcoming.map(u=>`<tr><td><b>${u.name}</b></td><td>${u.role}</td><td style='color:#38bdf8'>${u.stats}</td><td>${u.rating}</td><td style='color:#fbbf24'>${u.base.toFixed(2)}</td></tr>`).join('');\n"
            + "  }catch(e){}\n"
            + "}\n"
            + "</script>\n"
            + "</body></html>";
    }
}

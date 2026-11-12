package com.cricbid.network;

import com.cricbid.exception.CricBidException;
import com.cricbid.model.Bid;
import com.cricbid.model.OwnerType;
import com.cricbid.model.Player;
import com.cricbid.model.Team;
import com.cricbid.observer.AuctionObserver;
import com.cricbid.service.AuctionService;
import com.cricbid.service.TeamService;

import javax.swing.SwingUtilities;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class AuctionServer implements AuctionObserver {

    private final int port;
    private final AuctionService auctionService;
    private final TeamService teamService;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    private volatile boolean active = false;
    private ServerSocket serverSocket;
    private Consumer<List<String>> clientListListener;
    private Consumer<String> activityLogListener;

    public AuctionServer(int port, AuctionService auctionService, TeamService teamService) {
        this.port = port;
        this.auctionService = auctionService;
        this.teamService = teamService;
    }

    public void setClientListListener(Consumer<List<String>> listener) {
        this.clientListListener = listener;
        notifyClientListChanged();
    }

    public void setActivityLogListener(Consumer<String> listener) {
        this.activityLogListener = listener;
    }

    public void start() throws IOException {
        if (active) return;

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new java.net.InetSocketAddress(port));
        active = true;

        auctionService.getAuctionSubject().addObserver(this);

        Thread acceptThread = new Thread(this::acceptLoop, "CricBid-SocketServer-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (active) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);
                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);
                Thread reader = new Thread(handler, "CricBid-ClientHandler-" + clientSocket.getInetAddress());
                reader.setDaemon(true);
                reader.start();
            } catch (IOException e) {
                if (active) {
                    System.err.println("[AuctionServer] Accept error: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        active = false;
        auctionService.getAuctionSubject().removeObserver(this);
        for (ClientHandler client : clients) {
            client.closeQuietly();
        }
        clients.clear();
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
    }

    public int getPort() {
        return port;
    }

    public List<String> getConnectedClientLabels() {
        List<String> labels = new ArrayList<>();
        for (ClientHandler c : clients) {
            labels.add(c.getDisplayLabel());
        }
        return labels;
    }

    public void broadcastAuctionReset(boolean quickMode) {
        broadcast(new NetworkMessage(MessageType.AUCTION_RESET).setQuickMode(quickMode));
    }

    private void broadcast(NetworkMessage msg) {
        for (ClientHandler client : clients) {
            client.send(msg);
        }
    }

    private void notifyClientListChanged() {
        if (clientListListener != null) {
            List<String> snapshot = getConnectedClientLabels();
            SwingUtilities.invokeLater(() -> clientListListener.accept(snapshot));
        }
    }

    private void logActivity(String message) {
        if (activityLogListener != null) {
            SwingUtilities.invokeLater(() -> activityLogListener.accept(message));
        }
    }

    // --- AuctionObserver callbacks on Host ---

    @Override
    public void onPlayerUpForAuction(Player player, double currentPrice, int roundDurationSec) {
        broadcast(new NetworkMessage(MessageType.PLAYER_UP)
                .setPlayer(player)
                .setAmount(currentPrice)
                .setRoundDurationSec(roundDurationSec)
                .setQuickMode(auctionService.isQuickMode())
                .setUpcomingPool(auctionService.getUpcomingPoolSnapshot()));
    }

    @Override
    public void onBidPlaced(Bid bid, Team team, Player player) {
        broadcast(new NetworkMessage(MessageType.BID_PLACED)
                .setBid(bid)
                .setTeam(team)
                .setPlayer(player)
                .setRoundDurationSec(auctionService.getRoundDurationSec()));
    }

    @Override
    public void onPlayerSold(Player player, Team team, double finalPrice) {
        broadcast(new NetworkMessage(MessageType.PLAYER_SOLD)
                .setPlayer(player)
                .setTeam(team)
                .setAmount(finalPrice));
    }

    @Override
    public void onPlayerUnsold(Player player) {
        broadcast(new NetworkMessage(MessageType.PLAYER_UNSOLD)
                .setPlayer(player));
    }

    @Override
    public void onTimerTick(int secondsRemaining) {
        broadcast(new NetworkMessage(MessageType.TIMER_TICK)
                .setSecondsRemaining(secondsRemaining));
    }

    @Override
    public void onAuctionPaused(boolean isPaused) {
        broadcast(new NetworkMessage(MessageType.AUCTION_PAUSED)
                .setPaused(isPaused));
    }

    @Override
    public void onAuctionCompleted() {
        broadcast(new NetworkMessage(MessageType.AUCTION_COMPLETED));
    }

    @Override
    public void onRuleViolation(String message) {
        broadcast(new NetworkMessage(MessageType.RULE_VIOLATION)
                .setText(message));
    }

    // --- Per-Client Worker ---

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final Object writeLock = new Object();
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String username = "Participant";
        private String teamName = "Connecting...";
        private Integer teamId;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        String getDisplayLabel() {
            return teamName + " (" + username + ")";
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                while (active && !socket.isClosed()) {
                    Object obj = in.readObject();
                    if (obj instanceof NetworkMessage) {
                        handleClientMessage((NetworkMessage) obj);
                    }
                }
            } catch (EOFException ignored) {
                // Client disconnected normally
            } catch (Exception e) {
                if (active) {
                    System.err.println("[AuctionServer] Client disconnected (" + getDisplayLabel() + "): " + e.getMessage());
                }
            } finally {
                clients.remove(this);
                closeQuietly();
                notifyClientListChanged();
                logActivity("[NETWORK] Disconnected: " + getDisplayLabel());
            }
        }

        private void handleClientMessage(NetworkMessage msg) {
            switch (msg.getType()) {
                case REGISTER_CLIENT:
                    this.username = msg.getUsername() != null ? msg.getUsername() : "owner";
                    this.teamId = msg.getTeamId();
                    if (this.teamId != null) {
                        try {
                            teamService.updateOwnerType(this.teamId, OwnerType.HUMAN);
                            Team t = teamService.getTeamById(this.teamId);
                            if (t != null) {
                                this.teamName = t.getName();
                            }
                        } catch (SQLException ignored) {}
                    } else if (msg.getText() != null) {
                        this.teamName = msg.getText();
                    }
                    notifyClientListChanged();
                    logActivity("[NETWORK] Laptop joined: " + getDisplayLabel() + " from " + socket.getInetAddress().getHostAddress());
                    sendSyncState();
                    break;

                case PLACE_BID:
                    handleRemoteBid(msg);
                    break;

                case PASS_PLAYER:
                    String passer = msg.getText() != null ? msg.getText() : teamName;
                    String passLine = "  [PASS] " + passer + " passed on current player.";
                    logActivity(passLine);
                    broadcast(new NetworkMessage(MessageType.LOG_BROADCAST).setText(passLine));
                    break;

                default:
                    break;
            }
        }

        private void handleRemoteBid(NetworkMessage msg) {
            Integer bidTeamId = msg.getTeamId() != null ? msg.getTeamId() : this.teamId;
            if (bidTeamId == null) {
                send(new NetworkMessage(MessageType.BID_REJECTED).setText("No team associated with this session."));
                return;
            }
            try {
                Team biddingTeam = teamService.getTeamById(bidTeamId);
                if (biddingTeam == null) {
                    send(new NetworkMessage(MessageType.BID_REJECTED).setText("Team ID " + bidTeamId + " not found."));
                    return;
                }
                biddingTeam.setOwnerType(OwnerType.HUMAN);
                auctionService.placeHumanBid(biddingTeam, msg.getAmount());
            } catch (CricBidException ex) {
                send(new NetworkMessage(MessageType.BID_REJECTED).setText(ex.getMessage()));
            } catch (SQLException ex) {
                send(new NetworkMessage(MessageType.BID_REJECTED).setText("Database error: " + ex.getMessage()));
            }
        }

        private void sendSyncState() {
            NetworkMessage sync = new NetworkMessage(MessageType.SYNC_STATE)
                    .setRunning(auctionService.isRunning())
                    .setPaused(auctionService.isPaused())
                    .setQuickMode(auctionService.isQuickMode())
                    .setPlayer(auctionService.getCurrentPlayer())
                    .setAmount(auctionService.getCurrentPrice())
                    .setBid(auctionService.getCurrentHighestBid())
                    .setTeam(auctionService.getCurrentLeadingTeam())
                    .setRoundHasBids(auctionService.isRoundHasBids())
                    .setRoundDurationSec(auctionService.getRoundDurationSec())
                    .setSecondsRemaining(auctionService.getSecondsRemaining())
                    .setUpcomingPool(auctionService.getUpcomingPoolSnapshot());
            send(sync);
        }

        void send(NetworkMessage msg) {
            synchronized (writeLock) {
                if (out == null || socket.isClosed()) return;
                try {
                    out.reset();
                    out.writeObject(msg);
                    out.flush();
                } catch (IOException e) {
                    closeQuietly();
                }
            }
        }

        void closeQuietly() {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}

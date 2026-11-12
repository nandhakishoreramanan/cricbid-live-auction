package com.cricbid.network;

import com.cricbid.model.Player;
import com.cricbid.observer.AuctionSubject;
import com.cricbid.service.RuleValidator;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AuctionClient {

    private final String host;
    private final int port;
    private final String username;
    private final Integer teamId;
    private final String teamName;
    private final AuctionSubject localSubject;

    private final Object writeLock = new Object();
    private volatile boolean running = false;
    private volatile boolean connected = false;
    private Socket socket;
    private ObjectOutputStream out;

    private BiConsumer<Boolean, String> connectionStatusListener;
    private Consumer<String> bidRejectedListener;
    private Consumer<List<Player>> upcomingPoolListener;
    private Consumer<NetworkMessage> syncStateListener;
    private Consumer<Boolean> auctionResetListener;
    private Consumer<String> logBroadcastListener;

    public AuctionClient(String host, int port, String username, Integer teamId, String teamName,
            AuctionSubject localSubject) {
        this.host = (host == null || host.trim().isEmpty()) ? "localhost" : host.trim();
        this.port = port;
        this.username = username;
        this.teamId = teamId;
        this.teamName = teamName;
        this.localSubject = localSubject;
    }

    public void setConnectionStatusListener(BiConsumer<Boolean, String> listener) {
        this.connectionStatusListener = listener;
    }

    public void setBidRejectedListener(Consumer<String> listener) {
        this.bidRejectedListener = listener;
    }

    public void setUpcomingPoolListener(Consumer<List<Player>> listener) {
        this.upcomingPoolListener = listener;
    }

    public void setSyncStateListener(Consumer<NetworkMessage> listener) {
        this.syncStateListener = listener;
    }

    public void setAuctionResetListener(Consumer<Boolean> listener) {
        this.auctionResetListener = listener;
    }

    public void setLogBroadcastListener(Consumer<String> listener) {
        this.logBroadcastListener = listener;
    }

    public void start() {
        if (running)
            return;
        running = true;
        Thread clientThread = new Thread(this::connectionLoop, "CricBid-SocketClient");
        clientThread.setDaemon(true);
        clientThread.start();
    }

    private void connectionLoop() {
        while (running) {
            try {
                notifyStatus(false, String.format("Connecting to Host %s:%d...", host, port));
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), 3500);
                s.setTcpNoDelay(true);

                synchronized (writeLock) {
                    this.socket = s;
                    this.out = new ObjectOutputStream(s.getOutputStream());
                    this.out.flush();
                }

                ObjectInputStream in = new ObjectInputStream(s.getInputStream());
                this.connected = true;
                notifyStatus(true, String.format("Connected to Host (%s:%d)", host, port));

                send(new NetworkMessage(MessageType.REGISTER_CLIENT)
                        .setUsername(username)
                        .setTeamId(teamId)
                        .setText(teamName));

                while (running && !s.isClosed()) {
                    Object obj = in.readObject();
                    if (obj instanceof NetworkMessage) {
                        dispatchMessage((NetworkMessage) obj);
                    }
                }
            } catch (Exception e) {
                this.connected = false;
                if (running) {
                    notifyStatus(false,
                            String.format("Waiting for Auctioneer Host at %s:%d (retrying)...", host, port));
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                closeSocketQuietly();
            }
        }
    }

    private void dispatchMessage(NetworkMessage msg) {
        switch (msg.getType()) {
            case SYNC_STATE:
                RuleValidator.setQuickModeActive(msg.isQuickMode());
                if (syncStateListener != null) {
                    SwingUtilities.invokeLater(() -> syncStateListener.accept(msg));
                }
                break;

            case AUCTION_RESET:
                RuleValidator.setQuickModeActive(msg.isQuickMode());
                if (auctionResetListener != null) {
                    SwingUtilities.invokeLater(() -> auctionResetListener.accept(msg.isQuickMode()));
                }
                break;

            case PLAYER_UP:
                RuleValidator.setQuickModeActive(msg.isQuickMode());
                if (upcomingPoolListener != null && msg.getUpcomingPool() != null) {
                    SwingUtilities.invokeLater(() -> upcomingPoolListener.accept(msg.getUpcomingPool()));
                }
                if (msg.getPlayer() != null) {
                    localSubject.notifyPlayerUpForAuction(msg.getPlayer(), msg.getAmount(), msg.getRoundDurationSec());
                }
                break;

            case BID_PLACED:
                if (msg.getBid() != null && msg.getTeam() != null && msg.getPlayer() != null) {
                    localSubject.notifyBidPlaced(msg.getBid(), msg.getTeam(), msg.getPlayer());
                }
                break;

            case PLAYER_SOLD:
                if (msg.getPlayer() != null && msg.getTeam() != null) {
                    localSubject.notifyPlayerSold(msg.getPlayer(), msg.getTeam(), msg.getAmount());
                }
                break;

            case PLAYER_UNSOLD:
                if (msg.getPlayer() != null) {
                    localSubject.notifyPlayerUnsold(msg.getPlayer());
                }
                break;

            case TIMER_TICK:
                localSubject.notifyTimerTick(msg.getSecondsRemaining());
                break;

            case AUCTION_PAUSED:
                localSubject.notifyAuctionPaused(msg.isPaused());
                break;

            case AUCTION_COMPLETED:
                localSubject.notifyAuctionCompleted();
                break;

            case RULE_VIOLATION:
                localSubject.notifyRuleViolation(msg.getText());
                break;

            case BID_REJECTED:
                if (bidRejectedListener != null) {
                    SwingUtilities.invokeLater(() -> bidRejectedListener.accept(msg.getText()));
                }
                break;

            case LOG_BROADCAST:
                if (logBroadcastListener != null && msg.getText() != null) {
                    SwingUtilities.invokeLater(() -> logBroadcastListener.accept(msg.getText()));
                }
                break;

            default:
                break;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public void sendBid(int bidTeamId, double amount) throws IOException {
        if (!connected) {
            throw new IOException("Not connected to Auctioneer Host (" + host + ":" + port
                    + "). Make sure Laptop 1 (admin) is logged in.");
        }
        send(new NetworkMessage(MessageType.PLACE_BID)
                .setTeamId(bidTeamId)
                .setAmount(amount));
    }

    public void sendPass(String passerTeamName) {
        if (connected) {
            try {
                send(new NetworkMessage(MessageType.PASS_PLAYER).setText(passerTeamName));
            } catch (IOException ignored) {
            }
        }
    }

    private void send(NetworkMessage msg) throws IOException {
        synchronized (writeLock) {
            if (out == null || socket == null || socket.isClosed()) {
                throw new IOException("Socket connection is not open.");
            }
            out.reset();
            out.writeObject(msg);
            out.flush();
        }
    }

    private void notifyStatus(boolean isConn, String statusText) {
        if (connectionStatusListener != null) {
            SwingUtilities.invokeLater(() -> connectionStatusListener.accept(isConn, statusText));
        }
    }

    public void stop() {
        running = false;
        connected = false;
        closeSocketQuietly();
    }

    private void closeSocketQuietly() {
        synchronized (writeLock) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                socket = null;
                out = null;
            }
        }
    }
}

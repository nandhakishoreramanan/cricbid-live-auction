package com.cricbid.service;

import com.cricbid.dao.BidDAO;
import com.cricbid.dao.LogDAO;
import com.cricbid.dao.PlayerDAO;
import com.cricbid.dao.SquadDAO;
import com.cricbid.dao.TeamDAO;
import com.cricbid.dao.impl.BidDAOImpl;
import com.cricbid.dao.impl.LogDAOImpl;
import com.cricbid.dao.impl.PlayerDAOImpl;
import com.cricbid.dao.impl.SquadDAOImpl;
import com.cricbid.dao.impl.TeamDAOImpl;
import com.cricbid.db.DBConnection;
import com.cricbid.exception.CricBidException;
import com.cricbid.model.AuctionEventType;
import com.cricbid.model.Bid;
import com.cricbid.model.BotStrategyType;
import com.cricbid.model.OwnerType;
import com.cricbid.model.Player;
import com.cricbid.model.PlayerStatus;
import com.cricbid.model.SquadEntry;
import com.cricbid.model.Team;
import com.cricbid.observer.AuctionSubject;
import com.cricbid.strategy.AggressiveBot;
import com.cricbid.strategy.BalancedBot;
import com.cricbid.strategy.BiddingStrategy;
import com.cricbid.strategy.MoneyballBot;

import javax.swing.Timer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


public class AuctionService {

    private final PlayerDAO playerDAO;
    private final TeamDAO teamDAO;
    private final BidDAO bidDAO;
    private final SquadDAO squadDAO;
    private final LogDAO logDAO;
    private final RuleValidator ruleValidator;
    private final TeamService teamService;
    private final AuctionSubject auctionSubject;

    
    private final Map<BotStrategyType, BiddingStrategy> strategyMap = new EnumMap<>(BotStrategyType.class);

    
    private final ReentrantLock auctionLock = new ReentrantLock();
    private List<Player> auctionPool = new ArrayList<>();
    private int currentPoolIndex = 0;
    private Player currentPlayer;
    private double currentPrice;
    private Bid currentHighestBid;
    private Team currentLeadingTeam;
    private boolean roundHasBids = false;

    
    private int roundDurationSec = 15;
    private int secondsRemaining = 15;
    private boolean isPaused = false;
    private boolean isRunning = false;
    private boolean quickMode = false;
    private boolean botsEnabled = false;   
    private int botDelayMinMs = 2000;      
    private int botDelayMaxMs = 4000;

    
    private Timer swingCountdownTimer;
    private ScheduledExecutorService botExecutor;
    private final Random random = new Random();

    
    private volatile long lastBotBidTimeMs = 0;
    private static final long BOT_COOLDOWN_MS = 3000; 
    private volatile boolean botDecisionPending = false;

    
    private volatile boolean humanHasBidThisRound = false;

    public AuctionService(AuctionSubject auctionSubject) {
        this.playerDAO = new PlayerDAOImpl();
        this.teamDAO = new TeamDAOImpl();
        this.bidDAO = new BidDAOImpl();
        this.squadDAO = new SquadDAOImpl();
        this.logDAO = new LogDAOImpl();
        this.ruleValidator = new RuleValidator();
        this.teamService = new TeamService(teamDAO, squadDAO);
        this.auctionSubject = auctionSubject;

        
        strategyMap.put(BotStrategyType.AGGRESSIVE, new AggressiveBot());
        strategyMap.put(BotStrategyType.BALANCED, new BalancedBot());
        strategyMap.put(BotStrategyType.MONEYBALL, new MoneyballBot());

        initCountdownTimer();
    }

    private void initCountdownTimer() {
        swingCountdownTimer = new Timer(1000, e -> {
            auctionLock.lock();
            try {
                if (!isRunning || isPaused || currentPlayer == null) return;

                secondsRemaining--;
                auctionSubject.notifyTimerTick(secondsRemaining);

                if (secondsRemaining <= 0) {
                    swingCountdownTimer.stop();
                    handleRoundExpiry();
                }
            } finally {
                auctionLock.unlock();
            }
        });
    }

    
    public void startAuction(int durationSec, boolean quickMode) throws SQLException {
        auctionLock.lock();
        try {
            stopBotExecutor();
            this.roundDurationSec = Math.max(5, Math.min(30, durationSec));
            this.quickMode = quickMode;
            RuleValidator.setQuickModeActive(quickMode);
            this.isPaused = false;
            this.isRunning = true;

            
            this.auctionPool = playerDAO.findByStatus(PlayerStatus.AVAILABLE);
            if (quickMode && auctionPool.size() > 20) {
                
                this.auctionPool = new ArrayList<>(this.auctionPool.subList(0, 20));
            }
            this.currentPoolIndex = 0;

            startBotExecutor();
            presentNextPlayer();
        } finally {
            auctionLock.unlock();
        }
    }

    
    public void presentNextPlayer() {
        auctionLock.lock();
        try {
            if (currentPoolIndex >= auctionPool.size()) {
                
                isRunning = false;
                swingCountdownTimer.stop();
                stopBotExecutor();
                auctionSubject.notifyAuctionCompleted();
                return;
            }

            this.currentPlayer = auctionPool.get(currentPoolIndex++);
            this.currentPrice = currentPlayer.getBasePrice();
            this.currentHighestBid = null;
            this.currentLeadingTeam = null;
            
            this.lastBotBidTimeMs = 0;
            this.botDecisionPending = false;
            this.humanHasBidThisRound = false;  
            this.roundHasBids = false;
            this.secondsRemaining = roundDurationSec;

            auctionSubject.notifyPlayerUpForAuction(currentPlayer, currentPrice, roundDurationSec);
            swingCountdownTimer.restart();
            scheduleBotDecisions();
        } finally {
            auctionLock.unlock();
        }
    }

    
    public boolean placeBid(Team team, double amount) throws CricBidException, SQLException {
        return placeBid(team, amount, team != null && team.getOwnerType() == OwnerType.HUMAN);
    }

    public boolean placeHumanBid(Team team, double amount) throws CricBidException, SQLException {
        return placeBid(team, amount, true);
    }

    public boolean placeBid(Team team, double amount, boolean isHumanBidder) throws CricBidException, SQLException {
        auctionLock.lock();
        try {
            if (!isRunning || isPaused || currentPlayer == null) {
                throw new CricBidException(isPaused ? "Auction is currently paused." : "No active player up for auction.");
            }

            boolean isHuman = isHumanBidder || (team.getOwnerType() == OwnerType.HUMAN);
            if (!isHuman && humanHasBidThisRound) {
                return false;
            }

            teamService.loadSquadForTeam(team);
            Team refreshedTeam = teamDAO.findById(team.getTeamId());
            if (refreshedTeam != null) {
                team.setPurseRemaining(refreshedTeam.getPurseRemaining());
                if (isHumanBidder && refreshedTeam.getOwnerType() != OwnerType.HUMAN) {
                    team.setOwnerType(OwnerType.HUMAN);
                    teamDAO.updateOwnerType(team.getTeamId(), OwnerType.HUMAN);
                } else {
                    team.setOwnerType(refreshedTeam.getOwnerType());
                }
            }

            Integer leadingTeamId = (currentLeadingTeam != null) ? currentLeadingTeam.getTeamId() : null;

            
            ruleValidator.validateBid(team, currentPlayer, amount, leadingTeamId, currentPrice, roundHasBids);

            
            Bid bid = new Bid(currentPlayer.getPlayerId(), team.getTeamId(), amount);
            bid.setTeamName(team.getName());
            bid.setPlayerName(currentPlayer.getName());
            bidDAO.recordBid(bid);

            
            this.currentPrice = amount;
            this.currentHighestBid = bid;
            this.currentLeadingTeam = team;
            this.roundHasBids = true;

            
            if (isHuman) {
                this.humanHasBidThisRound = true;
            }

            
            this.secondsRemaining = roundDurationSec;

            
            logDAO.logEvent(AuctionEventType.BID, String.format("%s bid %.2f Cr for %s",
                    team.getName(), amount, currentPlayer.getName()));

            
            auctionSubject.notifyBidPlaced(bid, team, currentPlayer);

            
            if (!humanHasBidThisRound) {
                scheduleBotDecisions();
            }

            return true;
        } finally {
            auctionLock.unlock();
        }
    }

    
    private void handleRoundExpiry() {
        if (currentPlayer == null) return;

        try {
            if (roundHasBids && currentLeadingTeam != null && currentHighestBid != null) {
                executeSaleTransaction(currentLeadingTeam, currentPlayer, currentHighestBid.getAmount());
            } else {
                markPlayerUnsold(currentPlayer);
            }
        } catch (SQLException | CricBidException ex) {
            auctionSubject.notifyRuleViolation("Database error finalizing round: " + ex.getMessage());
        }
    }

    
    private void executeSaleTransaction(Team team, Player player, double soldPrice) throws SQLException, CricBidException {
        Connection conn = null;
        boolean originalAutoCommit = true;
        try {
            conn = DBConnection.getInstance().getConnection();
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false); 

            
            double newPurse = team.getPurseRemaining() - soldPrice;
            String deductPurseSql = "UPDATE teams SET purse_remaining = ? WHERE team_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deductPurseSql)) {
                stmt.setDouble(1, Math.round(newPurse * 100.0) / 100.0);
                stmt.setInt(2, team.getTeamId());
                stmt.executeUpdate();
            }

            
            String insertSquadSql = "INSERT INTO squads (team_id, player_id, sold_price) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertSquadSql)) {
                stmt.setInt(1, team.getTeamId());
                stmt.setInt(2, player.getPlayerId());
                stmt.setDouble(3, soldPrice);
                stmt.executeUpdate();
            }

            
            String updatePlayerSql = "UPDATE players SET status = 'SOLD' WHERE player_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updatePlayerSql)) {
                stmt.setInt(1, player.getPlayerId());
                stmt.executeUpdate();
            }

            
            String logSql = "INSERT INTO auction_log (event, details, log_time) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(logSql)) {
                stmt.setString(1, AuctionEventType.SOLD.name());
                String details = String.format("SOLD: %s bought by %s for %.2f Cr",
                        player.getName(), team.getName(), soldPrice);
                stmt.setString(2, details);
                stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                stmt.executeUpdate();
            }

            
            conn.commit();

            
            team.setPurseRemaining(newPurse);
            player.setStatus(PlayerStatus.SOLD);
            team.addSquadEntry(new SquadEntry(team.getTeamId(), player.getPlayerId(), soldPrice, player));

            
            auctionSubject.notifyPlayerSold(player, team, soldPrice);

        } catch (SQLException ex) {
            if (conn != null) {
                try {
                    conn.rollback(); 
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
            }
            throw ex;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    
    private void markPlayerUnsold(Player player) throws SQLException {
        playerDAO.updateStatus(player.getPlayerId(), PlayerStatus.UNSOLD);
        player.setStatus(PlayerStatus.UNSOLD);
        logDAO.logEvent(AuctionEventType.UNSOLD, String.format("UNSOLD: %s passed with no bids", player.getName()));
        auctionSubject.notifyPlayerUnsold(player);
    }

    

    private void startBotExecutor() {
        if (botExecutor == null || botExecutor.isShutdown()) {
            botExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "CricBid-Bot-Thread");
                t.setDaemon(true);
                return t;
            });
        }
    }

    private void stopBotExecutor() {
        if (botExecutor != null && !botExecutor.isShutdown()) {
            botExecutor.shutdownNow();
            botExecutor = null;
        }
    }

    
    private void scheduleBotDecisions() {
        if (!botsEnabled || botExecutor == null || botExecutor.isShutdown()) return;

        
        if (botDecisionPending) return;
        botDecisionPending = true;

        
        int delay = botDelayMinMs + random.nextInt(Math.max(1, botDelayMaxMs - botDelayMinMs));
        botExecutor.schedule(this::runBotBiddingCycle, delay, TimeUnit.MILLISECONDS);
    }

    private void runBotBiddingCycle() {
        
        botDecisionPending = false;

        auctionLock.lock();
        try {
            if (!isRunning || isPaused || !botsEnabled || currentPlayer == null) return;

            
            long now = System.currentTimeMillis();
            if (now - lastBotBidTimeMs < BOT_COOLDOWN_MS) return;

            
            List<Team> allTeams = teamDAO.findAll();
            List<Team> botTeams = new ArrayList<>();
            for (Team t : allTeams) {
                if (t.getOwnerType() == OwnerType.BOT) {
                    teamService.loadSquadForTeam(t);
                    botTeams.add(t);
                }
            }

            
            Collections.shuffle(botTeams);

            double nextAmount = RuleValidator.getNextValidBidAmount(currentPrice, roundHasBids);
            Integer leadingId = (currentLeadingTeam != null) ? currentLeadingTeam.getTeamId() : null;

            for (Team bot : botTeams) {
                if (leadingId != null && leadingId.equals(bot.getTeamId())) {
                    continue; 
                }

                BotStrategyType stratType = bot.getBotStrategy();
                if (stratType == null) stratType = BotStrategyType.BALANCED;
                BiddingStrategy strategy = strategyMap.getOrDefault(stratType, strategyMap.get(BotStrategyType.BALANCED));

                if (strategy.shouldBid(bot, currentPlayer, currentPrice, nextAmount)) {
                    try {
                        lastBotBidTimeMs = System.currentTimeMillis(); 
                        placeBid(bot, nextAmount);
                        return; 
                    } catch (CricBidException ignored) {
                        
                    }
                }
            }
        } catch (Exception ex) {
            
        } finally {
            auctionLock.unlock();
        }
    }

    
    public double suggestBidForHuman(Team humanTeam) {
        return suggestBidForPlayer(humanTeam, currentPlayer);
    }

    public double suggestBidForPlayer(Team humanTeam, Player targetPlayer) {
        if (humanTeam == null || targetPlayer == null) return 0.0;
        BiddingStrategy strategy = strategyMap.get(BotStrategyType.BALANCED);
        return Math.round(strategy.calculateMaxBid(humanTeam, targetPlayer) * 100.0) / 100.0;
    }

    public boolean isQuickMode() {
        return quickMode;
    }

    public List<Player> getUpcomingPoolSnapshot() {
        auctionLock.lock();
        try {
            if (currentPoolIndex >= auctionPool.size()) {
                return Collections.emptyList();
            }
            return new ArrayList<>(auctionPool.subList(currentPoolIndex, auctionPool.size()));
        } finally {
            auctionLock.unlock();
        }
    }

    

    public void pauseAuction() {
        auctionLock.lock();
        try {
            this.isPaused = true;
            if (swingCountdownTimer != null) swingCountdownTimer.stop();
            auctionSubject.notifyAuctionPaused(true);
        } finally {
            auctionLock.unlock();
        }
    }

    public void resumeAuction() {
        auctionLock.lock();
        try {
            this.isPaused = false;
            if (swingCountdownTimer != null && isRunning && currentPlayer != null) {
                swingCountdownTimer.start();
            }
            auctionSubject.notifyAuctionPaused(false);
            scheduleBotDecisions();
        } finally {
            auctionLock.unlock();
        }
    }

    public void shutdown() {
        stopBotExecutor();
        if (swingCountdownTimer != null) {
            swingCountdownTimer.stop();
        }
    }

    

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public Bid getCurrentHighestBid() {
        return currentHighestBid;
    }

    public Team getCurrentLeadingTeam() {
        return currentLeadingTeam;
    }

    public boolean isRoundHasBids() {
        return roundHasBids;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public int getRoundDurationSec() {
        return roundDurationSec;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public boolean isBotsEnabled() {
        return botsEnabled;
    }

    public void setBotsEnabled(boolean botsEnabled) {
        this.botsEnabled = botsEnabled;
    }

    public void setBotSpeed(int delayMin, int delayMax) {
        this.botDelayMinMs = delayMin;
        this.botDelayMaxMs = delayMax;
    }

    public List<Player> getAuctionPool() {
        return Collections.unmodifiableList(auctionPool);
    }

    public int getCurrentPoolIndex() {
        return currentPoolIndex;
    }

    public RuleValidator getRuleValidator() {
        return ruleValidator;
    }

    public TeamService getTeamService() {
        return teamService;
    }

    public AuctionSubject getAuctionSubject() {
        return auctionSubject;
    }
}

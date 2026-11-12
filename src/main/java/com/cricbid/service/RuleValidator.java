package com.cricbid.service;

import com.cricbid.exception.BudgetExceededException;
import com.cricbid.exception.InvalidBidException;
import com.cricbid.exception.OverseasLimitException;
import com.cricbid.exception.ReserveViolationException;
import com.cricbid.exception.SquadFullException;
import com.cricbid.model.Player;
import com.cricbid.model.PlayerRole;
import com.cricbid.model.Team;


public class RuleValidator {

    public static final double STARTING_PURSE = 100.00;
    public static final int MAX_SQUAD_SIZE = 25;
    public static final int MIN_SQUAD_SIZE = 18;
    public static final int QUICK_MIN_SQUAD_SIZE = 8;
    public static final int MAX_OVERSEAS_PLAYERS = 8;
    public static final int MIN_BOWLERS = 5;
    public static final int QUICK_MIN_BOWLERS = 2;
    public static final int MIN_WICKETKEEPERS = 1;
    public static final double MIN_SLOT_RESERVE_PRICE = 0.20; 

    private static volatile boolean quickModeActive = false;

    public static void setQuickModeActive(boolean quickMode) {
        quickModeActive = quickMode;
    }

    public static boolean isQuickModeActive() {
        return quickModeActive;
    }

    public static int getActiveMinSquadSize() {
        return quickModeActive ? QUICK_MIN_SQUAD_SIZE : MIN_SQUAD_SIZE;
    }

    public static int getActiveMinBowlers() {
        return quickModeActive ? QUICK_MIN_BOWLERS : MIN_BOWLERS;
    }

    
    public static double getNextValidBidAmount(double currentPrice, boolean hasExistingBids) {
        if (!hasExistingBids) {
            
            return Math.round(currentPrice * 100.0) / 100.0;
        }
        double increment = (currentPrice < 1.00) ? 0.20 : 0.25;
        return Math.round((currentPrice + increment) * 100.0) / 100.0;
    }

    
    public void validateBid(Team team, Player player, double proposedAmount,
                            Integer currentLeadingTeamId, double currentPrice, boolean hasExistingBids)
            throws SquadFullException, OverseasLimitException, InvalidBidException,
                   BudgetExceededException, ReserveViolationException {

        if (team == null) {
            throw new InvalidBidException("Bidding team cannot be null.");
        }
        if (player == null) {
            throw new InvalidBidException("Target player cannot be null.");
        }

        
        if (currentLeadingTeamId != null && currentLeadingTeamId.equals(team.getTeamId())) {
            throw new InvalidBidException(String.format("Team '%s' already holds the highest bid.", team.getName()));
        }

        
        if (team.getSquadSize() >= MAX_SQUAD_SIZE) {
            throw new SquadFullException(String.format(
                    "Team '%s' has reached the maximum squad limit of %d players.",
                    team.getName(), MAX_SQUAD_SIZE));
        }

        
        if (player.isOverseas() && team.getOverseasCount() >= MAX_OVERSEAS_PLAYERS) {
            throw new OverseasLimitException(String.format(
                    "Team '%s' has reached the maximum limit of %d overseas players.",
                    team.getName(), MAX_OVERSEAS_PLAYERS));
        }

        
        double minRequiredBid = getNextValidBidAmount(currentPrice, hasExistingBids);
        if (proposedAmount < minRequiredBid - 0.001) {
            throw new InvalidBidException(String.format(
                    "Bid of %.2f Cr is below the minimum required bid of %.2f Cr.",
                    proposedAmount, minRequiredBid));
        }

        
        if (proposedAmount > team.getPurseRemaining()) {
            throw new BudgetExceededException(String.format(
                    "Team '%s' has only %.2f Cr remaining, cannot bid %.2f Cr.",
                    team.getName(), team.getPurseRemaining(), proposedAmount));
        }

        
        
        
        int activeMinSquad = getActiveMinSquadSize();
        int activeMinBowlers = getActiveMinBowlers();
        int currentSquadSize = team.getSquadSize();
        int squadSizeAfterWinning = currentSquadSize + 1;
        int remainingSlotsNeededToMin = Math.max(0, activeMinSquad - squadSizeAfterWinning);
        double requiredReserveFunds = remainingSlotsNeededToMin * MIN_SLOT_RESERVE_PRICE;
        double remainingPurseAfterBid = team.getPurseRemaining() - proposedAmount;

        if (remainingPurseAfterBid < requiredReserveFunds - 0.001) {
            throw new ReserveViolationException(String.format(
                    "Reserve Rule Violation: Team '%s' needs %.2f Cr in reserve for %d remaining mandatory slots (min squad %d), but would only have %.2f Cr left after bidding %.2f Cr.",
                    team.getName(), requiredReserveFunds, remainingSlotsNeededToMin,
                    activeMinSquad, remainingPurseAfterBid, proposedAmount));
        }
    }

    
    public String getRosterComplianceIssues(Team team) {
        int activeMinSquad = getActiveMinSquadSize();
        int activeMinBowlers = getActiveMinBowlers();
        StringBuilder sb = new StringBuilder();
        if (team.getSquadSize() < activeMinSquad) {
            sb.append(String.format("- Squad size (%d) is below minimum of %d.\n",
                    team.getSquadSize(), activeMinSquad));
        }
        if (team.getBowlerCount() < activeMinBowlers) {
            sb.append(String.format("- Bowlers (%d) is below minimum of %d.\n",
                    team.getBowlerCount(), activeMinBowlers));
        }
        if (team.getWicketKeeperCount() < MIN_WICKETKEEPERS) {
            sb.append(String.format("- Wicketkeepers (%d) is below minimum of %d.\n",
                    team.getWicketKeeperCount(), MIN_WICKETKEEPERS));
        }
        if (team.getOverseasCount() > MAX_OVERSEAS_PLAYERS) {
            sb.append(String.format("- Overseas players (%d) exceeds maximum of %d.\n",
                    team.getOverseasCount(), MAX_OVERSEAS_PLAYERS));
        }
        return sb.toString();
    }
}

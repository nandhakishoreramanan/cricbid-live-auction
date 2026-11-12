# Member 2 Study & Code Explanation Guide: Service Packages, Custom Exceptions & Concurrency Libraries

## Review 2 Rubric Coverage (What Sir Grades You On!)
* **Packages (5 Marks):** `com.cricbid.service`, `com.cricbid.exception`, `com.cricbid.strategy`
* **Interfaces (5 Marks):** `BiddingStrategy` interface (`AggressiveBot`, `BalancedBot`, `MoneyballBot`) + `Set` / `List` / `Map` collection interfaces
* **Java Libraries (5 Marks):** `java.util.concurrent.locks.ReentrantLock`, `java.util.concurrent.ScheduledExecutorService`, `javax.swing.Timer`, `java.util.EnumMap`
* **Exception Handling (5 Marks):** Full custom checked exception hierarchy (`CricBidException` + 6 subclasses), `try-finally` lock release, and `try-catch-finally` JDBC `rollback()`

---

## 1. Your Updated 2-Minute Speech (Rubric-Aligned)
> Continuing from Member 1, I’ll cover our `service`, `strategy`, and `exception` packages, highlighting how we use Java concurrency libraries, interfaces, and custom exception handling.
>
> In `com.cricbid.service`, `AuctionService` manages the player pool, highest bid, and countdown timer using **Java libraries** like `javax.swing.Timer`, `java.util.EnumMap`, and `java.util.concurrent.locks.ReentrantLock`. Whenever a bid arrives, execution enters a critical section guarded by `auctionLock`, with `unlock()` inside a `finally` block so a deadlock can never occur. For **interfaces**, our `com.cricbid.strategy` package defines the `BiddingStrategy` interface implemented by `AggressiveBot`, `BalancedBot`, and `MoneyballBot`.
>
> Inside the lock, `RuleValidator` enforces **custom exception handling** through our `com.cricbid.exception` package. Extending our checked base class `CricBidException`, it throws specific exceptions—`InvalidBidException`, `SquadFullException`, `OverseasLimitException`, `BudgetExceededException`, and `ReserveViolationException`—whenever any IPL rule is broken. When a round ends, `executeSaleTransaction()` and `TradeService.executeTrade()` use `setAutoCommit(false)` inside `try-catch-finally`, calling `commit()` if all four SQL updates succeed or `rollback()` on `SQLException`.
>
> For Review 2, we defaulted bots to OFF, added `placeHumanBid()` so multiple human laptops can outbid each other, and made `ReserveViolationException` dynamic: requiring minimum 8 players in Quick Mode and 18 in Full Mode.

---

## 2. Exact Files & Line Numbers to Open on Your Laptop

Keep these 4 files open in tabs:
1. `src/main/java/com/cricbid/exception/CricBidException.java` & `ReserveViolationException.java`
2. `src/main/java/com/cricbid/service/RuleValidator.java`
3. `src/main/java/com/cricbid/service/AuctionService.java`
4. `src/main/java/com/cricbid/strategy/BiddingStrategy.java`

---

## 3. Code Blocks You MUST Know How to Explain (From Scratch)

### Part A: `com.cricbid.exception` Package & `RuleValidator.java` — Custom Exception Hierarchy (5 Marks Star!)

#### Snippet 1: Custom Checked Exception Hierarchy (`CricBidException.java` & Subclasses)
```java
package com.cricbid.exception;

public class CricBidException extends Exception { // Checked Exception!
    public CricBidException(String message) {
        super(message);
    }
}

// 6 Specific Subclasses in com.cricbid.exception:
// 1. AuthenticationException   4. OverseasLimitException
// 2. InvalidBidException       5. BudgetExceededException
// 3. SquadFullException        6. ReserveViolationException
```
* **Why `extends Exception` instead of `RuntimeException`?** Extending `Exception` makes these **Checked Exceptions**—the Java compiler forces callers (`AuctionService` and `AuctionServer`) to catch and handle every rule violation!

#### Snippet 2: Throwing Custom Exceptions in `RuleValidator.validateBid()` (Lines 54–117)
```java
public void validateBid(Team team, Player player, double proposedAmount,
                        Integer currentLeadingTeamId, double currentPrice, boolean hasExistingBids)
        throws SquadFullException, OverseasLimitException, InvalidBidException,
               BudgetExceededException, ReserveViolationException {

    if (currentLeadingTeamId != null && currentLeadingTeamId.equals(team.getTeamId()))
        throw new InvalidBidException("Team already holds the highest bid.");

    if (team.getSquadSize() >= 25)
        throw new SquadFullException("Reached maximum squad limit of 25 players.");

    if (player.isOverseas() && team.getOverseasCount() >= 8)
        throw new OverseasLimitException("Reached maximum limit of 8 overseas players.");

    if (proposedAmount < minRequiredBid - 0.001)
        throw new InvalidBidException("Bid is below minimum required increment.");

    if (proposedAmount > team.getPurseRemaining())
        throw new BudgetExceededException("Insufficient purse remaining.");

    int activeMinSquad = getActiveMinSquadSize(); // 8 in Quick Mode, 18 in Full Mode (NEW in Review 2)
    int remainingSlotsNeededToMin = Math.max(0, activeMinSquad - (team.getSquadSize() + 1));
    double requiredReserveFunds = remainingSlotsNeededToMin * 0.20;
    if (team.getPurseRemaining() - proposedAmount < requiredReserveFunds - 0.001)
        throw new ReserveViolationException(...);
}
```

---

### Part B: `AuctionService.java` — `java.util.concurrent.locks.ReentrantLock` Library & `try-finally`

#### Snippet: Thread-Safe Critical Section (Lines 61, 193–262)
```java
private final ReentrantLock auctionLock = new ReentrantLock(); // java.util.concurrent.locks library

public boolean placeHumanBid(Team team, double amount) throws CricBidException, SQLException {
    return placeBid(team, amount, true); // NEW in Review 2: multi-human bidding
}

public boolean placeBid(Team team, double amount, boolean isHumanBidder) throws CricBidException, SQLException {
    auctionLock.lock();
    try {
        ruleValidator.validateBid(team, currentPlayer, amount, leadingTeamId, currentPrice, roundHasBids);
        bidDAO.recordBid(bid);
        this.currentPrice = amount;
        this.currentLeadingTeam = team;
        this.secondsRemaining = roundDurationSec;
        auctionSubject.notifyBidPlaced(bid, team, currentPlayer);
        return true;
    } finally {
        auctionLock.unlock(); // Exception-safe unlock!
    }
}
```
* **Java Library (`ReentrantLock`):** Prevents race conditions when Laptop 2 and Laptop 3 bid at the exact same millisecond on different threads.
* **Exception Handling (`try-finally`):** Even when `validateBid()` throws `BudgetExceededException`, `finally` **always** runs `auctionLock.unlock()`, so the server never freezes!

---

### Part C: `AuctionService.java` — Transactional Exception Handling (`commit` / `rollback`, Lines 280–352)
```java
conn = DBConnection.getInstance().getConnection();
conn.setAutoCommit(false);
try {
    // 1. Deduct purse  2. Insert squad  3. Mark SOLD  4. Log event
    conn.commit();
} catch (SQLException ex) {
    if (conn != null) {
        try {
            conn.rollback(); // Undoes partial SQL writes!
        } catch (SQLException rollbackEx) {
            ex.addSuppressed(rollbackEx); // Preserves both exceptions!
        }
    }
    throw ex;
} finally {
    conn.setAutoCommit(originalAutoCommit);
    conn.close();
}
```
* **Pro Detail (`ex.addSuppressed`):** Notice line 340 even uses Java's suppressed-exception API so the original `SQLException` is never lost during rollback!

---

### Part D: `BiddingStrategy.java` — Interface in `com.cricbid.strategy`
* `public interface BiddingStrategy { double decideBid(Team botTeam, Player player, double currentBid, boolean hasExistingBids); }`
* Stored in `EnumMap<BotStrategyType, BiddingStrategy>` (`java.util.EnumMap` library) and implemented by `AggressiveBot`, `BalancedBot`, and `MoneyballBot`.

---

## 4. Likely Viva Questions on Review 2 Rubric Topics

1. **Q: Explain the difference between `throws` and `throw` in your `RuleValidator` code.**
   * **A:** `throw new BudgetExceededException(...)` inside the method body actually creates and triggers the exception object; `throws BudgetExceededException` in the method signature declares to callers that they must catch or propagate this checked exception.
2. **Q: Why use `java.util.concurrent.locks.ReentrantLock` instead of nothing?**
   * **A:** Multiple client socket threads and the countdown timer thread access `currentPrice` and `secondsRemaining` concurrently. `ReentrantLock` guarantees mutual exclusion (thread safety), and `try-finally` guarantees exception-safe lock release.
3. **Q: Why did you use `EnumMap` in `AuctionService`?**
   * **A:** `java.util.EnumMap` is a specialized high-performance `Map` library backed internally by an array indexed by enum ordinals (`AGGRESSIVE`, `BALANCED`, `MONEYBALL`), making strategy lookups O(1) and memory-efficient.

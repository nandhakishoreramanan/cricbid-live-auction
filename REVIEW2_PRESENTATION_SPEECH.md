# CricBid Review 2 — Rubric-Aligned Speeches & Code Guide (15 / 15 Marks)

> [!IMPORTANT]
> **Review 2 Official Marks Split-Up (15 Marks Total):**
> 1. **Implementation of packages, interfaces — 5 Marks**
> 2. **Implementation of Java library, Exception handling — 5 Marks**
> 3. **Presentation — 5 Marks**
>
> Every single member's speech and walkthrough below now **explicitly speaks these exact 4 rubric keywords** (*packages*, *interfaces*, *Java libraries*, and *exception handling*) while showing how Review 1 was upgraded to multi-laptop Review 2.

> [!TIP]
> **Updated Beginner-Friendly PDFs & Markdown Guides (`member_guides/`):**
> - **Member 1:** [Member1_Data_Layer_Guide.pdf](file:///Users/nandy/python/LoginSystem/member_guides/Member1_Data_Layer_Guide.pdf) | [.md](file:///Users/nandy/python/LoginSystem/member_guides/Member1_Data_Layer_Guide.md)
> - **Member 2:** [Member2_Business_Engine_Guide.pdf](file:///Users/nandy/python/LoginSystem/member_guides/Member2_Business_Engine_Guide.pdf) | [.md](file:///Users/nandy/python/LoginSystem/member_guides/Member2_Business_Engine_Guide.md)
> - **Member 3:** [Member3_Networking_Guide.pdf](file:///Users/nandy/python/LoginSystem/member_guides/Member3_Networking_Guide.pdf) | [.md](file:///Users/nandy/python/LoginSystem/member_guides/Member3_Networking_Guide.md)
> - **Member 4:** [Member4_UI_And_Demo_Guide.pdf](file:///Users/nandy/python/LoginSystem/member_guides/Member4_UI_And_Demo_Guide.pdf) | [.md](file:///Users/nandy/python/LoginSystem/member_guides/Member4_UI_And_Demo_Guide.md)
> - **All 4 Combined PDF:** [All_4_Members_Complete_Guide.pdf](file:///Users/nandy/python/LoginSystem/member_guides/All_4_Members_Complete_Guide.pdf)

---

## Master Rubric Mapping Across All 4 Members

| Member | Packages (`com.cricbid.*`) | Interfaces | Java Standard Libraries | Exception Handling |
| :--- | :--- | :--- | :--- | :--- |
| **Member 1** | `db`, `model`, `dao`, `dao.impl` | 6 DAO interfaces (`TeamDAO`..`LogDAO`) + `java.io.Serializable` *(NEW)* | `java.sql.*`, `java.util.Properties`, `java.io.FileInputStream`, `java.security.MessageDigest` | `try-with-resources` in DAOs, `SQLException`, `ClassNotFoundException`, custom `AuthenticationException` |
| **Member 2** | `service`, `strategy`, `exception` | `BiddingStrategy` (`AggressiveBot`, `BalancedBot`, `MoneyballBot`) | `java.util.concurrent.locks.ReentrantLock`, `ScheduledExecutorService`, `javax.swing.Timer`, `java.util.EnumMap` | Custom checked hierarchy (`CricBidException` + 6 subclasses), `try-finally` unlock, JDBC `commit()`/`rollback()` |
| **Member 3** | `network` *(BRAND-NEW)*, `observer` | `AuctionObserver`, `java.lang.Runnable`, `Serializable` | `java.net.*` (`ServerSocket`, `Socket`), `java.io.*` (`ObjectOutputStream`), `CopyOnWriteArrayList`, `SwingUtilities` | Cross-network `CricBidException` catch (`BID_REJECTED`), `EOFException` disconnect cleanup, 2s retry `try-catch` |
| **Member 4** | `ui` (9 panels & dialogs) | `AuctionObserver`, `ActionListener`, `Consumer` / `BiConsumer` | `javax.swing.*`, `java.awt.CardLayout`, `Graphics2D`, `java.io.PrintWriter`, `Collectors`, `NetworkInterface` | UI `try-catch` converting `AuthenticationException`, `SQLException`, and port/socket `IOException` into `JOptionPane` alerts |

---

## Member 1: Data Packages, DAO Interfaces & Persistence

### Updated Speech (~2 Mins)
> Good afternoon, Sir. I’ll explain CricBid’s data packages, DAO interfaces, Java libraries, and exception handling, along with our Review 2 multi-laptop upgrades.
>
> Our project is organized into ten modular packages. My layer spans `com.cricbid.db`, `model`, `dao`, and `dao.impl`, enforcing a strict three-tier architecture over six MySQL tables: `teams`, `users`, `players`, `squads`, `bids`, and `auction_log`.
>
> For **interfaces**, we define six DAO contracts—such as `PlayerDAO`, `TeamDAO`, and `BidDAO`—implemented in `dao.impl` using `java.sql.PreparedStatement` to prevent SQL injection. Using **Java libraries**, `DBConnection` uses `java.util.Properties` and `java.io.FileInputStream` inside a thread-safe Double-Checked Locking Singleton, while `AuthService` uses `java.security.MessageDigest` for SHA-256 password hashing. When mapping rows, `PlayerFactory` instantiates `Batsman`, `Bowler`, `AllRounder`, or `WicketKeeper`, each overriding `calculateValue()`. For **exception handling**, every DAO uses `try-with-resources` to close connections safely, handles `SQLException`, and `AuthService` throws our custom `AuthenticationException` on invalid logins.
>
> For Review 2, we added `DBConnection.configureHost()` so client laptops connect to Laptop 1’s IP, added `updateOwnerType()` to the `TeamDAO` interface, created `lan_setup.sql` and `owner1` through `owner6` in `seed.sql`, and implemented the `java.io.Serializable` interface across all model classes for network transfer.

### Files to Open
- [`DBConnection.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/db/DBConnection.java#L13-L92) — Singleton + `Properties`/`FileInputStream` libraries + `configureHost()`
- [`TeamDAO.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/dao/TeamDAO.java) & [`TeamDAOImpl.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/dao/impl/TeamDAOImpl.java#L72-L81) — Interface vs `dao.impl` package + `try-with-resources`
- [`AuthService.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/service/AuthService.java#L27-L58) — `MessageDigest` SHA-256 + `AuthenticationException`
- [`Player.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/model/Player.java#L7) & [`PlayerFactory.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/model/PlayerFactory.java#L17-L28) — `implements Serializable` + OOP polymorphism

---

## Member 2: Service Packages, Custom Exceptions & Concurrency Libraries

### Updated Speech (~2 Mins)
> Continuing from Member 1, I’ll cover our `service`, `strategy`, and `exception` packages, highlighting how we use Java concurrency libraries, interfaces, and custom exception handling.
>
> In `com.cricbid.service`, `AuctionService` manages the player pool, highest bid, and countdown timer using **Java libraries** like `javax.swing.Timer`, `java.util.EnumMap`, and `java.util.concurrent.locks.ReentrantLock`. Whenever a bid arrives, execution enters a critical section guarded by `auctionLock`, with `unlock()` inside a `finally` block so a deadlock can never occur. For **interfaces**, our `com.cricbid.strategy` package defines the `BiddingStrategy` interface implemented by `AggressiveBot`, `BalancedBot`, and `MoneyballBot`.
>
> Inside the lock, `RuleValidator` enforces **custom exception handling** through our `com.cricbid.exception` package. Extending our checked base class `CricBidException`, it throws specific exceptions—`InvalidBidException`, `SquadFullException`, `OverseasLimitException`, `BudgetExceededException`, and `ReserveViolationException`—whenever any IPL rule is broken. When a round ends, `executeSaleTransaction()` and `TradeService.executeTrade()` use `setAutoCommit(false)` inside `try-catch-finally`, calling `commit()` if all four SQL updates succeed or `rollback()` on `SQLException`.
>
> For Review 2, we defaulted bots to OFF, added `placeHumanBid()` so multiple human laptops can outbid each other, and made `ReserveViolationException` dynamic: requiring minimum 8 players in Quick Mode and 18 in Full Mode.

### Files to Open
- [`CricBidException.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/exception/CricBidException.java) & [`RuleValidator.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/service/RuleValidator.java#L54-L117) — `com.cricbid.exception` package + all 6 rule checks + dynamic Reserve Rule
- [`AuctionService.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/service/AuctionService.java#L58-L352) — `ReentrantLock` + `try-finally` + ACID `commit()`/`rollback()`
- [`BiddingStrategy.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/strategy/BiddingStrategy.java) — Strategy interface + `EnumMap`

---

## Member 3: New `network` Package, Interfaces & Socket Libraries

### Updated Speech (~2 Mins)
> I’ll explain our brand-new `com.cricbid.network` package built for Review 2, and how it combines interfaces, Java networking libraries, and network exception handling for real-time multi-laptop play.
>
> In Review 1, live timers and `AuctionObserver` callbacks existed only in one laptop’s RAM. To synchronize three laptops without database lag, we created the `com.cricbid.network` package using **Java’s `java.net` and `java.io` libraries**—specifically `ServerSocket`, `Socket`, `ObjectOutputStream`, and `ObjectInputStream` on TCP port `5050`.
>
> For **interfaces**, `AuctionServer` implements our `AuctionObserver` interface, while each per-laptop `ClientHandler` implements `java.lang.Runnable` inside a thread-safe `java.util.concurrent.CopyOnWriteArrayList`, and `NetworkMessage` implements `Serializable`. Whenever a timer tick, bid, or sale occurs on Laptop 1, `AuctionServer` automatically broadcasts it to all connected laptops in milliseconds.
>
> For **exception handling**, if a remote laptop’s bid violates a rule, `handleRemoteBid()` catches `CricBidException` and `SQLException` and sends a targeted `BID_REJECTED` message back to only that client. On Laptops 2 and 3, `AuctionClient` catches `IOException` and `EOFException` inside a retry loop—automatically reconnecting every 2 seconds if the Host isn’t up yet, applying a `SYNC_STATE` snapshot on join, and dispatching UI updates safely via `SwingUtilities.invokeLater()`.

### Files to Open
- [`AuctionServer.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/network/AuctionServer.java#L25-L325) — `implements AuctionObserver`, `ClientHandler implements Runnable`, `CopyOnWriteArrayList`, `handleRemoteBid` exception routing, `out.reset()`
- [`NetworkMessage.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/network/NetworkMessage.java) & [`MessageType.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/network/MessageType.java) — `implements Serializable` protocol DTO
- [`AuctionClient.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/network/AuctionClient.java#L80-L202) — `Socket`, 2s retry `try-catch`, `SYNC_STATE`, EDT dispatch

---

## Member 4: `ui` Package, Swing/Graphics/IO Libraries & Live Demo

### Updated Speech (~2 Mins)
> Finally, I’ll explain our `com.cricbid.ui` package, the Swing, 2D graphics, and I/O libraries we used, UI-level exception handling, and our role-based multi-laptop upgrades.
>
> In `com.cricbid.ui`, `MainFrame` uses `java.awt.CardLayout` to manage nine screens. For **interfaces**, `LiveAuctionPanel` implements `AuctionObserver`, while panels communicate via `ActionListener` and `java.util.function.Consumer` callbacks. For **Java libraries**, `ReportsPanel` uses `java.awt.Graphics2D` to render custom spending bar charts and role pie charts, and `ReportService` uses `java.util.stream.Collectors` and `java.io.PrintWriter` with `JFileChooser` to export CSV reports. Across the UI, **exception handling** catches `AuthenticationException`, `CricBidException`, and `IOException`—such as port conflicts or network drops—to display clean `JOptionPane` alerts instead of crashing.
>
> For Review 2, first, we deferred all secondary panel DB queries until after login so remote laptops never query `localhost` at startup, and used `java.net.NetworkInterface` in `NetworkUtils` so `LoginPanel` auto-displays Laptop 1’s Hotspot IP.
>
> Second, we built strict role-based separation: Laptop 1 logs in as `admin` (Auctioneer Host) to configure franchises and control Start, Pause, and Next Player with no Place Bid button. Laptops 2 and 3 log in as `owner1` and `owner2` (Thunder Hawks and Iron Wolves), skipping Setup into `LiveAuctionPanel` with synced timers, `[You]` / `[Participant]` badges, and Host-only round advancement after `SoldPopupDialog`. Let’s now demonstrate the live auction across our three laptops.

### Files to Open
- [`MainFrame.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/ui/MainFrame.java#L286-L380) — `CardLayout`, deferred DB queries, Admin vs Participant routing, port `IOException` dialog
- [`LoginPanel.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/ui/LoginPanel.java#L180-L198) & [`NetworkUtils.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/network/NetworkUtils.java#L14-L48) — `NetworkInterface` library + login exception dialogs
- [`LiveAuctionPanel.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/ui/LiveAuctionPanel.java#L41-L604) — `implements AuctionObserver`, role buttons, `[You]`/`[Participant]` badge, Host-only round advance
- [`ReportsPanel.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/ui/ReportsPanel.java#L155-L276) & [`ReportService.java`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/service/ReportService.java#L70-L107) — `Graphics2D` charts + `Collectors` + `PrintWriter` CSV export

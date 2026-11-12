# CricBid: Live Cricket Player Auction System

**CricBid** is a complete, production-grade desktop application built with **Java 17 Swing** for an Advanced Object-Oriented Programming (AOOP) college project. It simulates a live cricket franchise player auction with real-time bidding, automated background AI bots, budget reserve rules, transactional sales, and trade desks.

---

## 1. Quick Start: How to Run

### Prerequisites
- **Java 17** (`java -version`)
- **Apache Maven 3.8+** (`mvn -version`)
- **MySQL 8+** running on `localhost:3306`

### Step 1: Initialize Database
Open your MySQL terminal or database client (e.g. MySQL Workbench, DBeaver) and run:
```bash
mysql -u root -p < schema.sql
mysql -u root -p < seed.sql
```
This creates the `cricbid` database schema and populates:
- 6 fictional teams (`Thunder Hawks`, `Iron Wolves`, `Royal Titans`, `Storm Riders`, `Neon Vipers`, `Desert Falcons`)
- 48 fictional cricket players with role-specific stats
- 2 pre-seeded users with SHA-256 hashed credentials:
  - **Admin**: `admin` / `admin123`
  - **Team Owner**: `owner1` / `owner123`

### Step 2: Configure Database Credentials
Edit `db.properties` (or `src/main/resources/db.properties`):
```properties
db.url=jdbc:mysql://localhost:3306/cricbid?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
db.user=root
db.password=YOUR_MYSQL_PASSWORD
db.driver=com.mysql.cj.jdbc.Driver
```

### Step 3: Run the Application (Single Laptop)
You can run it directly using Maven:
```bash
mvn compile exec:java
```

Or build and run the standalone executable fat JAR:
```bash
mvn clean package -DskipTests
java -jar target/cricbid-auction-system-1.0.0.jar
```

### Step 4: Multi-Laptop Setup (3 Laptops — 1 Host + 2 Bidders)
1. **Connect all 3 laptops to the same Mobile Hotspot / Wi-Fi** (a phone hotspot avoids college Wi-Fi client isolation).
2. **On Laptop 1 (Auctioneer Host + MySQL Server)**:
   - Run once to enable LAN DB access and seed all accounts:
     ```bash
     mysql -u root -p < lan_setup.sql
     mysql -u cricbid -pcricbid123 < schema.sql
     mysql -u cricbid -pcricbid123 < seed.sql
     ```
   - Launch `java -jar target/cricbid-auction-system-1.0.0.jar`.
   - Note the **Detected LAN IP** shown at the top of the Login screen (e.g. `192.168.x.x`).
   - Keep Host IP as `localhost`, log in as **`admin` / `admin123`**. This automatically starts the TCP Socket Server on port `5050`.
3. **On Laptop 2 & Laptop 3 (Participating Franchises)**:
   - Only **Java 11+** is required (no MySQL or Maven installation needed). Copy `target/cricbid-auction-system-1.0.0.jar` to both laptops and run:
     ```bash
     java -jar cricbid-auction-system-1.0.0.jar
     ```
   - Enter **Laptop 1's LAN IP** in **Host Laptop IP**:
     - **Laptop 2**: `owner1` / `owner123` (*Thunder Hawks*)
     - **Laptop 3**: `owner2` / `owner123` (*Iron Wolves*)
   - Both laptops automatically enter the **Live Auction Room** and sync with Laptop 1.
4. **Start the Auction**:
   - On Laptop 1 (`admin`), verify both laptops appear under **Connected Laptops (2)**, choose **Quick Auction (Min Squad 8)** or **Full Auction (Min Squad 18)** (AI Bots are OFF by default), and click **Start Live Multi-Laptop Auction**!

---

## 2. Swing Components Checklist

Every required Swing component is utilized for a real, functional purpose:

| Swing Component | Screen / Class | Real Functional Purpose in Application |
|---|---|---|
| `JTextField` | `LoginPanel`, `PlayerPoolPanel` | Username input field; dynamic player search box |
| `JPasswordField` | `LoginPanel` | Masked user password input |
| `JButton` | `LoginPanel`, `SetupPanel`, `LiveAuctionPanel`, `TradeWindowPanel`, `ReportsPanel`, `MainFrame` | Form submission, placing bids, passing, executing trades, CSV export, navigation |
| `JLabel` | All screens | Display current bid, leading bidder, countdown timer, stats, and rule status |
| `JCheckBox` | `LoginPanel`, `PlayerPoolPanel` | "Show password" toggle; "Overseas Only" filter |
| `JOptionPane` | All screens | Error dialogs, rule violation alerts, and confirmations |
| `JComboBox` | `SetupPanel`, `PlayerPoolPanel`, `BotControlPanel`, `TradeWindowPanel` | Team selection, status filters, bot strategy switching, trade team picks |
| `JRadioButton` & `ButtonGroup` | `SetupPanel`, `PlayerPoolPanel`, `MainFrame` | Auction mode (Full vs. Quick); Player role filters; View menu switches |
| `JSlider` | `SetupPanel`, `BotControlPanel` | Round timer length (5-30s); AI bot decision response delay (1.0-4.0s) |
| `JSpinner` | `SetupPanel`, `LiveAuctionPanel`, `TradeWindowPanel` | Starting purse adjustment; Human bid input; Trade cash adjustment component |
| `JSplitPane` | `LiveAuctionPanel`, `TeamsPanel`, `ReportsPanel` | Resizable split layout between bidding block, bid log, rosters, and charts |
| `JProgressBar` | `LiveAuctionPanel`, `TeamsPanel` | Visual round countdown bar; Team purse utilization indicator |
| `javax.swing.Timer` | `LiveAuctionPanel`, `SoldPopupDialog`, `AuctionService` | 1-second interval countdown timer; 3-second auto-close modal dialog |
| `JTextArea` | `LiveAuctionPanel` | Scrollable live audit log showing all real-time bidding events |
| `JScrollPane` | `LiveAuctionPanel`, `PlayerPoolPanel`, `TeamsPanel`, `TradeWindowPanel`, `ReportsPanel`, `AboutDialog` | Provides scrolling capability for tables, lists, text areas, and text panes |
| `JTable` | `LiveAuctionPanel`, `PlayerPoolPanel`, `TeamsPanel`, `ReportsPanel`, `FinalSummaryPanel` | Upcoming pool table, player database, team purchase ledger, top buys, and compliance summary |
| `TableCellRenderer` | `PlayerTableRenderer`, `FinalSummaryPanel` | Custom color-coding by role, auction status, and red highlighting for rule violations |
| `JPopupMenu` | `PlayerPoolPanel` | Right-click context menu on player table showing full scouting stats report |
| `JTabbedPane` | `TeamsPanel` | Multi-tabbed view with one tab per franchise displaying roster, quotas, and purchases |
| `JList` | `TeamsPanel`, `TradeWindowPanel` | Roster list view; selectable player swap lists in trade desk |
| `ListCellRenderer` | `SquadListCellRenderer` | Custom badge formatting for players in `JList` |
| `JToggleButton` | `BotControlPanel` | Pause / Resume toggle button for automated AI bot bidding |
| `JDialog` | `SoldPopupDialog`, `AboutDialog` | Auto-closing SOLD/UNSOLD notification modal; Rich text documentation dialog |
| `JPanel` (`paintComponent`) | `ReportsPanel` | Custom vector 2D graphics rendering Bar Chart (spending) & Pie Chart (role share) |
| `JFileChooser` | `ReportsPanel` | File browser dialog to select location and save auction results as CSV |
| `JMenuBar` | `MainFrame` | Top application menu bar with File, Auction, View, and Help menus |
| `JMenuItem` | `MainFrame` | Menu options for New Auction, Exit, Skip Player, About |
| `JCheckBoxMenuItem`| `MainFrame` | Menu toggle to enable/disable automated AI bots |
| `JRadioButtonMenuItem`| `MainFrame` | View menu items for switching between screens |
| `JSeparator` | `MainFrame`, `TradeWindowPanel` | Visual dividers separating menu sections and panel actions |
| `JToolBar` | `MainFrame` | Icon-less quick action text toolbar (Start, Pause, Next Player, Teams, Reports) |
| `JTextPane` | `AboutDialog` | Formatted HTML document explaining AOOP project concepts |
| `JToolTip` | `MainFrame`, `LiveAuctionPanel` | Hover tooltips explaining button actions and auto-suggest algorithms |
| `JColorChooser` | `MainFrame` | Custom theme accent color picker |
| `CardLayout` | `MainFrame` | Seamless screen switching across all 9 application panels |
| `BorderLayout` & `GridBagLayout` | Across all panels | Structured grid layouts and responsive border positionings |

---

## 3. OOP Concepts Mapping Table

| OOP Concept / Pattern | Class / File | Explanation & Implementation in CricBid |
|---|---|---|
| **Abstraction** | [`Player`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/model/Player.java) | Abstract base class declaring abstract methods `calculateValue()` and `describe()`. |
| **Inheritance** | [`Batsman`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/model/Batsman.java), [`Bowler`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/model/Bowler.java), [`AllRounder`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/model/AllRounder.java), [`WicketKeeper`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/model/WicketKeeper.java) | Subclasses inherit common attributes from `Player` and provide role-specific fields and logic. |
| **Polymorphism** | `calculateValue()` across `Player` subclasses | Subclasses implement distinct mathematical models; treated uniformly as `List<Player>`. |
| **Encapsulation** | `Player`, `Team`, `User`, `Bid` | Private instance variables with validated getters and setters (e.g. rating 1-100 check). |
| **Interfaces** | `BiddingStrategy`, `AuctionObserver`, `PlayerDAO`, etc. | Decouples caller logic from implementations. |
| **Strategy Pattern** | [`BiddingStrategy`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/strategy/BiddingStrategy.java), `AggressiveBot`, `BalancedBot`, `MoneyballBot` | Encapsulates bidding algorithms; swappable at runtime in `BotControlPanel`. |
| **Observer Pattern** | [`AuctionObserver`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/observer/AuctionObserver.java), [`AuctionSubject`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/observer/AuctionSubject.java) | Real-time event publisher notifying registered UI components on the EDT without polling. |
| **Singleton Pattern**| [`DBConnection`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/db/DBConnection.java) | Double-checked locking Singleton providing centralized JDBC connections from `db.properties`. |
| **DAO Pattern** | `PlayerDAO`, `TeamDAO`, `BidDAO`, `SquadDAO`, `UserDAO`, `LogDAO` | Completely abstracts database access; zero SQL queries exist in UI panels. |
| **Factory Pattern** | [`PlayerFactory`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/model/PlayerFactory.java) | Instantiates the correct `Player` subclass based on database role string. |
| **Custom Exceptions**| `BudgetExceededException`, `SquadFullException`, `OverseasLimitException`, `ReserveViolationException`, `InvalidBidException` | Checked domain exceptions enforcing strict auction constraints. |
| **JDBC Transactions**| `AuctionService.executeSaleTransaction()`, `TradeService.executeTrade()` | Multi-step database operations executed atomically with `setAutoCommit(false)`, `commit()`, and `rollback()`. |
| **Multithreading** | `AuctionService`, `AuctionServer`, `AuctionClient` | Daemon socket listener/client handler threads and executor services; EDT safety via `SwingUtilities.invokeLater`. |
| **Socket Networking** | [`AuctionServer`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/network/AuctionServer.java), [`AuctionClient`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/network/AuctionClient.java) | TCP `ServerSocket` & `Socket` broadcasting real-time timer ticks, bids, and round transitions across multiple laptops. |
| **Object Serialization** | [`NetworkMessage`](file:///Users/nandy/python/LoginSystem/src/main/java/com/cricbid/network/NetworkMessage.java), `Player`, `Team`, `Bid` | `Serializable` domain objects streamed over `ObjectOutputStream` / `ObjectInputStream`. |

---

## 4. Top 10 Viva Questions & Answers

1. **Q: Why did you use the Strategy Pattern for AI bots?**  
   *A:* It decouples the bidding decision algorithms (`AggressiveBot`, `BalancedBot`, `MoneyballBot`) from the auction engine. This allows teams to dynamically swap strategies at runtime from the UI without changing any core auction logic.

2. **Q: How is the Observer Pattern used to update the GUI?**  
   *A:* `AuctionSubject` maintains a list of `AuctionObserver` listeners (`LiveAuctionPanel`, etc.). Whenever a bid is placed, a player is sold, or the timer ticks, the subject dispatches events on the Swing Event Dispatch Thread (EDT) using `SwingUtilities.invokeLater()`. No UI panel ever polls the database.

3. **Q: How is thread safety ensured between AI bot background threads and Swing?**  
   *A:* Auction mutations (placing bids, deducting purse, updating timer) are synchronized via a `ReentrantLock` in `AuctionService`. All GUI modifications are strictly dispatched onto the EDT using `SwingUtilities.invokeLater()`.

4. **Q: How does the SOLD step maintain ACID compliance?**  
   *A:* In `AuctionService.executeSaleTransaction()`, `setAutoCommit(false)` is set on the JDBC connection. Purse deduction, squad entry insertion, player status update to 'SOLD', and auction audit logging all execute within one transaction. If any query fails, `conn.rollback()` restores the previous database state.

5. **Q: What is the Reserve Rule and how is it calculated?**  
   *A:* The Reserve Rule guarantees that a team can always afford to complete the mandatory minimum squad size of 18 players at the base reserve price of 0.20 crore per slot. If `(purseRemaining - bidAmount) < (18 - (currentSquadSize + 1)) * 0.20`, a `ReserveViolationException` is thrown.

6. **Q: How does the Factory Pattern simplify player object creation?**  
   *A:* `PlayerFactory.fromResultSet(rs)` encapsulates the logic of inspecting the database `role` column and creating the appropriate polymorphic subclass (`Batsman`, `Bowler`, `AllRounder`, `WicketKeeper`) without exposing constructor details to the DAO layer.

7. **Q: Why did you use CardLayout instead of opening multiple JFrames?**  
   *A:* `CardLayout` provides a professional Single-Document Interface (SDI) desktop experience, allowing smooth transitions between screens (Login, Setup, Live Auction, Pool, Teams, Trade, Reports, Summary) while maintaining shared session state in a single container.

8. **Q: How are custom exceptions handled in the service layer?**  
   *A:* `RuleValidator` validates bids against team purse, overseas limits, squad sizes, and self-bidding before executing any database mutation. If violated, specific checked exceptions (`BudgetExceededException`, `SquadFullException`, `OverseasLimitException`, `ReserveViolationException`) are thrown with clear, descriptive messages shown to the user in a `JOptionPane`.

9. **Q: How is polymorphism demonstrated in the player valuation model?**  
   *A:* The abstract method `calculateValue()` is implemented with distinct domain algorithms for each subclass: `Batsman` evaluates average and strike rate; `Bowler` evaluates wickets and economy; `AllRounder` balances dual contributions; `WicketKeeper` incorporates dismissals.

10. **Q: How does CricBid prevent SQL injection?**  
    *A:* All DAO implementations strictly utilize parameterized `PreparedStatement` with typed setters (`setInt`, `setString`, `setDouble`, `setTimestamp`) for every SQL query, with zero string concatenation of query parameters.

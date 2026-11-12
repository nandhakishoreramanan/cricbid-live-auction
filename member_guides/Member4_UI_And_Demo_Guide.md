# Member 4 Study & Code Explanation Guide: `ui` Package, Swing/Graphics/IO Libraries & Live Demo

## Review 2 Rubric Coverage (What Sir Grades You On!)
* **Packages (5 Marks):** `com.cricbid.ui` (9 panels & dialogs) integrated with `service`, `observer`, and `network`
* **Interfaces (5 Marks):** `LiveAuctionPanel implements AuctionObserver`, `ActionListener`, and `java.util.function` (`Consumer`, `BiConsumer`) callback interfaces
* **Java Libraries (5 Marks):** `javax.swing.*` (`CardLayout`, `JSpinner`, `JFileChooser`), `java.awt.Graphics2D` (2D charts), `java.io.PrintWriter` (CSV export), `java.util.stream.Collectors`, `java.net.NetworkInterface`
* **Exception Handling (5 Marks):** User-facing `try-catch` blocks converting `AuthenticationException`, `CricBidException`, `SQLException`, and `IOException` into clear `JOptionPane` dialogs

---

## 1. Your Updated 2-Minute Speech (Rubric-Aligned)
> Finally, I’ll explain our `com.cricbid.ui` package, the Swing, 2D graphics, and I/O libraries we used, UI-level exception handling, and our role-based multi-laptop upgrades.
>
> In `com.cricbid.ui`, `MainFrame` uses `java.awt.CardLayout` to manage nine screens. For **interfaces**, `LiveAuctionPanel` implements `AuctionObserver`, while panels communicate via `ActionListener` and `java.util.function.Consumer` callbacks. For **Java libraries**, `ReportsPanel` uses `java.awt.Graphics2D` to render custom spending bar charts and role pie charts, and `ReportService` uses `java.util.stream.Collectors` and `java.io.PrintWriter` with `JFileChooser` to export CSV reports. Across the UI, **exception handling** catches `AuthenticationException`, `CricBidException`, and `IOException`—such as port conflicts or network drops—to display clean `JOptionPane` alerts instead of crashing.
>
> For Review 2, first, we deferred all secondary panel DB queries until after login so remote laptops never query `localhost` at startup, and used `java.net.NetworkInterface` in `NetworkUtils` so `LoginPanel` auto-displays Laptop 1’s Hotspot IP.
>
> Second, we built strict role-based separation: Laptop 1 logs in as `admin` (Auctioneer Host) to configure franchises and control Start, Pause, and Next Player with no Place Bid button. Laptops 2 and 3 log in as `owner1` and `owner2` (Thunder Hawks and Iron Wolves), skipping Setup into `LiveAuctionPanel` with synced timers, `[You]` / `[Participant]` badges, and Host-only round advancement after `SoldPopupDialog`. Let’s now demonstrate the live auction across our three laptops.

---

## 2. Exact Files & Line Numbers to Open on Your Laptop

Keep these 5 files open in tabs:
1. `src/main/java/com/cricbid/ui/MainFrame.java`
2. `src/main/java/com/cricbid/ui/LoginPanel.java` & `network/NetworkUtils.java`
3. `src/main/java/com/cricbid/ui/LiveAuctionPanel.java`
4. `src/main/java/com/cricbid/ui/ReportsPanel.java` & `service/ReportService.java`

---

## 3. Code Blocks You MUST Know How to Explain (From Scratch)

### Part A: `MainFrame.java` — `com.cricbid.ui` Package, `CardLayout`, Role Separation & Port Exception Handling

#### Snippet 1: Deferred DB Queries & Role-Based Menus (Lines 286–324)
```java
private void setToolbarAndMenusForSession(boolean loggedIn, boolean isAdmin) {
    mainToolBar.setVisible(loggedIn);
    newAuctionItem.setEnabled(loggedIn && isAdmin);
    auctionMenu.setEnabled(loggedIn && isAdmin);
    startBtn.setVisible(isAdmin);
    pauseBtn.setVisible(isAdmin);
    nextBtn.setVisible(isAdmin);
}

public void showCard(String cardName) {
    if (currentUser != null) { // Deferred until AFTER login configures Host IP!
        switch (cardName) {
            case CARD_POOL:    playerPoolPanel.refreshPlayers(); break;
            case CARD_TEAMS:   teamsPanel.refreshTeams();        break;
            case CARD_REPORTS: reportsPanel.refreshReports();    break;
            case CARD_SUMMARY: summaryPanel.refreshSummary();    break;
        }
    }
    cardLayout.show(cardsPanel, cardName); // java.awt.CardLayout library
}
```

#### Snippet 2: UI Exception Handling for Socket Startup (`onLoginSuccess`, Lines 334–379)
```java
if (isAdmin) {
    this.auctionServer = new AuctionServer(serverPort, auctionService, teamService);
    try {
        this.auctionServer.start();
    } catch (IOException ex) {
        JOptionPane.showMessageDialog(this,
            "Could not start Host Socket Server on TCP port " + serverPort + ":\n" + ex.getMessage(),
            "Host Server Port Busy", JOptionPane.WARNING_MESSAGE);
    }
    showCard(CARD_SETUP);
} else {
    this.auctionClient = new AuctionClient(hostIp, serverPort, ...);
    this.auctionClient.start();
    showCard(CARD_LIVE);
}
```
* **Rubric Point — UI Exception Handling:** If two Admin windows are opened on the same laptop and port `5050` is already busy, `catch (IOException ex)` catches the error cleanly and pops up a helpful `JOptionPane` message instead of crashing!

---

### Part B: `LoginPanel.java` & `NetworkUtils.java` — `java.net.NetworkInterface` Library & `AuthenticationException`
* **`NetworkUtils.getLocalLanIp()` (`NetworkUtils.java` Lines 14–48):** Uses `java.net.NetworkInterface` and `java.net.Inet4Address` inside `try-catch (SocketException)` to auto-detect Laptop 1's active Wi-Fi/Hotspot IP.
* **`LoginPanel.attemptLogin()` (`LoginPanel.java` Lines 180–196):** Uses functional interface `BiConsumer<User, String>` and catches both `AuthenticationException` ("Login Failed") and `SQLException` ("Cannot reach Host MySQL at `<IP>:3306`") with clear dialogs!

---

### Part C: `LiveAuctionPanel.java` — `implements AuctionObserver`, Socket Bid Exception Handling & Sync

```java
public class LiveAuctionPanel extends JPanel implements AuctionObserver { // Interface + Inheritance!
...
// 1. Role-based controls (Lines 113–116)
bidSpinner.setVisible(!isAdmin);
bidderBtnRow.setVisible(!isAdmin);
adminControlRow.setVisible(isAdmin);

// 2. Sending bids over network with IOException handling (Lines 450–457)
if (auctionClient != null) {
    try {
        auctionClient.sendBid(humanTeam.getTeamId(), bidAmount);
    } catch (IOException ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "Network Error", JOptionPane.WARNING_MESSAGE);
    }
    return;
}

// 3. [You] vs [Participant] badge (Lines 502–508)
if (humanTeam != null && team.getTeamId() == humanTeam.getTeamId()) return team.getName() + " [You]";

// 4. Only Host advances round after SoldPopupDialog (Lines 597–603)
if (auctionClient == null) {
    auctionService.presentNextPlayer();
}
```

---

### Part D: `ReportsPanel.java` & `ReportService.java` — `Graphics2D`, `Collectors` & `PrintWriter` Libraries
* **`java.awt.Graphics2D` (`ReportsPanel.java` Lines 199–276):** Custom `BarChartPanel` (`fillRoundRect`) and `PieChartPanel` (`fillArc`) render live analytics charts without external dependencies.
* **`java.io.PrintWriter` + `JFileChooser` (`ReportsPanel.java` Lines 155–182 & `ReportService.java` Lines 85–107):** Exports sold players to `.csv` inside `try-with-resources`, catching `IOException` and `SQLException` in the UI.
* **`java.util.stream.Collectors` (`ReportService.java` Line 80):** Uses Java Streams (`stream().filter(...).limit(limit).collect(Collectors.toList())`) to compute top value picks!

---

## 4. Likely Viva Questions on Review 2 Rubric Topics

1. **Q: Show me an interface and a Java library used inside `com.cricbid.ui`.**
   * **A:** `LiveAuctionPanel implements AuctionObserver` (our custom observer interface) and buttons use `java.awt.event.ActionListener`. For libraries, we use `javax.swing.*`, `java.awt.CardLayout` for multi-screen navigation, `java.awt.Graphics2D` for bar/pie charts, and `java.io.PrintWriter` for CSV file export.
2. **Q: How do you handle exceptions on the UI layer when Laptop 2 cannot reach Laptop 1's MySQL database?**
   * **A:** In `LoginPanel.java` (lines 193–198), we catch `SQLException` and display a `JOptionPane` telling the user the exact Host IP (`<host>:3306`) that could not be reached and reminding them to check Wi-Fi/Hotspot!
3. **Q: Why does `LiveAuctionPanel` use `extends JPanel implements AuctionObserver`?**
   * **A:** Java supports single class inheritance (`extends JPanel` makes it a Swing screen) and multiple interface implementation (`implements AuctionObserver` lets it receive live auction events).

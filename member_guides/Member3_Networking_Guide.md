# Member 3 Study & Code Explanation Guide: New `network` Package, Interfaces & Socket Libraries

## Review 2 Rubric Coverage (What Sir Grades You On!)
* **Packages (5 Marks):** Brand-new `com.cricbid.network` package added in Review 2 + `com.cricbid.observer`
* **Interfaces (5 Marks):** `AuctionServer implements AuctionObserver` (custom interface), `ClientHandler implements Runnable` (thread interface), `NetworkMessage implements Serializable`
* **Java Libraries (5 Marks):** `java.net.*` (`ServerSocket`, `Socket`, `InetSocketAddress`), `java.io.*` (`ObjectOutputStream`, `ObjectInputStream`), `java.util.concurrent.CopyOnWriteArrayList`, `javax.swing.SwingUtilities`
* **Exception Handling (5 Marks):** Cross-network `CricBidException` catching (`BID_REJECTED`), `EOFException` / `IOException` disconnect cleanup in `finally`, and `InterruptedException` / 2-second auto-reconnect `try-catch`

---

## 1. Your Updated 2-Minute Speech (Rubric-Aligned)
> I’ll explain our brand-new `com.cricbid.network` package built for Review 2, and how it combines interfaces, Java networking libraries, and network exception handling for real-time multi-laptop play.
>
> In Review 1, live timers and `AuctionObserver` callbacks existed only in one laptop’s RAM. To synchronize three laptops without database lag, we created the `com.cricbid.network` package using **Java’s `java.net` and `java.io` libraries**—specifically `ServerSocket`, `Socket`, `ObjectOutputStream`, and `ObjectInputStream` on TCP port `5050`.
>
> For **interfaces**, `AuctionServer` implements our `AuctionObserver` interface, while each per-laptop `ClientHandler` implements `java.lang.Runnable` inside a thread-safe `java.util.concurrent.CopyOnWriteArrayList`, and `NetworkMessage` implements `Serializable`. Whenever a timer tick, bid, or sale occurs on Laptop 1, `AuctionServer` automatically broadcasts it to all connected laptops in milliseconds.
>
> For **exception handling**, if a remote laptop’s bid violates a rule, `handleRemoteBid()` catches `CricBidException` and `SQLException` and sends a targeted `BID_REJECTED` message back to only that client. On Laptops 2 and 3, `AuctionClient` catches `IOException` and `EOFException` inside a retry loop—automatically reconnecting every 2 seconds if the Host isn’t up yet, applying a `SYNC_STATE` snapshot on join, and dispatching UI updates safely via `SwingUtilities.invokeLater()`.

---

## 2. Exact Files & Line Numbers to Open on Your Laptop

Keep these 4 files open in tabs:
1. `src/main/java/com/cricbid/network/AuctionServer.java`
2. `src/main/java/com/cricbid/network/MessageType.java` & `NetworkMessage.java`
3. `src/main/java/com/cricbid/network/AuctionClient.java`
4. `src/main/java/com/cricbid/observer/AuctionObserver.java` & `AuctionSubject.java`

---

## 3. Code Blocks You MUST Know How to Explain (From Scratch)

### Part A: `AuctionServer.java` — New Package, 2 Interfaces (`AuctionObserver`, `Runnable`) & `java.net` / `CopyOnWriteArrayList`

#### Snippet 1: Package, Interfaces & Libraries (Lines 1–31, 52–83, 194)
```java
package com.cricbid.network; // Brand-new package created for Review 2!
...
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuctionServer implements AuctionObserver { // Implements custom interface!
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>(); // Thread-safe library list
    ...
    serverSocket = new ServerSocket();
    serverSocket.bind(new java.net.InetSocketAddress(port)); // TCP Port 5050
    auctionService.getAuctionSubject().addObserver(this);
    ...
    private class ClientHandler implements Runnable { // Implements Runnable thread interface!
```
* **Rubric Point — Packages & Interfaces:**
  1. `com.cricbid.network` cleanly isolates all socket code from UI and DAO code.
  2. `AuctionServer implements AuctionObserver` subscribes the network server to `AuctionService` so every auction event broadcasts automatically.
  3. `ClientHandler implements Runnable` gives every connected laptop its own background daemon thread.
* **Rubric Point — Java Libraries:**
  * `java.net.ServerSocket` & `Socket`: Reliable TCP connection on port `5050`.
  * `java.util.concurrent.CopyOnWriteArrayList`: Prevents `ConcurrentModificationException` if a laptop joins/leaves while `broadcast()` loops through clients.
  * `java.io.ObjectOutputStream` (lines 312–323): Uses `out.reset()` before `out.writeObject(msg)` so updated `Team` purses are never stale!

---

### Part B: `AuctionServer.java` — Network Exception Handling (`handleRemoteBid` & `ClientHandler.run`)

#### Snippet 2: Catching Custom Exceptions Across the Network (Lines 275–294)
```java
private void handleRemoteBid(NetworkMessage msg) {
    try {
        Team biddingTeam = teamService.getTeamById(bidTeamId);
        biddingTeam.setOwnerType(OwnerType.HUMAN);
        auctionService.placeHumanBid(biddingTeam, msg.getAmount());
    } catch (CricBidException ex) {
        send(new NetworkMessage(MessageType.BID_REJECTED).setText(ex.getMessage()));
    } catch (SQLException ex) {
        send(new NetworkMessage(MessageType.BID_REJECTED).setText("Database error: " + ex.getMessage()));
    }
}
```
* **How to explain to Sir:** When Laptop 2 sends an invalid bid (e.g. exceeding purse), `placeHumanBid()` throws `BudgetExceededException` (a subclass of `CricBidException`). `AuctionServer` catches it here without crashing the server, wraps the exact error message in `BID_REJECTED`, and sends it **only** to Laptop 2!

#### Snippet 3: Disconnect Cleanup with `EOFException`, `Exception` & `finally` (Lines 224–236)
```java
} catch (EOFException ignored) {
    // Client closed window normally — end of socket stream
} catch (Exception e) {
    if (active) System.err.println("[AuctionServer] Client disconnected: " + e.getMessage());
} finally {
    clients.remove(this);
    closeQuietly();
    notifyClientListChanged(); // Live laptop counter on SetupPanel updates immediately!
}
```

---

### Part C: `AuctionClient.java` — Auto-Reconnect Exception Handling & `SwingUtilities` Library (Lines 80–125)
```java
while (running) {
    try {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 3500);
        ...
        send(new NetworkMessage(MessageType.REGISTER_CLIENT)...);
        while (running && !s.isClosed()) {
            Object obj = in.readObject();
            if (obj instanceof NetworkMessage) dispatchMessage((NetworkMessage) obj);
        }
    } catch (Exception e) {
        this.connected = false;
        notifyStatus(false, "Waiting for Auctioneer Host (retrying)...");
        try {
            Thread.sleep(2000); // Retries every 2 seconds instead of crashing!
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
        }
    } finally {
        closeSocketQuietly();
    }
}
```
* **Why Sir will love this:** Instead of crashing with `ConnectException` if Laptop 2 opens before Laptop 1, `catch` updates the status banner and retries cleanly every 2 seconds, while `finally` closes broken sockets safely!

---

## 4. Likely Viva Questions on Review 2 Rubric Topics

1. **Q: Which Java libraries did you import to build `com.cricbid.network`?**
   * **A:** `java.net` (`ServerSocket`, `Socket`, `InetSocketAddress`, `NetworkInterface`), `java.io` (`ObjectOutputStream`, `ObjectInputStream`, `Serializable`, `EOFException`, `IOException`), `java.util.concurrent` (`CopyOnWriteArrayList`), `java.util.function` (`Consumer`, `BiConsumer`), and `javax.swing.SwingUtilities`.
2. **Q: Why do you catch `EOFException` separately in `ClientHandler`?**
   * **A:** When a participant closes their laptop window, `ObjectInputStream.readObject()` throws `java.io.EOFException` to signal end-of-stream. Catching it cleanly lets `finally` remove that laptop from `clients` and update the Host's connected-laptops count without printing scary stack traces.
3. **Q: How can one class (`AuctionServer` or `LiveAuctionPanel`) implement an interface (`AuctionObserver`)?**
   * **A:** `AuctionObserver` defines 8 callback methods (`onPlayerUpForAuction`, `onBidPlaced`, `onTimerTick`, etc.). Both `LiveAuctionPanel` (in `ui`) and `AuctionServer` (in `network`) implement `AuctionObserver` so `AuctionSubject` notifies both local UI and remote laptops through polymorphism!

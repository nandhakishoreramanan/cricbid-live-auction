# Member 1 Study & Code Explanation Guide: Packages, DAO Interfaces & Persistence Layer

## Review 2 Rubric Coverage (What Sir Grades You On!)
* **Packages (5 Marks):** `com.cricbid.db`, `com.cricbid.model`, `com.cricbid.dao`, `com.cricbid.dao.impl`
* **Interfaces (5 Marks):** 6 DAO interfaces (`TeamDAO`, `PlayerDAO`, `BidDAO`, `SquadDAO`, `UserDAO`, `LogDAO`) + `java.io.Serializable` on all models
* **Java Libraries (5 Marks):** `java.sql.*` (`DriverManager`, `PreparedStatement`), `java.util.Properties`, `java.io.FileInputStream`, `java.security.MessageDigest` (SHA-256)
* **Exception Handling (5 Marks):** `try-with-resources` auto-closing DB connections, `SQLException` & `ClassNotFoundException` handling, and custom `AuthenticationException`

---

## 1. Your Updated 2-Minute Speech (Rubric-Aligned)
> Good afternoon, Sir. I’ll explain CricBid’s data packages, DAO interfaces, Java libraries, and exception handling, along with our Review 2 multi-laptop upgrades.
>
> Our project is organized into ten modular packages. My layer spans `com.cricbid.db`, `model`, `dao`, and `dao.impl`, enforcing a strict three-tier architecture over six MySQL tables: `teams`, `users`, `players`, `squads`, `bids`, and `auction_log`.
>
> For **interfaces**, we define six DAO contracts—such as `PlayerDAO`, `TeamDAO`, and `BidDAO`—implemented in `dao.impl` using `java.sql.PreparedStatement` to prevent SQL injection. Using **Java libraries**, `DBConnection` uses `java.util.Properties` and `java.io.FileInputStream` inside a thread-safe Double-Checked Locking Singleton, while `AuthService` uses `java.security.MessageDigest` for SHA-256 password hashing. When mapping rows, `PlayerFactory` instantiates `Batsman`, `Bowler`, `AllRounder`, or `WicketKeeper`, each overriding `calculateValue()`. For **exception handling**, every DAO uses `try-with-resources` to close connections safely, handles `SQLException`, and `AuthService` throws our custom `AuthenticationException` on invalid logins.
>
> For Review 2, we added `DBConnection.configureHost()` so client laptops connect to Laptop 1’s IP, added `updateOwnerType()` to the `TeamDAO` interface, created `lan_setup.sql` and `owner1` through `owner6` in `seed.sql`, and implemented the `java.io.Serializable` interface across all model classes for network transfer.

---

## 2. Exact Files & Line Numbers to Open on Your Laptop

Keep these 5 files open in tabs:
1. `src/main/java/com/cricbid/db/DBConnection.java`
2. `src/main/java/com/cricbid/dao/TeamDAO.java` & `dao/impl/TeamDAOImpl.java`
3. `src/main/java/com/cricbid/service/AuthService.java`
4. `src/main/java/com/cricbid/model/Player.java` & `PlayerFactory.java`
5. `lan_setup.sql` & `seed.sql`

---

## 3. Code Blocks You MUST Know How to Explain (From Scratch)

### Part A: `DBConnection.java` — Package `com.cricbid.db`, `java.util.Properties` Library & Singleton

#### Snippet 1: Double-Checked Locking Singleton (Lines 13–34)
```java
package com.cricbid.db;
...
public class DBConnection {
    private static volatile DBConnection instance;
    private final Properties properties = new Properties(); // java.util.Properties library

    private DBConnection() {
        loadProperties();
    }

    public static DBConnection getInstance() {
        if (instance == null) {                     // 1st check (fast, no lock)
            synchronized (DBConnection.class) {     // Thread lock
                if (instance == null) {             // 2nd check (inside lock)
                    instance = new DBConnection();
                }
            }
        }
        return instance;
    }
```
* **Java Libraries here:** Uses `java.util.Properties` and `java.io.FileInputStream` (lines 48–54) with `try-with-resources` and `catch (IOException)` + `catch (ClassNotFoundException)` (lines 77–81) to load `db.properties` safely.
* **Why `private` constructor + `synchronized` + `volatile`?** Ensures only ONE `DBConnection` object ever exists across all threads (Singleton Pattern).

#### Snippet 2: Dynamic Host IP Configuration — NEW in Review 2 (Lines 84–91)
```java
public synchronized void configureHost(String host) {
    String cleanHost = (host == null || host.trim().isEmpty()) ? "localhost" : host.trim();
    this.activeHost = cleanHost;
    String newUrl = String.format(
            "jdbc:mysql://%s:3306/cricbid?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=4000",
            cleanHost);
    properties.setProperty("db.url", newUrl);
}
```
* **How to explain to Sir:** Rewrites `db.url` in `Properties` at runtime from `localhost` to Laptop 1's Hotspot IP so Laptops 2 and 3 connect over Wi-Fi.

---

### Part B: `TeamDAO.java` & `TeamDAOImpl.java` — Packages, Interfaces & `try-with-resources` Exception Handling

#### Snippet: Interface (`TeamDAO.java` Line 20) + Implementation (`TeamDAOImpl.java` Lines 72–81)
```java
// Package com.cricbid.dao — Interface contract
public interface TeamDAO {
    List<Team> findAll() throws SQLException;
    ...
    boolean updateOwnerType(int teamId, OwnerType ownerType) throws SQLException; // NEW in Review 2
}

// Package com.cricbid.dao.impl — Concrete implementation
public class TeamDAOImpl implements TeamDAO {
    @Override
    public boolean updateOwnerType(int teamId, OwnerType ownerType) throws SQLException {
        String sql = "UPDATE teams SET owner_type = ? WHERE team_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ownerType != null ? ownerType.name() : OwnerType.BOT.name());
            stmt.setInt(2, teamId);
            return stmt.executeUpdate() > 0;
        }
    }
}
```
* **Rubric Point — Packages & Interfaces:** Notice DAO interfaces live in `com.cricbid.dao` while SQL classes live in `com.cricbid.dao.impl` (`implements TeamDAO`). That decouples business logic from SQL.
* **Rubric Point — Java Library (`java.sql.PreparedStatement`):** Uses `?` placeholders (`setString`, `setInt`) to prevent SQL injection.
* **Rubric Point — Exception Handling (`try-with-resources` + `throws SQLException`):** Putting `Connection` and `PreparedStatement` inside `try (...)` automatically closes both resources even if an SQL error happens!

---

### Part C: `AuthService.java` — `java.security.MessageDigest` Library & `AuthenticationException` (Lines 27–58)
```java
MessageDigest md = MessageDigest.getInstance("SHA-256"); // java.security library
byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
...
public User login(String username, String password) throws AuthenticationException, SQLException {
    if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
        throw new AuthenticationException("Username and password cannot be empty.");
    }
    String hashed = hashPassword(password);
    User user = userDAO.authenticate(username.trim(), hashed);
    if (user == null) {
        throw new AuthenticationException("Invalid username or password.");
    }
    return user;
}
```
* **Java Library:** `java.security.MessageDigest` (`SHA-256`) + `java.nio.charset.StandardCharsets` hashes passwords into 64-char hex.
* **Exception Handling:** Catches `NoSuchAlgorithmException` and throws our custom checked `AuthenticationException` when fields are empty or credentials fail.

---

### Part D: `Player.java` & `PlayerFactory.java` — `Serializable` Interface & OOP Pillars
* **`public abstract class Player implements Serializable` (`Player.java` Line 7):**
  * **Built-in Java Interface (`java.io.Serializable` — NEW in Review 2):** Implemented across `Player`, `Team`, `Bid`, `SquadEntry`, and `User` so Java sockets can serialize objects into bytes across laptops.
  * **OOP Pillars + Factory:** Abstract `Player` + 4 subclasses (`Batsman`, `Bowler`, `AllRounder`, `WicketKeeper`) overriding `calculateValue()`, instantiated via `PlayerFactory.fromResultSet()`.

---

## 4. Likely Viva Questions on Review 2 Rubric Topics

1. **Q: Why did you separate `com.cricbid.dao` and `com.cricbid.dao.impl` into two packages with interfaces?**
   * **A:** Programming to an interface (`TeamDAO`) instead of a concrete class (`TeamDAOImpl`) achieves loose coupling and abstraction—services only depend on the interface contract, not the SQL details.
2. **Q: Why is `Serializable` called a marker interface in Java?**
   * **A:** Because `java.io.Serializable` has zero methods inside it—implementing it marks our model classes (`Player`, `Team`, `Bid`) as safe for `ObjectOutputStream` to convert into a byte stream, otherwise Java throws `NotSerializableException`.
3. **Q: How does `try-with-resources` in your DAOs improve exception handling?**
   * **A:** Because `Connection`, `PreparedStatement`, and `ResultSet` implement `AutoCloseable`, `try (...)` guarantees they are closed automatically even when `SQLException` is thrown, eliminating memory and connection leaks.

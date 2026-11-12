package com.cricbid.db;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;


public class DBConnection {
    private static volatile DBConnection instance;
    private final Properties properties = new Properties();
    private boolean initialized = false;
    private String lastErrorMessage = "";

    
    private DBConnection() {
        loadProperties();
    }

    
    public static DBConnection getInstance() {
        if (instance == null) {
            synchronized (DBConnection.class) {
                if (instance == null) {
                    instance = new DBConnection();
                }
            }
        }
        return instance;
    }

    private String activeHost = "localhost";

    private void loadProperties() {
        // 1. Load bundled defaults from classpath first
        try (InputStream in = getClass().getResourceAsStream("/db.properties")) {
            if (in != null) {
                properties.load(in);
                initialized = true;
            }
        } catch (IOException ignored) {}

        // 2. Allow external db.properties in working directory to override bundled defaults
        File propFile = new File("db.properties");
        if (propFile.exists()) {
            try (InputStream in = new FileInputStream(propFile)) {
                properties.load(in);
                initialized = true;
            } catch (IOException ignored) {}
        }

        if (!initialized) {
            properties.setProperty("db.url", "jdbc:mysql://localhost:3306/cricbid?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=4000");
            properties.setProperty("db.user", "root");
            properties.setProperty("db.password", "password");
            properties.setProperty("db.driver", "com.mysql.cj.jdbc.Driver");
        }

        // Extract host from initial db.url if present
        String url = properties.getProperty("db.url", "");
        int idx = url.indexOf("://");
        if (idx >= 0) {
            String rest = url.substring(idx + 3);
            int colonOrSlash = Math.min(
                    rest.indexOf(':') >= 0 ? rest.indexOf(':') : rest.length(),
                    rest.indexOf('/') >= 0 ? rest.indexOf('/') : rest.length()
            );
            if (colonOrSlash > 0) {
                activeHost = rest.substring(0, colonOrSlash);
            }
        }

        try {
            Class.forName(properties.getProperty("db.driver", "com.mysql.cj.jdbc.Driver"));
        } catch (ClassNotFoundException e) {
            lastErrorMessage = "MySQL JDBC Driver not found in classpath: " + e.getMessage();
        }
    }

    public synchronized void configureHost(String host) {
        String cleanHost = (host == null || host.trim().isEmpty()) ? "localhost" : host.trim();
        this.activeHost = cleanHost;
        String newUrl = String.format(
                "jdbc:mysql://%s:3306/cricbid?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=4000",
                cleanHost);
        properties.setProperty("db.url", newUrl);
    }

    public synchronized String getHost() {
        return activeHost;
    }

    public int getServerPort() {
        try {
            return Integer.parseInt(properties.getProperty("server.port", "5050").trim());
        } catch (NumberFormatException e) {
            return 5050;
        }
    }

    
    private volatile boolean usingEmbeddedH2 = false;
    private static final String H2_URL = "jdbc:h2:mem:cricbid;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";

    public Connection getConnection() throws SQLException {
        if (usingEmbeddedH2) {
            return DriverManager.getConnection(H2_URL, "sa", "");
        }
        String url = properties.getProperty("db.url");
        String user = properties.getProperty("db.user");
        String pass = properties.getProperty("db.password");
        try {
            return DriverManager.getConnection(url, user, pass);
        } catch (SQLException primaryEx) {
            // Fallback to root/password or root/<empty> if lan_setup.sql hasn't been run yet on localhost
            if ("cricbid".equals(user)) {
                for (String fallbackPass : new String[]{"password", ""}) {
                    try {
                        Connection conn = DriverManager.getConnection(url, "root", fallbackPass);
                        properties.setProperty("db.user", "root");
                        properties.setProperty("db.password", fallbackPass);
                        return conn;
                    } catch (SQLException ignored) {}
                }
            }
            // Cloud / Zero-MySQL Fallback: Automatically boot H2 Embedded JDBC in MySQL Mode
            try {
                return initEmbeddedH2Database();
            } catch (Exception h2Ex) {
                lastErrorMessage = primaryEx.getMessage();
                throw primaryEx;
            }
        }
    }

    private synchronized Connection initEmbeddedH2Database() throws SQLException {
        if (usingEmbeddedH2) {
            return DriverManager.getConnection(H2_URL, "sa", "");
        }
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("H2 JDBC Driver not found", e);
        }
        Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
        runSqlResource(conn, "/sql/schema.sql");
        runSqlResource(conn, "/sql/seed.sql");
        usingEmbeddedH2 = true;
        System.out.println("[DBConnection] Initialized Embedded H2 JDBC Database (MySQL Mode) with 48 players & 6 teams.");
        return conn;
    }

    private void runSqlResource(Connection conn, String resourcePath) throws SQLException {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) return;
            String sql = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            // Strip single-line comments and incompatible MySQL database-level statements
            StringBuilder cleaned = new StringBuilder();
            for (String line : sql.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.isEmpty()) continue;
                if (trimmed.toUpperCase().startsWith("CREATE DATABASE") || trimmed.toUpperCase().startsWith("USE ")) continue;
                cleaned.append(line).append("\n");
            }
            String fullSql = cleaned.toString()
                    .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "")
                    .replace("\\'", "''");
            try (java.sql.Statement stmt = conn.createStatement()) {
                for (String part : fullSql.split(";")) {
                    String s = part.trim();
                    if (!s.isEmpty()) {
                        stmt.execute(s);
                    }
                }
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read SQL resource " + resourcePath, e);
        }
    }

    
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            lastErrorMessage = e.getMessage();
            return false;
        }
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public Properties getProperties() {
        return properties;
    }
}

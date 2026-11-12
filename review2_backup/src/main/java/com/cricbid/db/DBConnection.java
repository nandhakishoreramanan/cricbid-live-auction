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

    
    public Connection getConnection() throws SQLException {
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
            lastErrorMessage = primaryEx.getMessage();
            throw primaryEx;
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

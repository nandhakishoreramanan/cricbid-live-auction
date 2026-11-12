package com.cricbid;

import com.cricbid.ui.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.InputStream;


public class Main {

    private static Font baseUiFont = new Font("SansSerif", Font.PLAIN, 13);
    private static Font baseDisplayFont = new Font("SansSerif", Font.BOLD, 20);
    private static Font baseMonoFont = new Font("Monospaced", Font.PLAIN, 12);
    private static BufferedImage stadiumBgImage = null;

    public static void main(String[] args) {
        boolean cloudFlag = false;
        for (String a : args) {
            if ("--cloud".equalsIgnoreCase(a) || "--web-only".equalsIgnoreCase(a)) {
                cloudFlag = true;
                break;
            }
        }
        if (cloudFlag || System.getenv("RENDER") != null || java.awt.GraphicsEnvironment.isHeadless()) {
            startCloudWebMode();
            return;
        }

        setupBroadcastDarkTheme();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    private static void startCloudWebMode() {
        int port = 8080;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        }
        try {
            com.cricbid.service.AuthService authService = new com.cricbid.service.AuthService();
            com.cricbid.service.TeamService teamService = new com.cricbid.service.TeamService();
            com.cricbid.observer.AuctionSubject subject = new com.cricbid.observer.AuctionSubject();
            com.cricbid.service.AuctionService auctionService = new com.cricbid.service.AuctionService(subject);

            java.util.Set<Integer> humanIds = new java.util.HashSet<>(java.util.Arrays.asList(1, 2, 3, 4, 5, 6));
            teamService.resetAllTeams(100.0, humanIds);
            new com.cricbid.dao.impl.PlayerDAOImpl().resetAllStatus();
            auctionService.setBotsEnabled(false);

            com.cricbid.network.WebAuctionPortalServer webServer =
                    new com.cricbid.network.WebAuctionPortalServer(port, authService, auctionService, teamService);
            webServer.start();
            auctionService.startAuction(20, true);
            System.out.println("[CricBid Cloud] 24/7 Java HTTP + JDBC Server running on port " + port);
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setupBroadcastDarkTheme() {
        loadBundledGoogleFonts();
        try {
            FlatDarkLaf.setup();
            UIManager.put("defaultFont", uiFont(Font.PLAIN, 13));
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ProgressBar.arc", 10);
            UIManager.put("ScrollBar.showButtons", false);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("Table.rowHeight", 28);
            UIManager.put("Panel.background", new Color(11, 19, 43));
            UIManager.put("Viewport.background", new Color(15, 23, 42));
            UIManager.put("ScrollPane.background", new Color(15, 23, 42));
            UIManager.put("Table.background", new Color(15, 23, 42));
            UIManager.put("Table.foreground", new Color(241, 245, 249));
            UIManager.put("Table.gridColor", new Color(30, 41, 59));
            UIManager.put("TableHeader.background", new Color(30, 41, 59));
            UIManager.put("TableHeader.foreground", new Color(251, 191, 36));
            UIManager.put("TitledBorder.titleColor", new Color(251, 191, 36));
            UIManager.put("List.background", new Color(15, 23, 42));
            UIManager.put("List.foreground", new Color(241, 245, 249));
            UIManager.put("TabbedPane.selectedBackground", new Color(30, 58, 138));
            UIManager.put("TextField.background", new Color(30, 41, 59));
            UIManager.put("TextField.foreground", Color.WHITE);
            UIManager.put("PasswordField.background", new Color(30, 41, 59));
            UIManager.put("PasswordField.foreground", Color.WHITE);
            UIManager.put("ComboBox.background", new Color(30, 41, 59));
            UIManager.put("ComboBox.foreground", Color.WHITE);
            UIManager.put("Spinner.background", new Color(30, 41, 59));
            UIManager.put("Spinner.foreground", Color.WHITE);
        } catch (Exception ignored) {
        }
    }

    private static void loadBundledGoogleFonts() {
        try (InputStream is = Main.class.getResourceAsStream("/fonts/Poppins-SemiBold.ttf")) {
            if (is != null) {
                baseUiFont = Font.createFont(Font.TRUETYPE_FONT, is);
            }
        } catch (Exception ignored) {
        }
        try (InputStream is = Main.class.getResourceAsStream("/fonts/Rajdhani-Bold.ttf")) {
            if (is != null) {
                baseDisplayFont = Font.createFont(Font.TRUETYPE_FONT, is);
            }
        } catch (Exception ignored) {
        }
        try (InputStream is = Main.class.getResourceAsStream("/fonts/JetBrainsMono-Regular.ttf")) {
            if (is != null) {
                baseMonoFont = Font.createFont(Font.TRUETYPE_FONT, is);
            }
        } catch (Exception ignored) {
        }
    }

    public static Font uiFont(int style, int size) {
        return baseUiFont.deriveFont(Font.PLAIN, (float) size);
    }

    public static Font displayFont(int style, int size) {
        return baseDisplayFont.deriveFont(Font.PLAIN, (float) size);
    }

    public static Font monoFont(int style, int size) {
        return baseMonoFont.deriveFont(Font.PLAIN, (float) size);
    }

    public static synchronized BufferedImage getStadiumBackgroundImage() {
        if (stadiumBgImage == null) {
            try (InputStream is = Main.class.getResourceAsStream("/images/cricket_stadium_bg.png")) {
                if (is != null) {
                    stadiumBgImage = ImageIO.read(is);
                }
            } catch (Exception ignored) {
            }
        }
        return stadiumBgImage;
    }
}

package com.cricbid.ui;

import com.cricbid.Main;
import com.cricbid.db.DBConnection;
import com.cricbid.exception.AuthenticationException;
import com.cricbid.model.User;
import com.cricbid.network.NetworkUtils;
import com.cricbid.service.AuthService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.sql.SQLException;
import java.util.function.BiConsumer;


public class LoginPanel extends JPanel {

    private final AuthService authService;
    private final BiConsumer<User, String> onLoginSuccess;

    private JTextField hostIpField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JCheckBox showPasswordCheckBox;
    private JButton loginButton;

    public LoginPanel(AuthService authService, BiConsumer<User, String> onLoginSuccess) {
        this.authService = authService;
        this.onLoginSuccess = onLoginSuccess;

        setLayout(new BorderLayout());
        setBackground(new Color(10, 18, 38));
        initComponents();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        BufferedImage bg = Main.getStadiumBackgroundImage();
        if (bg != null) {
            g2.drawImage(bg, 0, 0, getWidth(), getHeight(), null);
            // Dark stadium broadcast vignette overlay so foreground card & text pop crisply
            g2.setColor(new Color(7, 14, 30, 145));
            g2.fillRect(0, 0, getWidth(), getHeight());
        } else {
            GradientPaint gp = new GradientPaint(
                    0, 0, new Color(8, 15, 34),
                    getWidth(), getHeight(), new Color(20, 38, 78));
            g2.setPaint(gp);
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
        g2.dispose();
    }

    private void initComponents() {
        // Top IPL Broadcast Hero Banner
        JPanel topHero = new JPanel();
        topHero.setLayout(new BoxLayout(topHero, BoxLayout.Y_AXIS));
        topHero.setOpaque(false);
        topHero.setBorder(BorderFactory.createEmptyBorder(26, 20, 8, 20));

        JLabel badge = new JLabel("*  IPL BROADCAST EDITION  |  MULTI-LAPTOP LIVE AUCTION  *", SwingConstants.CENTER);
        badge.setAlignmentX(CENTER_ALIGNMENT);
        badge.setFont(Main.uiFont(Font.BOLD, 13));
        badge.setForeground(new Color(251, 191, 36));

        JLabel mainTitle = new JLabel("CRICBID LIVE AUCTION ARENA", SwingConstants.CENTER);
        mainTitle.setAlignmentX(CENTER_ALIGNMENT);
        mainTitle.setFont(Main.displayFont(Font.BOLD, 34));
        mainTitle.setForeground(Color.WHITE);

        String detectedLanIp = NetworkUtils.getLocalLanIp();
        JLabel ipBanner = new JLabel(
                "[LAN] Detected Hotspot IP on this Laptop:  " + detectedLanIp
                        + "   (Laptop 1 uses 'localhost'  |  Laptops 2 & 3 enter Host IP)",
                SwingConstants.CENTER);
        ipBanner.setAlignmentX(CENTER_ALIGNMENT);
        ipBanner.setFont(Main.uiFont(Font.BOLD, 12));
        ipBanner.setForeground(new Color(56, 189, 248));

        topHero.add(badge);
        topHero.add(Box.createVerticalStrut(6));
        topHero.add(mainTitle);
        topHero.add(Box.createVerticalStrut(6));
        topHero.add(ipBanner);
        add(topHero, BorderLayout.NORTH);

        // Center Login Card (Translucent Dark Navy Stadium Glass Card)
        JPanel centerCard = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(14, 24, 48, 232));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        centerCard.setOpaque(false);
        centerCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(245, 158, 11), 2),
                BorderFactory.createEmptyBorder(24, 38, 24, 38)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 8, 7, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel cardHeader = new JLabel("Franchise & Auctioneer Portal Login", SwingConstants.CENTER);
        cardHeader.setFont(Main.displayFont(Font.BOLD, 22));
        cardHeader.setForeground(new Color(251, 191, 36));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        centerCard.add(cardHeader, gbc);

        // Host Server IP field
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        gbc.gridx = 0;
        JLabel hostLabel = new JLabel("Host Laptop IP:");
        hostLabel.setFont(Main.uiFont(Font.BOLD, 13));
        hostLabel.setForeground(new Color(226, 232, 240));
        centerCard.add(hostLabel, gbc);

        gbc.gridx = 1;
        hostIpField = new JTextField(DBConnection.getInstance().getHost(), 18);
        hostIpField.setPreferredSize(new Dimension(240, 34));
        hostIpField.setFont(Main.uiFont(Font.BOLD, 13));
        hostIpField.setToolTipText("Keep 'localhost' on Laptop 1 (Admin Host). On Laptops 2 & 3, type Laptop 1's Hotspot IP.");
        centerCard.add(hostIpField, gbc);

        // Username field
        gbc.gridy = 2;
        gbc.gridx = 0;
        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(Main.uiFont(Font.BOLD, 13));
        userLabel.setForeground(new Color(226, 232, 240));
        centerCard.add(userLabel, gbc);

        gbc.gridx = 1;
        usernameField = new JTextField("admin", 18);
        usernameField.setPreferredSize(new Dimension(240, 34));
        usernameField.setFont(Main.uiFont(Font.PLAIN, 13));
        centerCard.add(usernameField, gbc);

        // Password field
        gbc.gridy = 3;
        gbc.gridx = 0;
        JLabel passLabel = new JLabel("Password:");
        passLabel.setFont(Main.uiFont(Font.BOLD, 13));
        passLabel.setForeground(new Color(226, 232, 240));
        centerCard.add(passLabel, gbc);

        gbc.gridx = 1;
        passwordField = new JPasswordField("admin123", 18);
        passwordField.setPreferredSize(new Dimension(240, 34));
        centerCard.add(passwordField, gbc);

        // Show password checkbox
        gbc.gridy = 4;
        gbc.gridx = 1;
        showPasswordCheckBox = new JCheckBox("Show password");
        showPasswordCheckBox.setFont(Main.uiFont(Font.PLAIN, 12));
        showPasswordCheckBox.setForeground(new Color(203, 213, 225));
        showPasswordCheckBox.setOpaque(false);
        showPasswordCheckBox.addActionListener(e -> {
            if (showPasswordCheckBox.isSelected()) {
                passwordField.setEchoChar((char) 0);
            } else {
                passwordField.setEchoChar('•');
            }
        });
        centerCard.add(showPasswordCheckBox, gbc);

        // Quick-fill role preset bar for 3-laptop setup
        gbc.gridy = 5;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        presetPanel.setBackground(new Color(11, 19, 43));
        presetPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        JLabel qLabel = new JLabel("Quick Role Fill:");
        qLabel.setFont(Main.uiFont(Font.BOLD, 12));
        qLabel.setForeground(new Color(251, 191, 36));
        presetPanel.add(qLabel);

        JButton adminPreset = createPresetButton("Laptop 1: Admin (Host)", new Color(30, 41, 59), Color.WHITE, () -> {
            hostIpField.setText("localhost");
            usernameField.setText("admin");
            passwordField.setText("admin123");
        });
        JButton owner1Preset = createPresetButton("Laptop 2: owner1", new Color(37, 99, 235), Color.WHITE, () -> {
            usernameField.setText("owner1");
            passwordField.setText("owner123");
        });
        JButton owner2Preset = createPresetButton("Laptop 3: owner2", new Color(22, 163, 74), Color.WHITE, () -> {
            usernameField.setText("owner2");
            passwordField.setText("owner123");
        });
        presetPanel.add(adminPreset);
        presetPanel.add(owner1Preset);
        presetPanel.add(owner2Preset);
        centerCard.add(presetPanel, gbc);

        // Login button
        gbc.gridy = 6;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        loginButton = new JButton(">>  CONNECT & ENTER AUCTION ARENA  <<");
        loginButton.setFont(Main.displayFont(Font.BOLD, 17));
        loginButton.setBackground(new Color(245, 158, 11));
        loginButton.setForeground(new Color(15, 23, 42));
        loginButton.setPreferredSize(new Dimension(260, 44));
        loginButton.addActionListener(e -> handleLogin());
        centerCard.add(loginButton, gbc);

        // Credentials hint
        gbc.gridy = 7;
        JLabel hintLabel = new JLabel(
                "Accounts:  admin / admin123 (Host)   |   owner1 / owner123 (Thunder Hawks)   |   owner2 / owner123 (Iron Wolves)",
                SwingConstants.CENTER);
        hintLabel.setFont(Main.uiFont(Font.ITALIC, 11));
        hintLabel.setForeground(new Color(148, 163, 184));
        centerCard.add(hintLabel, gbc);

        JPanel outer = new JPanel(new GridBagLayout());
        outer.setOpaque(false);
        outer.add(centerCard);
        add(outer, BorderLayout.CENTER);
    }

    private JButton createPresetButton(String label, Color bg, Color fg, Runnable action) {
        JButton btn = new JButton(label);
        btn.setFont(Main.uiFont(Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.addActionListener(e -> action.run());
        return btn;
    }

    private void handleLogin() {
        String hostIp = hostIpField.getText().trim();
        if (hostIp.isEmpty()) {
            hostIp = "localhost";
            hostIpField.setText(hostIp);
        }
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        try {
            DBConnection.getInstance().configureHost(hostIp);
            User user = authService.login(username, password);
            if (onLoginSuccess != null) {
                onLoginSuccess.accept(user, hostIp);
            }
        } catch (AuthenticationException ex) {
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    "Authentication Failed",
                    JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not connect to Host MySQL Database at '" + hostIp + ":3306'.\n" +
                    "Error: " + ex.getMessage() + "\n\n" +
                    "Checklist:\n" +
                    "1. Is Laptop 1's Hotspot IP entered correctly in 'Host Laptop IP'?\n" +
                    "2. Is MySQL running on Laptop 1 with lan_setup.sql executed?",
                    "Database Network Connection Error",
                    JOptionPane.WARNING_MESSAGE);
        }
    }
}

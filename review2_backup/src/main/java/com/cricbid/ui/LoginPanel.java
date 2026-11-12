package com.cricbid.ui;

import com.cricbid.db.DBConnection;
import com.cricbid.exception.AuthenticationException;
import com.cricbid.model.User;
import com.cricbid.network.NetworkUtils;
import com.cricbid.service.AuthService;

import javax.swing.BorderFactory;
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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
        setBackground(new Color(245, 247, 250));

        initComponents();
    }

    private void initComponents() {
        JPanel centerCard = new JPanel(new GridBagLayout());
        centerCard.setBackground(Color.WHITE);
        centerCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 226, 235), 1),
                BorderFactory.createEmptyBorder(26, 36, 26, 36)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("CricBid Multi-Laptop Auction System", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 21));
        titleLabel.setForeground(new Color(25, 45, 85));

        String detectedLanIp = NetworkUtils.getLocalLanIp();
        JLabel subtitleLabel = new JLabel(
                "This Laptop's LAN IP: " + detectedLanIp + "   (Host uses 'localhost'; Participants enter Host IP)",
                SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitleLabel.setForeground(new Color(35, 95, 155));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        centerCard.add(titleLabel, gbc);

        gbc.gridy = 1;
        centerCard.add(subtitleLabel, gbc);

        // Host Server IP field
        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        JLabel hostLabel = new JLabel("Host Laptop IP:");
        hostLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        centerCard.add(hostLabel, gbc);

        gbc.gridx = 1;
        hostIpField = new JTextField(DBConnection.getInstance().getHost(), 16);
        hostIpField.setPreferredSize(new Dimension(220, 30));
        hostIpField.setToolTipText("Keep 'localhost' on Laptop 1 (Admin Host). On Laptops 2 & 3, type Laptop 1's Wi-Fi/Hotspot IP.");
        centerCard.add(hostIpField, gbc);

        // Username field
        gbc.gridy = 3;
        gbc.gridx = 0;
        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        centerCard.add(userLabel, gbc);

        gbc.gridx = 1;
        usernameField = new JTextField("admin", 16);
        usernameField.setPreferredSize(new Dimension(220, 30));
        centerCard.add(usernameField, gbc);

        // Password field
        gbc.gridy = 4;
        gbc.gridx = 0;
        JLabel passLabel = new JLabel("Password:");
        passLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        centerCard.add(passLabel, gbc);

        gbc.gridx = 1;
        passwordField = new JPasswordField("admin123", 16);
        passwordField.setPreferredSize(new Dimension(220, 30));
        centerCard.add(passwordField, gbc);

        // Show password checkbox
        gbc.gridy = 5;
        gbc.gridx = 1;
        showPasswordCheckBox = new JCheckBox("Show password");
        showPasswordCheckBox.setBackground(Color.WHITE);
        showPasswordCheckBox.addActionListener(e -> {
            if (showPasswordCheckBox.isSelected()) {
                passwordField.setEchoChar((char) 0);
            } else {
                passwordField.setEchoChar('•');
            }
        });
        centerCard.add(showPasswordCheckBox, gbc);

        // Quick-fill role preset bar for 3-laptop setup
        gbc.gridy = 6;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
        presetPanel.setOpaque(false);
        presetPanel.add(new JLabel("Quick Fill:"));

        JButton adminPreset = createPresetButton("Laptop 1: Admin", () -> {
            hostIpField.setText("localhost");
            usernameField.setText("admin");
            passwordField.setText("admin123");
        });
        JButton owner1Preset = createPresetButton("Laptop 2: owner1", () -> {
            usernameField.setText("owner1");
            passwordField.setText("owner123");
        });
        JButton owner2Preset = createPresetButton("Laptop 3: owner2", () -> {
            usernameField.setText("owner2");
            passwordField.setText("owner123");
        });
        presetPanel.add(adminPreset);
        presetPanel.add(owner1Preset);
        presetPanel.add(owner2Preset);
        centerCard.add(presetPanel, gbc);

        // Login button
        gbc.gridy = 7;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        loginButton = new JButton("Connect & Login to Auction");
        loginButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        loginButton.setBackground(new Color(30, 60, 120));
        loginButton.setForeground(Color.BLACK);
        loginButton.setPreferredSize(new Dimension(220, 38));
        loginButton.addActionListener(e -> handleLogin());
        centerCard.add(loginButton, gbc);

        // Credentials hint
        gbc.gridy = 8;
        JLabel hintLabel = new JLabel(
                "Logins: admin/admin123 (Host)  |  owner1/owner123 (Thunder Hawks)  |  owner2/owner123 (Iron Wolves)",
                SwingConstants.CENTER);
        hintLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        hintLabel.setForeground(new Color(100, 110, 130));
        centerCard.add(hintLabel, gbc);

        JPanel outer = new JPanel(new GridBagLayout());
        outer.setOpaque(false);
        outer.add(centerCard);
        add(outer, BorderLayout.CENTER);
    }

    private JButton createPresetButton(String label, Runnable action) {
        JButton btn = new JButton(label);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
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

        DBConnection.getInstance().configureHost(hostIp);

        try {
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
                    "Could not connect to MySQL on Host '" + hostIp + ":3306'.\n\n" +
                    "Details: " + ex.getMessage() + "\n\n" +
                    "Multi-Laptop Checklist:\n" +
                    "1. Connect all 3 laptops to the same Phone Hotspot / Wi-Fi.\n" +
                    "2. On Laptop 1 (Host), ensure MySQL is running and run:\n" +
                    "     mysql -u root -p < schema.sql\n" +
                    "     mysql -u root -p < seed.sql\n" +
                    "     mysql -u root -p < lan_setup.sql\n" +
                    "3. Verify Laptop 1's LAN IP matches '" + hostIp + "'.",
                    "Database Connection Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}

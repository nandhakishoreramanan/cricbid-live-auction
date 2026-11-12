package com.cricbid.ui.dialog;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import java.awt.BorderLayout;
import java.awt.Dimension;


public class AboutDialog extends JDialog {

    public AboutDialog(JFrame parent) {
        super(parent, "About CricBid: Live Auction System", true);
        setSize(580, 480);
        setLocationRelativeTo(parent);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");
        textPane.setEditable(false);
        textPane.setText(
            "<html><body style='font-family:sans-serif; font-size:12px; line-height:1.4;'>" +
            "<h2 style='color:#1e3a8a; margin-bottom:4px;'>CricBid: Live Cricket Player Auction System</h2>" +
            "<p><b>Advanced Object-Oriented Programming (AOOP) Project</b></p>" +
            "<hr/>" +
            "<h4>Key Architectural Design Patterns:</h4>" +
            "<ul>" +
            "  <li><b>Strategy Pattern:</b> AI Bidding behaviors (AggressiveBot, BalancedBot, MoneyballBot) swappable at runtime.</li>" +
            "  <li><b>Observer Pattern:</b> AuctionSubject publishes real-time live events to registered UI panels without polling.</li>" +
            "  <li><b>Singleton Pattern:</b> DBConnection maintains synchronized database access from db.properties.</li>" +
            "  <li><b>DAO Pattern:</b> Complete separation of persistence logic across Player, Team, Bid, Squad, User, and Log DAOs.</li>" +
            "  <li><b>Factory Pattern:</b> PlayerFactory creates dynamic role subclasses directly from database rows.</li>" +
            "  <li><b>JDBC Transactions:</b> Multi-table atomicity with commit and rollback for player sales and franchise trades.</li>" +
            "</ul>" +
            "<h4>Business Rules Enforced:</h4>" +
            "<ul>" +
            "  <li>Squad limits (18 minimum to 25 maximum), overseas cap of 8.</li>" +
            "  <li>Role constraints: minimum 5 specialist bowlers, at least 1 wicketkeeper.</li>" +
            "  <li>Purse reserve safety rule: Team must preserve 0.20 Cr per remaining required slot.</li>" +
            "</ul>" +
            "</body></html>"
        );

        JScrollPane scrollPane = new JScrollPane(textPane);
        panel.add(scrollPane, BorderLayout.CENTER);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel();
        btnPanel.add(closeBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(panel);
    }
}

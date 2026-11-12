package com.cricbid.ui.dialog;

import com.cricbid.model.Player;
import com.cricbid.model.Team;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


public class SoldPopupDialog extends JDialog {

    private final Timer autoCloseTimer;

    public SoldPopupDialog(JFrame parent, Player player, Team team, double price, boolean isSold) {
        super(parent, isSold ? "PLAYER SOLD!" : "PLAYER UNSOLD", true);
        setUndecorated(false);
        setSize(460, 260);
        setLocationRelativeTo(parent);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        content.setBackground(isSold ? new Color(240, 248, 255) : new Color(255, 245, 245));

        
        JLabel headerLabel = new JLabel(isSold ? "🎉 HAMMER DOWN: SOLD! 🎉" : "⚠ PASS: UNSOLD ⚠", SwingConstants.CENTER);
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        headerLabel.setForeground(isSold ? new Color(20, 100, 30) : new Color(180, 40, 40));
        content.add(headerLabel, BorderLayout.NORTH);

        
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(player.getName() + (player.isOverseas() ? " (Overseas)" : ""));
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        nameLabel.setAlignmentX(CENTER_ALIGNMENT);

        JLabel roleLabel = new JLabel(String.format("Role: %s | Rating: %d/100", player.getRole().getDisplayName(), player.getRating()));
        roleLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        roleLabel.setForeground(Color.DARK_GRAY);
        roleLabel.setAlignmentX(CENTER_ALIGNMENT);

        JLabel resultLabel;
        if (isSold && team != null) {
            resultLabel = new JLabel(String.format("Sold to %s for %.2f Cr", team.getName(), price));
            resultLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
            resultLabel.setForeground(new Color(15, 60, 130));
        } else {
            resultLabel = new JLabel("No bids received. Returned to unsold pool.");
            resultLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
            resultLabel.setForeground(Color.RED);
        }
        resultLabel.setAlignmentX(CENTER_ALIGNMENT);

        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(nameLabel);
        centerPanel.add(Box.createVerticalStrut(6));
        centerPanel.add(roleLabel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(resultLabel);
        centerPanel.add(Box.createVerticalGlue());

        content.add(centerPanel, BorderLayout.CENTER);

        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);

        JLabel timerNote = new JLabel("Auto-closing in 3 seconds (or click Dismiss)...");
        timerNote.setFont(new Font("SansSerif", Font.ITALIC, 11));
        timerNote.setForeground(Color.GRAY);

        JButton dismissBtn = new JButton("Dismiss");
        dismissBtn.setPreferredSize(new Dimension(90, 28));
        dismissBtn.addActionListener(e -> dismiss());

        bottomPanel.add(timerNote, BorderLayout.WEST);
        bottomPanel.add(dismissBtn, BorderLayout.EAST);
        content.add(bottomPanel, BorderLayout.SOUTH);

        
        content.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        content.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dismiss();
            }
        });

        setContentPane(content);

        
        autoCloseTimer = new Timer(3000, e -> dismiss());
        autoCloseTimer.setRepeats(false);
        autoCloseTimer.start();
    }

    private void dismiss() {
        if (autoCloseTimer != null && autoCloseTimer.isRunning()) {
            autoCloseTimer.stop();
        }
        dispose();
    }
}

package com.cricbid.ui.dialog;

import com.cricbid.model.Player;
import com.cricbid.model.Team;
import com.cricbid.service.SoundEffectService;

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
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;


public class SoldPopupDialog extends JDialog {

    private final Timer autoCloseTimer;

    public SoldPopupDialog(JFrame parent, Player player, Team team, double price, boolean isSold) {
        super(parent, isSold ? "HAMMER DOWN — PLAYER SOLD!" : "ROUND ENDED — UNSOLD", true);
        setUndecorated(false);
        setSize(520, 310);
        setLocationRelativeTo(parent);
        setResizable(false);

        if (isSold) {
            SoundEffectService.playSoldHammer();
        } else {
            SoundEffectService.playUnsoldTone();
        }

        JPanel content = new JPanel(new BorderLayout(12, 12)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color top = isSold ? new Color(12, 22, 48) : new Color(40, 16, 22);
                Color bottom = isSold ? new Color(24, 48, 96) : new Color(68, 22, 30);
                g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Draw rotated broadcast stamp watermark on the bottom-right corner
                AffineTransform old = g2.getTransform();
                g2.translate(getWidth() - 82, getHeight() - 88);
                g2.rotate(Math.toRadians(-14));
                Color stampColor = isSold ? new Color(245, 158, 11, 210) : new Color(239, 68, 68, 210);
                g2.setColor(stampColor);
                g2.setStroke(new BasicStroke(2.8f));
                g2.drawRoundRect(-56, -20, 112, 40, 10, 10);
                g2.setFont(new Font("SansSerif", Font.BOLD, 16));
                String stampText = isSold ? "SOLD!" : "UNSOLD";
                int sw = g2.getFontMetrics().stringWidth(stampText);
                g2.drawString(stampText, -sw / 2, 6);
                g2.setTransform(old);
                g2.dispose();
            }
        };
        Color borderAccent = isSold ? new Color(245, 158, 11) : new Color(239, 68, 68);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderAccent, 3),
                BorderFactory.createEmptyBorder(16, 24, 14, 24)
        ));

        JLabel headerLabel = new JLabel(
                isSold ? "★  HAMMER DOWN — PLAYER SOLD!  ★" : "●  ROUND EXPIRED — PLAYER UNSOLD  ●",
                SwingConstants.CENTER);
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        headerLabel.setForeground(isSold ? new Color(251, 191, 36) : new Color(252, 165, 165));
        content.add(headerLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(player.getName() + (player.isOverseas() ? "  ✈ OVERSEAS" : ""));
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(CENTER_ALIGNMENT);

        JLabel roleLabel = new JLabel(String.format("%s   •   Rating: %d / 100   •   Base: %.2f Cr",
                player.getRole().getDisplayName().toUpperCase(), player.getRating(), player.getBasePrice()));
        roleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        roleLabel.setForeground(new Color(148, 163, 184));
        roleLabel.setAlignmentX(CENTER_ALIGNMENT);

        centerPanel.add(Box.createVerticalStrut(8));
        centerPanel.add(nameLabel);
        centerPanel.add(Box.createVerticalStrut(5));
        centerPanel.add(roleLabel);
        centerPanel.add(Box.createVerticalStrut(14));

        if (isSold && team != null) {
            JLabel buyerLabel = new JLabel("ACQUIRED BY FRANCHISE: " + team.getName().toUpperCase());
            buyerLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
            buyerLabel.setForeground(new Color(56, 189, 248));
            buyerLabel.setAlignmentX(CENTER_ALIGNMENT);

            JLabel priceBadge = new JLabel(String.format("FINAL WINNING BID:  %.2f Cr", price));
            priceBadge.setFont(new Font("SansSerif", Font.BOLD, 20));
            priceBadge.setForeground(new Color(74, 222, 128));
            priceBadge.setAlignmentX(CENTER_ALIGNMENT);

            centerPanel.add(buyerLabel);
            centerPanel.add(Box.createVerticalStrut(6));
            centerPanel.add(priceBadge);
        } else {
            JLabel unsoldLabel = new JLabel("No franchise placed a bid during this round.");
            unsoldLabel.setFont(new Font("SansSerif", Font.ITALIC, 15));
            unsoldLabel.setForeground(new Color(254, 202, 202));
            unsoldLabel.setAlignmentX(CENTER_ALIGNMENT);
            centerPanel.add(unsoldLabel);
        }
        centerPanel.add(Box.createVerticalGlue());

        content.add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);

        JLabel timerNote = new JLabel("Advancing to next player in 3 seconds (or click anywhere)...");
        timerNote.setFont(new Font("SansSerif", Font.ITALIC, 11));
        timerNote.setForeground(new Color(148, 163, 184));

        JButton dismissBtn = new JButton("Continue ▶");
        dismissBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        dismissBtn.setBackground(borderAccent);
        dismissBtn.setForeground(new Color(15, 23, 42));
        dismissBtn.setPreferredSize(new Dimension(115, 30));
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

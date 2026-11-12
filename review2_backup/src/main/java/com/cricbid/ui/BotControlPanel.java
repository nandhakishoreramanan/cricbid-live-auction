package com.cricbid.ui;

import com.cricbid.model.BotStrategyType;
import com.cricbid.model.OwnerType;
import com.cricbid.model.Team;
import com.cricbid.service.AuctionService;
import com.cricbid.service.TeamService;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class BotControlPanel extends JPanel {

    private final AuctionService auctionService;
    private final TeamService teamService;

    private JToggleButton botToggleBtn;
    private JSlider botSpeedSlider;
    private JPanel teamStrategiesPanel;
    private final Map<Integer, JComboBox<BotStrategyType>> strategyCombos = new HashMap<>();

    public BotControlPanel(AuctionService auctionService, TeamService teamService) {
        this.auctionService = auctionService;
        this.teamService = teamService;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        initComponents();
    }

    private void initComponents() {
        JPanel masterControlPanel = new JPanel(new GridBagLayout());
        masterControlPanel.setBorder(BorderFactory.createTitledBorder("AI Engine Master Controls"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        masterControlPanel.add(new JLabel("Bot Activity State:"), gbc);

        gbc.gridx = 1;
        botToggleBtn = new JToggleButton("Bots PAUSED (Click to Enable)", false);
        botToggleBtn.setPreferredSize(new Dimension(240, 32));
        botToggleBtn.setBackground(new Color(255, 220, 220));
        botToggleBtn.addActionListener(e -> {
            boolean active = botToggleBtn.isSelected();
            auctionService.setBotsEnabled(active);
            syncToggleStyle(active);
        });
        masterControlPanel.add(botToggleBtn, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        JLabel sliderLabel = new JLabel("Bot Response Delay: 2.0s");
        masterControlPanel.add(sliderLabel, gbc);

        gbc.gridx = 1;
        botSpeedSlider = new JSlider(10, 40, 20);
        botSpeedSlider.setMajorTickSpacing(10);
        botSpeedSlider.setMinorTickSpacing(2);
        botSpeedSlider.setPaintTicks(true);
        botSpeedSlider.setPaintLabels(true);
        botSpeedSlider.addChangeListener(e -> {
            double secs = botSpeedSlider.getValue() / 10.0;
            sliderLabel.setText(String.format("Bot Response Delay: %.1fs", secs));
            int minMs = (int) (secs * 700);
            int maxMs = (int) (secs * 1300);
            auctionService.setBotSpeed(minMs, maxMs);
        });
        masterControlPanel.add(botSpeedSlider, gbc);

        add(masterControlPanel, BorderLayout.NORTH);

        teamStrategiesPanel = new JPanel(new GridLayout(0, 2, 12, 12));
        teamStrategiesPanel.setBorder(BorderFactory.createTitledBorder("Swap Bot Strategies at Runtime (Strategy Pattern)"));
        add(teamStrategiesPanel, BorderLayout.CENTER);
    }

    private void syncToggleStyle(boolean active) {
        botToggleBtn.setSelected(active);
        botToggleBtn.setText(active ? "Bots Active (Click to Pause)" : "Bots PAUSED (Click to Enable)");
        botToggleBtn.setBackground(active ? new Color(220, 245, 220) : new Color(255, 220, 220));
    }

    public void refreshBots() {
        syncToggleStyle(auctionService.isBotsEnabled());
        teamStrategiesPanel.removeAll();
        strategyCombos.clear();

        try {
            List<Team> teams = teamService.getAllTeamsWithSquads();
            for (Team t : teams) {
                if (t.getOwnerType() == OwnerType.BOT) {
                    JPanel row = new JPanel(new BorderLayout(8, 4));
                    row.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(225, 230, 240)),
                            BorderFactory.createEmptyBorder(8, 8, 8, 8)
                    ));

                    JLabel nameLabel = new JLabel(t.getName());
                    nameLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

                    JComboBox<BotStrategyType> combo = new JComboBox<>(BotStrategyType.values());
                    combo.setSelectedItem(t.getBotStrategy() != null ? t.getBotStrategy() : BotStrategyType.BALANCED);
                    combo.addActionListener(e -> {
                        BotStrategyType selected = (BotStrategyType) combo.getSelectedItem();
                        try {
                            teamService.updateBotStrategy(t.getTeamId(), selected);
                            t.setBotStrategy(selected);
                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(this, "Failed to update strategy: " + ex.getMessage(),
                                    "DB Error", JOptionPane.ERROR_MESSAGE);
                        }
                    });

                    strategyCombos.put(t.getTeamId(), combo);

                    row.add(nameLabel, BorderLayout.WEST);
                    row.add(combo, BorderLayout.EAST);
                    teamStrategiesPanel.add(row);
                }
            }
        } catch (SQLException ex) {
            teamStrategiesPanel.add(new JLabel("Failed to load bots: " + ex.getMessage()));
        }

        teamStrategiesPanel.revalidate();
        teamStrategiesPanel.repaint();
    }
}

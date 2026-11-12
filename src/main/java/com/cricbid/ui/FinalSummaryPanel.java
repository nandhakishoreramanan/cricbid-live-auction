package com.cricbid.ui;

import com.cricbid.model.SquadEntry;
import com.cricbid.model.Team;
import com.cricbid.service.ReportService;
import com.cricbid.service.RuleValidator;
import com.cricbid.service.TeamService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.List;


public class FinalSummaryPanel extends JPanel {

    private final TeamService teamService;
    private final ReportService reportService;
    private final RuleValidator ruleValidator;

    private JLabel bestBuyLabel;
    private JLabel mostExpensiveLabel;
    private JLabel totalSpendLabel;

    private JTable summaryTable;
    private DefaultTableModel summaryModel;

    public FinalSummaryPanel(TeamService teamService, ReportService reportService) {
        this.teamService = teamService;
        this.reportService = reportService;
        this.ruleValidator = teamService.getRuleValidator();

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        initComponents();
    }

    private void initComponents() {
        
        JPanel kpiPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        kpiPanel.setBorder(BorderFactory.createTitledBorder("Auction Highlights & Marquee Buys"));

        bestBuyLabel = new JLabel("Best Value Buy: Loading...", JLabel.CENTER);
        bestBuyLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        bestBuyLabel.setForeground(new Color(74, 222, 128));

        mostExpensiveLabel = new JLabel("Most Expensive Buy: Loading...", JLabel.CENTER);
        mostExpensiveLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        mostExpensiveLabel.setForeground(new Color(251, 191, 36));

        totalSpendLabel = new JLabel("Total Money Spent: 0.00 Cr", JLabel.CENTER);
        totalSpendLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        totalSpendLabel.setForeground(new Color(56, 189, 248));

        kpiPanel.add(bestBuyLabel);
        kpiPanel.add(mostExpensiveLabel);
        kpiPanel.add(totalSpendLabel);

        add(kpiPanel, BorderLayout.NORTH);

        
        JPanel tableContainer = new JPanel(new BorderLayout());
        tableContainer.setBorder(BorderFactory.createTitledBorder("Final Franchise Compliance & Roster Audit"));

        String[] cols = {"Team", "Purse Left", "Squad Size", "Overseas", "Bowlers", "Keepers", "Compliance Status"};
        summaryModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        summaryTable = new JTable(summaryModel);
        summaryTable.setRowHeight(26);

        
        summaryTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String status = (String) table.getValueAt(row, 6);
                if (!isSelected) {
                    if (status != null && status.startsWith("VIOLATION")) {
                        c.setBackground(new Color(64, 22, 32));
                        c.setForeground(new Color(252, 165, 165));
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    } else {
                        c.setBackground(row % 2 == 0 ? new Color(15, 23, 42) : new Color(23, 35, 62));
                        c.setForeground(new Color(74, 222, 128));
                    }
                }
                return c;
            }
        });

        tableContainer.add(new JScrollPane(summaryTable), BorderLayout.CENTER);
        add(tableContainer, BorderLayout.CENTER);

        
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshBtn = new JButton("Recalculate Summary");
        refreshBtn.addActionListener(e -> refreshSummary());
        bottomBar.add(refreshBtn);
        add(bottomBar, BorderLayout.SOUTH);
    }

    public void refreshSummary() {
        summaryModel.setRowCount(0);
        try {
            List<Team> teams = teamService.getAllTeamsWithSquads();
            double totalSpent = 0;

            for (Team t : teams) {
                totalSpent += t.getSpentAmount();
                String issues = ruleValidator.getRosterComplianceIssues(t);
                String compliance = issues.isEmpty() ? "COMPLIANT" : "VIOLATION: " + issues.replace("\n", " ");

                int minSquad = RuleValidator.getActiveMinSquadSize();
                int minBowlers = RuleValidator.getActiveMinBowlers();

                summaryModel.addRow(new Object[]{
                        t.getName(),
                        String.format("%.2f Cr", t.getPurseRemaining()),
                        String.format("%d/25 (Min %d)", t.getSquadSize(), minSquad),
                        t.getOverseasCount() + "/8",
                        String.format("%d (Min %d)", t.getBowlerCount(), minBowlers),
                        t.getWicketKeeperCount() + " (Min 1)",
                        compliance
                });
            }

            totalSpendLabel.setText(String.format("Total Auction Spend: %.2f Cr", totalSpent));

            SquadEntry mostExp = reportService.getMostExpensivePlayer();
            if (mostExp != null && mostExp.getPlayer() != null) {
                mostExpensiveLabel.setText(String.format("Top Buy: %s (%.2f Cr)",
                        mostExp.getPlayer().getName(), mostExp.getSoldPrice()));
            } else {
                mostExpensiveLabel.setText("Top Buy: None yet");
            }

            SquadEntry bestVal = reportService.getBestValueBuy();
            if (bestVal != null && bestVal.getPlayer() != null) {
                bestBuyLabel.setText(String.format("Best Value: %s (Rating %d, %.2f Cr)",
                        bestVal.getPlayer().getName(), bestVal.getPlayer().getRating(), bestVal.getSoldPrice()));
            } else {
                bestBuyLabel.setText("Best Value: None yet");
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to compute tournament summary: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

package com.cricbid.ui;

import com.cricbid.model.PlayerRole;
import com.cricbid.model.SquadEntry;
import com.cricbid.service.ReportService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ReportsPanel extends JPanel {

    private final ReportService reportService;
    private ChartCanvasPanel chartCanvasPanel;
    private JTable topBuysTable;
    private DefaultTableModel topBuysModel;

    public ReportsPanel(ReportService reportService) {
        this.reportService = reportService;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        initComponents();
    }

    private void initComponents() {
        
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        JButton exportBtn = new JButton("Export to CSV (JFileChooser)");
        exportBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        exportBtn.addActionListener(e -> handleExportCSV());
        topBar.add(exportBtn);

        JButton refreshBtn = new JButton("Refresh Charts");
        refreshBtn.addActionListener(e -> refreshReports());
        topBar.add(refreshBtn);

        add(topBar, BorderLayout.NORTH);

        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.6);

        
        chartCanvasPanel = new ChartCanvasPanel();
        splitPane.setLeftComponent(chartCanvasPanel);

        
        JPanel tableContainer = new JPanel(new BorderLayout());
        tableContainer.setBorder(BorderFactory.createTitledBorder("Top 10 Most Expensive Buys"));

        String[] cols = {"Rank", "Player", "Role", "Rating", "Sold Price (Cr)"};
        topBuysModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        topBuysTable = new JTable(topBuysModel);
        topBuysTable.setRowHeight(22);
        tableContainer.add(new JScrollPane(topBuysTable), BorderLayout.CENTER);
        splitPane.setRightComponent(tableContainer);

        add(splitPane, BorderLayout.CENTER);
    }

    public void refreshReports() {
        try {
            Map<String, Double> spending = reportService.getSpendingByTeam();
            Map<PlayerRole, Integer> roles = reportService.getRoleDistribution();
            chartCanvasPanel.setData(spending, roles);

            topBuysModel.setRowCount(0);
            List<SquadEntry> top = reportService.getTopBuys(10);
            int rank = 1;
            for (SquadEntry e : top) {
                if (e.getPlayer() != null) {
                    topBuysModel.addRow(new Object[]{
                            rank++,
                            e.getPlayer().getName(),
                            e.getPlayer().getRole().getDisplayName(),
                            e.getPlayer().getRating(),
                            String.format("%.2f", e.getSoldPrice())
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to aggregate report data: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleExportCSV() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Auction Rosters to CSV");
        fileChooser.setSelectedFile(new File("cricbid_auction_results.csv"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Comma Separated (*.csv)", "csv"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".csv")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".csv");
            }
            try {
                reportService.exportAuctionResultsToCSV(fileToSave);
                JOptionPane.showMessageDialog(this, "CSV exported successfully to:\n" + fileToSave.getAbsolutePath(),
                        "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    
    private static class ChartCanvasPanel extends JPanel {
        private Map<String, Double> spendingData = new HashMap<>();
        private Map<PlayerRole, Integer> roleData = new HashMap<>();

        public ChartCanvasPanel() {
            setBackground(new Color(17, 28, 53));
            setBorder(BorderFactory.createTitledBorder("Visual Analytics (Custom 2D Graphics Rendering)"));
            setPreferredSize(new Dimension(500, 400));
        }

        public void setData(Map<String, Double> spending, Map<PlayerRole, Integer> roles) {
            this.spendingData = spending != null ? spending : new HashMap<>();
            this.roleData = roles != null ? roles : new HashMap<>();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            int halfHeight = height / 2;

            
            drawBarChart(g2, 20, 30, width - 40, halfHeight - 45);

            
            drawPieChart(g2, 20, halfHeight + 20, width - 40, halfHeight - 40);

            g2.dispose();
        }

        private void drawBarChart(Graphics2D g2, int x, int y, int w, int h) {
            g2.setColor(new Color(251, 191, 36));
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.drawString("Team Spending in Crores (Bar Chart)", x, y);

            if (spendingData.isEmpty()) {
                g2.setColor(new Color(148, 163, 184));
                g2.drawString("No team spending data available yet.", x + 20, y + 40);
                return;
            }

            int count = spendingData.size();
            int barWidth = Math.max(15, (w - 60) / Math.max(1, count) - 10);
            double maxSpend = 100.0; 

            int startX = x + 30;
            int baseY = y + h - 20;

            
            g2.setColor(new Color(71, 85, 105));
            g2.drawLine(startX - 5, baseY, startX + (count * (barWidth + 10)), baseY);

            Color[] barColors = {
                    new Color(56, 189, 248), new Color(251, 191, 36), new Color(74, 222, 128),
                    new Color(248, 113, 113), new Color(167, 139, 250), new Color(251, 146, 60)
            };

            int i = 0;
            for (Map.Entry<String, Double> entry : spendingData.entrySet()) {
                double spent = entry.getValue();
                int barHeight = (int) ((spent / maxSpend) * (h - 50));
                barHeight = Math.max(4, Math.min(h - 50, barHeight));

                int barX = startX + i * (barWidth + 10);
                int barY = baseY - barHeight;

                g2.setColor(barColors[i % barColors.length]);
                g2.fillRect(barX, barY, barWidth, barHeight);
                g2.setColor(new Color(15, 23, 42));
                g2.drawRect(barX, barY, barWidth, barHeight);

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                g2.drawString(String.format("%.1f", spent), barX, barY - 4);

                String teamInitials = entry.getKey().length() > 6 ? entry.getKey().substring(0, 6) : entry.getKey();
                g2.setColor(new Color(203, 213, 225));
                g2.drawString(teamInitials, barX, baseY + 14);

                i++;
            }
        }

        private void drawPieChart(Graphics2D g2, int x, int y, int w, int h) {
            g2.setColor(new Color(251, 191, 36));
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.drawString("Squad Composition by Role (Pie Chart)", x, y);

            int totalPlayers = roleData.values().stream().mapToInt(Integer::intValue).sum();
            if (totalPlayers == 0) {
                g2.setColor(new Color(148, 163, 184));
                g2.drawString("No players purchased yet.", x + 20, y + 40);
                return;
            }

            int diameter = Math.min(w / 2, h - 30);
            int pieX = x + 30;
            int pieY = y + 15;

            Color[] roleColors = {
                    new Color(56, 189, 248), 
                    new Color(248, 113, 113),  
                    new Color(74, 222, 128),  
                    new Color(251, 191, 36)  
            };

            PlayerRole[] roles = PlayerRole.values();
            int startAngle = 0;

            for (int i = 0; i < roles.length; i++) {
                int count = roleData.getOrDefault(roles[i], 0);
                int arcAngle = (int) Math.round(((double) count / totalPlayers) * 360.0);

                g2.setColor(roleColors[i]);
                g2.fillArc(pieX, pieY, diameter, diameter, startAngle, arcAngle);
                g2.setColor(new Color(15, 23, 42));
                g2.drawArc(pieX, pieY, diameter, diameter, startAngle, arcAngle);

                startAngle += arcAngle;
            }

            
            int legendX = pieX + diameter + 30;
            int legendY = pieY + 15;
            for (int i = 0; i < roles.length; i++) {
                int count = roleData.getOrDefault(roles[i], 0);
                g2.setColor(roleColors[i]);
                g2.fillRect(legendX, legendY + (i * 20), 12, 12);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                g2.drawString(String.format("%s: %d", roles[i].getDisplayName(), count), legendX + 18, legendY + (i * 20) + 10);
            }
        }
    }
}

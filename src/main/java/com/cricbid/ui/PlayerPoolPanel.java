package com.cricbid.ui;

import com.cricbid.dao.PlayerDAO;
import com.cricbid.dao.impl.PlayerDAOImpl;
import com.cricbid.model.Player;
import com.cricbid.model.PlayerRole;
import com.cricbid.model.PlayerStatus;
import com.cricbid.ui.renderer.PlayerTableRenderer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class PlayerPoolPanel extends JPanel {

    private final PlayerDAO playerDAO;
    private JTable playerTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JComboBox<String> statusFilterCombo;
    private JCheckBox overseasOnlyCheckBox;

    private JRadioButton allRolesRadio;
    private JRadioButton batsmanRadio;
    private JRadioButton bowlerRadio;
    private JRadioButton allRounderRadio;
    private JRadioButton keeperRadio;

    private final List<Player> masterList = new ArrayList<>();

    public PlayerPoolPanel() {
        this.playerDAO = new PlayerDAOImpl();
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        initFilterBar();
        initTable();
    }

    private void initFilterBar() {
        JPanel filterContainer = new JPanel(new BorderLayout(6, 6));
        filterContainer.setBorder(BorderFactory.createTitledBorder("Search & Filter Player Roster"));

        
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        row1.add(new JLabel("Search Name:"));
        searchField = new JTextField(12);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });
        row1.add(searchField);

        row1.add(new JLabel("Status:"));
        statusFilterCombo = new JComboBox<>(new String[]{"ALL", "AVAILABLE", "SOLD", "UNSOLD"});
        statusFilterCombo.addActionListener(e -> applyFilters());
        row1.add(statusFilterCombo);

        overseasOnlyCheckBox = new JCheckBox("Overseas Only");
        overseasOnlyCheckBox.addActionListener(e -> applyFilters());
        row1.add(overseasOnlyCheckBox);

        filterContainer.add(row1, BorderLayout.NORTH);

        
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        row2.add(new JLabel("Role Filter:"));

        allRolesRadio = new JRadioButton("All Roles", true);
        batsmanRadio = new JRadioButton("Batsmen");
        bowlerRadio = new JRadioButton("Bowlers");
        allRounderRadio = new JRadioButton("All-Rounders");
        keeperRadio = new JRadioButton("Wicketkeepers");

        ButtonGroup roleGroup = new ButtonGroup();
        roleGroup.add(allRolesRadio);
        roleGroup.add(batsmanRadio);
        roleGroup.add(bowlerRadio);
        roleGroup.add(allRounderRadio);
        roleGroup.add(keeperRadio);

        allRolesRadio.addActionListener(e -> applyFilters());
        batsmanRadio.addActionListener(e -> applyFilters());
        bowlerRadio.addActionListener(e -> applyFilters());
        allRounderRadio.addActionListener(e -> applyFilters());
        keeperRadio.addActionListener(e -> applyFilters());

        row2.add(allRolesRadio);
        row2.add(batsmanRadio);
        row2.add(bowlerRadio);
        row2.add(allRounderRadio);
        row2.add(keeperRadio);

        filterContainer.add(row2, BorderLayout.SOUTH);

        add(filterContainer, BorderLayout.NORTH);
    }

    private void initTable() {
        String[] columns = {"ID", "Name", "Role", "Overseas", "Base (Cr)", "Rating", "Key Stats", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        playerTable = new JTable(tableModel);
        playerTable.setDefaultRenderer(Object.class, new PlayerTableRenderer());
        playerTable.setRowHeight(24);
        playerTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));

        
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem viewStatsItem = new JMenuItem("View Detailed Player Profile");
        viewStatsItem.addActionListener(e -> showSelectedPlayerPopup());
        popupMenu.add(viewStatsItem);

        playerTable.setComponentPopupMenu(popupMenu);
        playerTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = playerTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < playerTable.getRowCount()) {
                        playerTable.setRowSelectionInterval(row, row);
                    }
                }
            }
        });

        add(new JScrollPane(playerTable), BorderLayout.CENTER);
    }

    public void refreshPlayers() {
        try {
            masterList.clear();
            masterList.addAll(playerDAO.findAll());
            applyFilters();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load players: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyFilters() {
        String query = searchField.getText().trim().toLowerCase();
        String statusFilter = (String) statusFilterCombo.getSelectedItem();
        boolean overseasOnly = overseasOnlyCheckBox.isSelected();

        PlayerRole roleFilter = null;
        if (batsmanRadio.isSelected()) roleFilter = PlayerRole.BATSMAN;
        else if (bowlerRadio.isSelected()) roleFilter = PlayerRole.BOWLER;
        else if (allRounderRadio.isSelected()) roleFilter = PlayerRole.ALLROUNDER;
        else if (keeperRadio.isSelected()) roleFilter = PlayerRole.WICKETKEEPER;

        tableModel.setRowCount(0);

        for (Player p : masterList) {
            
            if (!query.isEmpty() && !p.getName().toLowerCase().contains(query)) {
                continue;
            }
            
            if (statusFilter != null && !statusFilter.equals("ALL")) {
                if (!p.getStatus().name().equalsIgnoreCase(statusFilter)) {
                    continue;
                }
            }
            
            if (overseasOnly && !p.isOverseas()) {
                continue;
            }
            
            if (roleFilter != null && p.getRole() != roleFilter) {
                continue;
            }

            tableModel.addRow(new Object[]{
                    p.getPlayerId(),
                    p.getName(),
                    p.getRole().getDisplayName(),
                    p.isOverseas() ? "Yes ✈" : "No",
                    String.format("%.2f", p.getBasePrice()),
                    p.getRating(),
                    p.describe(),
                    p.getStatus().name()
            });
        }
    }

    private void showSelectedPlayerPopup() {
        int selectedRow = playerTable.getSelectedRow();
        if (selectedRow < 0) return;

        int playerId = (int) playerTable.getValueAt(selectedRow, 0);
        Player selected = masterList.stream().filter(p -> p.getPlayerId() == playerId).findFirst().orElse(null);
        if (selected != null) {
            String details = String.format(
                    "Name: %s\n" +
                    "Role: %s\n" +
                    "Nationality: %s\n" +
                    "Base Price: %.2f Cr\n" +
                    "Rating: %d/100\n" +
                    "Estimated Valuation: %.2f Cr\n" +
                    "Status: %s\n\n" +
                    "Career Statistics:\n%s",
                    selected.getName(),
                    selected.getRole().getDisplayName(),
                    selected.isOverseas() ? "Overseas (Foreign Player)" : "Domestic",
                    selected.getBasePrice(),
                    selected.getRating(),
                    selected.calculateValue(),
                    selected.getStatus(),
                    selected.describe()
            );

            JOptionPane.showMessageDialog(this, details, "Player Scouting Report", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}

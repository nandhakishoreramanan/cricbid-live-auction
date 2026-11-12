package com.cricbid.ui.renderer;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;


public class PlayerTableRenderer extends DefaultTableCellRenderer {

    private static final Color COLOR_AVAILABLE = new Color(230, 255, 230);
    private static final Color COLOR_SOLD = new Color(220, 235, 255);
    private static final Color COLOR_UNSOLD = new Color(255, 230, 230);

    private static final Color ROLE_BATSMAN = new Color(20, 90, 160);
    private static final Color ROLE_BOWLER = new Color(180, 40, 40);
    private static final Color ROLE_ALLROUNDER = new Color(40, 140, 50);
    private static final Color ROLE_KEEPER = new Color(150, 90, 20);

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (!isSelected) {
            
            String statusStr = "";
            int statusCol = -1;
            for (int i = 0; i < table.getColumnCount(); i++) {
                if ("Status".equalsIgnoreCase(table.getColumnName(i))) {
                    statusCol = i;
                    break;
                }
            }

            if (statusCol != -1 && table.getValueAt(row, statusCol) != null) {
                statusStr = table.getValueAt(row, statusCol).toString();
            }

            if ("SOLD".equalsIgnoreCase(statusStr)) {
                c.setBackground(COLOR_SOLD);
            } else if ("UNSOLD".equalsIgnoreCase(statusStr)) {
                c.setBackground(COLOR_UNSOLD);
            } else {
                c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 250, 252));
            }

            
            int roleCol = -1;
            for (int i = 0; i < table.getColumnCount(); i++) {
                if ("Role".equalsIgnoreCase(table.getColumnName(i))) {
                    roleCol = i;
                    break;
                }
            }

            if (column == roleCol && value != null) {
                String val = value.toString().toUpperCase();
                c.setFont(c.getFont().deriveFont(Font.BOLD));
                if (val.contains("BAT")) c.setForeground(ROLE_BATSMAN);
                else if (val.contains("BOWL")) c.setForeground(ROLE_BOWLER);
                else if (val.contains("ALL")) c.setForeground(ROLE_ALLROUNDER);
                else if (val.contains("KEEP")) c.setForeground(ROLE_KEEPER);
            } else {
                c.setForeground(Color.BLACK);
            }
        }

        return c;
    }
}

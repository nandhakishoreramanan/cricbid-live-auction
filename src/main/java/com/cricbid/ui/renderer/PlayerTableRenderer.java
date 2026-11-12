package com.cricbid.ui.renderer;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;


public class PlayerTableRenderer extends DefaultTableCellRenderer {

    private static final Color COLOR_ROW_EVEN = new Color(15, 23, 42);
    private static final Color COLOR_ROW_ODD = new Color(23, 35, 62);
    private static final Color COLOR_SOLD = new Color(16, 56, 42);
    private static final Color COLOR_UNSOLD = new Color(64, 22, 32);

    private static final Color ROLE_BATSMAN = new Color(56, 189, 248);
    private static final Color ROLE_BOWLER = new Color(248, 113, 113);
    private static final Color ROLE_ALLROUNDER = new Color(74, 222, 128);
    private static final Color ROLE_KEEPER = new Color(251, 191, 36);

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
                c.setBackground(row % 2 == 0 ? COLOR_ROW_EVEN : COLOR_ROW_ODD);
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
            } else if (column == statusCol && value != null) {
                c.setFont(c.getFont().deriveFont(Font.BOLD));
                if ("SOLD".equalsIgnoreCase(statusStr)) {
                    c.setForeground(new Color(74, 222, 128));
                } else if ("UNSOLD".equalsIgnoreCase(statusStr)) {
                    c.setForeground(new Color(252, 165, 165));
                } else {
                    c.setForeground(new Color(226, 232, 240));
                }
            } else {
                c.setForeground(new Color(241, 245, 249));
            }
        }

        return c;
    }
}

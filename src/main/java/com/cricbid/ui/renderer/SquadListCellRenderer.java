package com.cricbid.ui.renderer;

import com.cricbid.model.Player;
import com.cricbid.model.SquadEntry;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;


public class SquadListCellRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (value instanceof SquadEntry) {
            SquadEntry entry = (SquadEntry) value;
            Player p = entry.getPlayer();
            if (p != null) {
                String overseas = p.isOverseas() ? " ✈" : "";
                setText(String.format(" %s%s (%s, Rating %d) - %.2f Cr",
                        p.getName(), overseas, p.getRole().getDisplayName(), p.getRating(), entry.getSoldPrice()));
            } else {
                setText(String.format(" Player #%d - %.2f Cr", entry.getPlayerId(), entry.getSoldPrice()));
            }
        } else if (value instanceof Player) {
            Player p = (Player) value;
            String overseas = p.isOverseas() ? " ✈" : "";
            setText(String.format(" %s%s (%s, Rating %d) - Base: %.2f Cr",
                    p.getName(), overseas, p.getRole().getDisplayName(), p.getRating(), p.getBasePrice()));
        }

        return this;
    }
}

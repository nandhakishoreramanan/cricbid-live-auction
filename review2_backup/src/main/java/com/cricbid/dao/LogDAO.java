package com.cricbid.dao;

import com.cricbid.model.AuctionEvent;
import com.cricbid.model.AuctionEventType;

import java.sql.SQLException;
import java.util.List;


public interface LogDAO {
    void logEvent(AuctionEventType type, String details) throws SQLException;
    List<AuctionEvent> getRecentEvents(int limit) throws SQLException;
    void clearLogs() throws SQLException;
}

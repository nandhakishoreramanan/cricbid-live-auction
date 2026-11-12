package com.cricbid.dao;

import com.cricbid.model.Bid;

import java.sql.SQLException;
import java.util.List;


public interface BidDAO {
    int recordBid(Bid bid) throws SQLException;
    List<Bid> getBidsForPlayer(int playerId) throws SQLException;
    Bid getHighestBidForPlayer(int playerId) throws SQLException;
    void clearBids() throws SQLException;
}

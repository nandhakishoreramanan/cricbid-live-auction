package com.cricbid.model;


public enum AuctionEventType {
    BID,
    SOLD,
    UNSOLD,
    RULE_VIOLATION,
    TRADE;

    public static AuctionEventType fromString(String str) {
        if (str == null) return BID;
        try {
            return AuctionEventType.valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BID;
        }
    }
}

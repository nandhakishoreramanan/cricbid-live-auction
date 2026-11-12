package com.cricbid.model;

import java.sql.Timestamp;


public class AuctionEvent {
    private int logId;
    private AuctionEventType event;
    private String details;
    private Timestamp logTime;

    public AuctionEvent() {
        this.logTime = new Timestamp(System.currentTimeMillis());
    }

    public AuctionEvent(AuctionEventType event, String details) {
        this();
        this.event = event;
        this.details = details;
    }

    public AuctionEvent(int logId, AuctionEventType event, String details, Timestamp logTime) {
        this.logId = logId;
        this.event = event;
        this.details = details;
        this.logTime = logTime;
    }

    public int getLogId() {
        return logId;
    }

    public void setLogId(int logId) {
        this.logId = logId;
    }

    public AuctionEventType getEvent() {
        return event;
    }

    public void setEvent(AuctionEventType event) {
        this.event = event;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Timestamp getLogTime() {
        return logTime;
    }

    public void setLogTime(Timestamp logTime) {
        this.logTime = logTime;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s",
                logTime != null ? logTime.toString().substring(11, 19) : "",
                event, details);
    }
}

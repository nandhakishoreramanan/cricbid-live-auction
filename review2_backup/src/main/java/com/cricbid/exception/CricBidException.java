package com.cricbid.exception;


public class CricBidException extends Exception {
    public CricBidException(String message) {
        super(message);
    }

    public CricBidException(String message, Throwable cause) {
        super(message, cause);
    }
}

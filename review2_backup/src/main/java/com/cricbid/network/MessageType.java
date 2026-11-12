package com.cricbid.network;

public enum MessageType {
    // Client -> Host Server
    REGISTER_CLIENT,
    PLACE_BID,
    PASS_PLAYER,

    // Host Server -> Clients
    SYNC_STATE,
    AUCTION_RESET,
    PLAYER_UP,
    BID_PLACED,
    PLAYER_SOLD,
    PLAYER_UNSOLD,
    TIMER_TICK,
    AUCTION_PAUSED,
    AUCTION_COMPLETED,
    RULE_VIOLATION,
    BID_REJECTED,
    LOG_BROADCAST
}

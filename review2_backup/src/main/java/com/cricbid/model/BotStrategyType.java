package com.cricbid.model;


public enum BotStrategyType {
    AGGRESSIVE("Aggressive (Star Hunter)"),
    BALANCED("Balanced (Squad Builder)"),
    MONEYBALL("Moneyball (Value Optimizer)");

    private final String description;

    BotStrategyType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static BotStrategyType fromString(String str) {
        if (str == null) return BALANCED;
        try {
            return BotStrategyType.valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BALANCED;
        }
    }
}

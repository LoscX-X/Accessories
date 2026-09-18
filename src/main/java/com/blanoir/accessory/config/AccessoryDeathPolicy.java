package com.blanoir.accessory.config;

import java.util.Locale;

public enum AccessoryDeathPolicy {
    KEEP, DROP, FOLLOW_KEEP_INVENTORY;

    public static AccessoryDeathPolicy parse(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "keep" -> KEEP;
            case "drop" -> DROP;
            case "follow-keep-inventory" -> FOLLOW_KEEP_INVENTORY;
            default -> throw new IllegalArgumentException("lifecycle.death must be keep, drop or follow-keep-inventory");
        };
    }
}

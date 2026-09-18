package com.blanoir.accessory.config;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;
public record DebugSettings(boolean enabled, Set<String> categories, int intervalTicks) {
    public DebugSettings { categories = Set.copyOf(categories); }
    public static DebugSettings read(ConfigurationSection config) {
        return new DebugSettings(config.getBoolean("debug.enabled", false),
                new HashSet<>(config.getStringList("debug.categories")), Math.max(0, config.getInt("debug.interval-ticks", 20)));
    }
}

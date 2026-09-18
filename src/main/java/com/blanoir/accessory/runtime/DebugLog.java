package com.blanoir.accessory.runtime;

import com.blanoir.accessory.Accessory;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Category filtering, lazy messages, and per-event throttling. Errors are always visible. */
public final class DebugLog {
    private final Accessory plugin;
    private final Map<String, Long> last = new HashMap<>();
    public DebugLog(Accessory plugin) { this.plugin = plugin; }
    public synchronized void trace(String category, String key, Supplier<String> message) {
        var settings = plugin.settings().debug();
        if (!settings.enabled() || !settings.categories().contains(category)) return;
        long now = System.nanoTime(), interval = settings.intervalTicks() * 50_000_000L;
        String token = category + ":" + key;
        if (now - last.getOrDefault(token, Long.MIN_VALUE / 2) < interval) return;
        if (last.size() > 2048) last.clear();
        last.put(token, now);
        plugin.getLogger().info("[" + category + "] " + message.get());
    }
    public void failure(String category, String message, Throwable error) {
        if (plugin.settings().debug().enabled()) plugin.getLogger().log(Level.WARNING, "[" + category + "] " + message, error);
        else plugin.getLogger().warning("[" + category + "] " + message + ": " + error.getClass().getSimpleName() + ": " + error.getMessage());
    }
}

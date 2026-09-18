package com.blanoir.accessory.module.skill;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Reserves before casting to block reentrant damage/skill chains, including zero-cooldown skills. */
public final class SkillCooldowns {
    private final Map<UUID, Map<String, Long>> ready = new HashMap<>();
    private final Set<UUID> casting = new HashSet<>();
    public synchronized long remaining(UUID owner, String key, long tick) { return Math.max(0, ready.getOrDefault(owner, Map.of()).getOrDefault(key, 0L) - tick); }
    public synchronized boolean cast(UUID owner, String key, int seconds, long tick, BooleanSupplier action) {
        if (remaining(owner, key, tick) > 0 || !casting.add(owner)) return false;
        try {
            if (!action.getAsBoolean()) return false;
            if (seconds > 0) ready.computeIfAbsent(owner, ignored -> new HashMap<>()).put(key, tick + seconds * 20L);
            return true;
        } finally { casting.remove(owner); }
    }
    public synchronized void clear(UUID owner) { ready.remove(owner); }
    public synchronized void clear() { ready.clear(); }
    public synchronized void expire(long tick) {
        ready.values().forEach(values -> values.values().removeIf(deadline -> deadline <= tick));
        ready.values().removeIf(Map::isEmpty);
    }
}

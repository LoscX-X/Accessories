package com.blanoir.accessory.module.inventory.service;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.api.*;
import com.blanoir.accessory.events.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.CompletionStage;

/** Inventory commands and global slot state; windows, profiles and persistence have their own owners. */
public final class DefaultAccessoryService implements AccessoryService {
    private final Accessory plugin;
    private final Set<Integer> disabled = new LinkedHashSet<>();
    private boolean changingSlots;
    public DefaultAccessoryService(Accessory plugin) { this.plugin = plugin; }
    @Override public CompletionStage<ItemStack[]> read(UUID owner) {
        return plugin.inventoryStore().readAsync(Objects.requireNonNull(owner), plugin.totalAccessoryStorageSize());
    }
    @Override public void open(Player viewer, Player owner, AccessoryViewMode mode) { plugin.menus().openFor(viewer, owner, mode); }
    @Override public void setBlocked(UUID owner, AccessoryAction action, boolean blocked) { plugin.profiles().setBlocked(owner, action, blocked); }
    @Override public boolean isAllowed(Player owner, AccessoryAction action) { return plugin.profiles().isAllowed(owner, action); }
    @Override public List<Integer> getDisabledSlots() { requireMainThread(); return List.copyOf(disabled); }
    @Override public boolean isSlotDisabled(int slot) { requireMainThread(); return disabled.contains(slot); }
    @Override public void setSlotEnabled(int slot, boolean enabled) {
        requireMainThread();
        if (slot < 0 || slot >= 54) throw new IllegalArgumentException("Slot must be 0..53");
        Set<Integer> next = new LinkedHashSet<>(disabled);
        if (enabled) next.remove(slot); else next.add(slot);
        setDisabledSlots(next);
    }
    @Override public void setDisabledSlots(Collection<Integer> slots) {
        requireMainThread();
        if (changingSlots) throw new IllegalStateException("Cannot change slots from inside a slot state event");
        Set<Integer> next = new LinkedHashSet<>(Objects.requireNonNull(slots));
        for (Integer slot : next) if (slot == null || slot < 0 || slot >= 54) throw new IllegalArgumentException("Slot must be 0..53");
        if (next.equals(disabled)) return;
        changingSlots = true;
        try {
            Set<Integer> changed = new LinkedHashSet<>(disabled); changed.addAll(next);
            for (int slot : changed) {
                if (disabled.contains(slot) == next.contains(slot)) continue;
                var event = new AccessorySlotStateChangeEvent(slot, !next.contains(slot));
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) return;
            }
            plugin.menus().closeAll(); // Commit any outstanding GUI transfers before changing equipment eligibility.
            disabled.clear(); disabled.addAll(next);
            for (Player player : Bukkit.getOnlinePlayers()) plugin.lifecycle().load(player, AccessoryChangeCause.SLOT_STATE);
        } finally { changingSlots = false; }
    }
    @Override public boolean clear(UUID owner) { return clear(owner, null, AccessoryChangeCause.CLEAR); }
    @Override public boolean clear(UUID owner, Player actor, AccessoryChangeCause cause) {
        requireMainThread();
        if (owner == null) return false;
        plugin.menus().closeForOwner(owner);
        plugin.lifecycle().invalidateLoad(owner);
        plugin.inventoryStore().clear(owner, plugin.totalAccessoryStorageSize());
        Player online = Bukkit.getPlayer(owner);
        if (online != null) plugin.lifecycle().refresh(online, new ItemStack[0], actor, cause);
        return true; // Accepted in memory; persistence errors remain observable and retryable.
    }
    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Accessory inventory operations require the server thread");
    }
}

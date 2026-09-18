package com.blanoir.accessory.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Before a GLOBAL local-slot enable/disable change. No economy or per-player unlock policy is implied. */
public final class AccessorySlotStateChangeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final int slot;
    private final boolean enabled;
    private boolean cancelled;
    public AccessorySlotStateChangeEvent(int slot, boolean enabled) { this.slot = slot; this.enabled = enabled; }
    public int getSlot() { return slot; }
    public boolean isEnabled() { return enabled; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

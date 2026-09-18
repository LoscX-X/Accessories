package com.blanoir.accessory.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class AccessoryPlaceEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final java.util.UUID ownerId;
    private final int page;
    private final AccessoryChangeCause cause;
    private final int slot;
    private final ItemStack item;
    private final ItemStack replaced;
    private boolean cancelled;

    public AccessoryPlaceEvent(Player player, java.util.UUID ownerId, int page, int slot,
                               ItemStack item, ItemStack replaced, AccessoryChangeCause cause) {
        this.player = player;
        this.ownerId = ownerId;
        this.page = page;
        this.cause = cause;
        this.slot = slot;
        this.item = item.clone();
        this.replaced = replaced == null ? null : replaced.clone();
    }

    public java.util.UUID getOwnerId() { return ownerId; }
    public int getPage() { return page; }
    public AccessoryChangeCause getCause() { return cause; }

    public Player getPlayer() {
        return player;
    }

    public int getSlot() {
        return slot;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public ItemStack getReplaced() {
        return replaced == null ? null : replaced.clone();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

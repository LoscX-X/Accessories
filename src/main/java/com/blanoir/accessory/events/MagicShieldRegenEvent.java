package com.blanoir.accessory.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class MagicShieldRegenEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final double currentShield;
    private final double maxShield;
    private boolean cancelled;
    private double amount;

    public MagicShieldRegenEvent(Player player, double currentShield, double maxShield, double amount) {
        this.player = player;
        this.currentShield = currentShield;
        this.maxShield = maxShield;
        this.amount = amount;
    }

    public Player getPlayer() {
        return player;
    }

    public double getCurrentShield() {
        return currentShield;
    }

    public double getMaxShield() {
        return maxShield;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
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
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

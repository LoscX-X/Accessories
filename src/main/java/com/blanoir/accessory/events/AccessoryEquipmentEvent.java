package com.blanoir.accessory.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Post-change notification. Cancel placement through AccessoryPlaceEvent before the transfer. */
public abstract class AccessoryEquipmentEvent extends Event {
    private final Player owner;
    private final Player actor;
    private final int page;
    private final int slot;
    private final ItemStack item;
    private final ItemStack counterpart;
    private final AccessoryChangeCause cause;

    protected AccessoryEquipmentEvent(Player owner, @Nullable Player actor, int page, int slot,
                                      ItemStack item, @Nullable ItemStack counterpart, AccessoryChangeCause cause) {
        this.owner = owner;
        this.actor = actor;
        this.page = page;
        this.slot = slot;
        this.item = item.clone();
        this.counterpart = counterpart == null ? null : counterpart.clone();
        this.cause = cause;
    }

    public Player getOwner() { return owner; }
    public @Nullable Player getActor() { return actor; }
    public int getPage() { return page; }
    public int getSlot() { return slot; }
    public ItemStack getItem() { return item.clone(); }
    /** Previous item for equip; replacement item for unequip. Null for a one-sided change. */
    public @Nullable ItemStack getCounterpart() { return counterpart == null ? null : counterpart.clone(); }
    public AccessoryChangeCause getCause() { return cause; }
}

package com.blanoir.accessory.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AccessoryEquipEvent extends AccessoryEquipmentEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    public AccessoryEquipEvent(Player owner, @Nullable Player actor, int page, int slot, ItemStack item,
                               @Nullable ItemStack previous, AccessoryChangeCause cause) {
        super(owner, actor, page, slot, item, previous, cause);
    }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

package com.blanoir.accessory.events;

import com.blanoir.accessory.config.AccessoryDeathPolicy;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/** Fired for a non-cancelled player death before applying the accessory death policy. */
public final class AccessoryDeathEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final ItemStack[] contents;
    private AccessoryDeathPolicy policy;

    public AccessoryDeathEvent(Player player, ItemStack[] contents, AccessoryDeathPolicy policy) {
        this.player = player;
        this.contents = copy(contents);
        this.policy = Objects.requireNonNull(policy);
    }
    public Player getPlayer() { return player; }
    public ItemStack[] getContents() { return copy(contents); }
    public AccessoryDeathPolicy getPolicy() { return policy; }
    public void setPolicy(AccessoryDeathPolicy policy) { this.policy = Objects.requireNonNull(policy); }
    private static ItemStack[] copy(ItemStack[] source) {
        return Arrays.stream(source).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
    }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

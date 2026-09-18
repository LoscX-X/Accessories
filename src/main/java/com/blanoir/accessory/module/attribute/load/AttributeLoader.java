package com.blanoir.accessory.module.attribute.load;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** One attribute system. All methods run on the server thread. */
public interface AttributeLoader {
    void rebuild(Player player, ItemStack[] activeContents);
    default boolean isReady(Player player) { return true; }
    default void clear(Player player) { rebuild(player, new ItemStack[0]); }
}

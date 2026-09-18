package com.blanoir.accessory.api;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
/** Server-thread transfer API; implementation is owned by the inventory module. */
public interface AccessoryQuickEquipService {
    boolean tryEquipMainHand(Player player);
    boolean equipToSlot(Player player, int page, int slot);
}

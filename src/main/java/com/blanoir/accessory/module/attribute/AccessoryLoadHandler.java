package com.blanoir.accessory.module.attribute;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

interface AccessoryLoadHandler {
    void rebuildFromInventory(Player player, Inventory inventory);

    void rebuildFromContents(Player player, ItemStack[] contents);
}

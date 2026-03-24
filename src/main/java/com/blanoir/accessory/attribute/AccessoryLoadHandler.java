package com.blanoir.accessory.attribute;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

interface AccessoryLoadHandler {
    void rebuildFromInventory(Player player, Inventory inventory);
}

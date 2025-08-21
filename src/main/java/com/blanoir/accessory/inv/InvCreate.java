package com.blanoir.accessory.inv;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

public class InvCreate implements InventoryHolder {
    private final Inventory inventory;
    public InvCreate(JavaPlugin plugin) {
        this.inventory = Bukkit.createInventory(this, 9);
    }
    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

}

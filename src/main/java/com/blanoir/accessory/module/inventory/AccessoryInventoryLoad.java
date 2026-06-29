package com.blanoir.accessory.module.inventory;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.module.inventory.ui.AccessoryInventoryMenu;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class AccessoryInventoryLoad {
    private final Accessory plugin;
    private final AccessoryInventoryMenu menu;

    public AccessoryInventoryLoad(Accessory plugin) {
        this.plugin = plugin;
        this.menu = new AccessoryInventoryMenu(plugin);
    }

    public void openFor(Player player) {
        openFor(player, player);
    }

    public void openFor(Player viewer, Player target) {
        int page = 1;
        int totalPages = plugin.accessoryPages();
        int size = plugin.accessorySize(page);

        plugin.inventoryStore().getSliceOrLoadAsync(
                target.getUniqueId(),
                plugin.accessoryPageStart(page),
                size,
                plugin.totalAccessoryStorageSize(),
                cached -> {
                    var service = plugin.service();

                    Inventory inventory = menu.create(
                            target.getUniqueId(),
                            page,
                            totalPages,
                            cached,
                            service != null ? service.getDisabledSlots() : null
                    );

                    viewer.openInventory(inventory);
                }
        );
    }
}
package com.blanoir.accessory.inventory;

import com.blanoir.accessory.Accessory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InvLoad {
    private final Accessory plugin;

    public InvLoad(Accessory plugin) {
        this.plugin = plugin;
    }

    public void openFor(Player player) {
        openFor(player, player);
    }

    public void openFor(Player viewer, Player target) {
        int totalPages = plugin.accessoryPages();
        InvCreate holder = new InvCreate(plugin, target.getUniqueId(), 1, totalPages);
        Inventory inventory = holder.getInventory();

        ItemStack[] cached = plugin.inventoryStore().getPageOrLoad(
                target.getUniqueId(),
                holder.currentPage(),
                inventory.getSize(),
                totalPages
        );
        inventory.setContents(cached);

        var service = plugin.service();
        holder.decorate(service != null ? service.getDisabledSlots() : null);
        viewer.openInventory(inventory);
    }
}

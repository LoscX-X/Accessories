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
        InvCreate holder = new InvCreate(plugin, target.getUniqueId());
        Inventory inventory = holder.getInventory();

        ItemStack[] cached = plugin.inventoryStore().getOrLoad(target.getUniqueId(), inventory.getSize());
        inventory.setContents(cached);

        holder.applyFrames();
        var service = plugin.service();
        if (service != null) {
            holder.applyDisabledSlots(service.getDisabledSlots());
        }

        viewer.openInventory(inventory);
    }
}

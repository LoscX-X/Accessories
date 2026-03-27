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
        InvCreate holder = new InvCreate(plugin);
        Inventory inventory = holder.getInventory();

        ItemStack[] cached = plugin.inventoryStore().getOrLoad(player.getUniqueId(), inventory.getSize());
        inventory.setContents(cached);

        holder.applyFrames();
        var service = plugin.service();
        if (service != null) {
            holder.applyDisabledSlots(service.getDisabledSlots());
        }

        player.openInventory(inventory);
    }
}

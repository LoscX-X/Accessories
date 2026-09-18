package com.blanoir.accessory.module.inventory;

import com.blanoir.accessory.Accessory;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryCloseEvent;

/** Window lifecycle adapter; player lifecycle is owned by AccessoryPlayerListener. */
public final class AccessoryInventoryLifecycleListener implements Listener {
    private final Accessory plugin;
    public AccessoryInventoryLifecycleListener(Accessory plugin) { this.plugin = plugin; }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        plugin.menus().commit(event.getPlayer() instanceof Player player ? player : null, event.getView().getTopInventory());
    }
}

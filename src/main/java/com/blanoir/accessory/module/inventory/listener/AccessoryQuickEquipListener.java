package com.blanoir.accessory.module.inventory.listener;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.api.AccessoryQuickEquipService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class AccessoryQuickEquipListener implements Listener {
    private final Accessory plugin;
    private final AccessoryQuickEquipService quickEquip;

    public AccessoryQuickEquipListener(Accessory plugin, AccessoryQuickEquipService quickEquip) {
        this.plugin = plugin;
        this.quickEquip = quickEquip;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryRightClick(InventoryClickEvent event) {
        if (!plugin.getConfig().getBoolean("quick-equip.shift-right-click", true)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != player.getInventory()) {
            return;
        }
        ClickType click = event.getClick();
        if (click != ClickType.SHIFT_RIGHT && !(click.isRightClick() && player.isSneaking())) {
            return;
        }

        event.setCancelled(quickEquip.tryEquipMainHand(player));
    }
}

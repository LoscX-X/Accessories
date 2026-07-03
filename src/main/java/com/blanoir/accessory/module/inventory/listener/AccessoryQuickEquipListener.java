package com.blanoir.accessory.module.inventory.listener;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.api.AccessoryQuickEquipService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class AccessoryQuickEquipListener implements Listener {
    private final Accessory plugin;
    private final AccessoryQuickEquipService quickEquip;

    public AccessoryQuickEquipListener(Accessory plugin, AccessoryQuickEquipService quickEquip) {
        this.plugin = plugin;
        this.quickEquip = quickEquip;
    }

    @EventHandler(ignoreCancelled = true)
    public void onShiftRightClick(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("quick-equip.shift-right-click", true)) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        quickEquip.tryEquipMainHand(player);
    }
}

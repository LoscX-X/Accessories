package com.blanoir.accessory.module.inventory.listener;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.api.AccessoryQuickEquipService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class AccessoryQuickEquipListener implements Listener {

    private final Accessory plugin;
    private final AccessoryQuickEquipService quickEquip;

    public AccessoryQuickEquipListener(Accessory plugin, AccessoryQuickEquipService quickEquip) {
        this.plugin = plugin;
        this.quickEquip = quickEquip;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSneakRightClick(PlayerInteractEvent event) {
        if (!isQuickEquipEnabled()) {
            return;
        }

        // 防止主手、副手触发两次
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();

        // 右键空气 / 右键方块都允许
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.isSneaking()) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        boolean equipped = quickEquip.tryEquipMainHand(player);

        if (equipped) {
            event.setCancelled(true);
        }
    }

    private boolean isQuickEquipEnabled() {
        if (plugin.getConfig().contains("quick-equip.shift-right-click")) {
            return plugin.getConfig().getBoolean("quick-equip.shift-right-click");
        }
        return plugin.getConfig().getBoolean("quick-equip.sneak-right-click", true);
    }
}

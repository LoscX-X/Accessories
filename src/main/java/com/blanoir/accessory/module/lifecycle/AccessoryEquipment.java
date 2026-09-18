package com.blanoir.accessory.module.lifecycle;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.utils.LoreUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** One eligibility rule for attributes, tags and skills. Never changes stored items. */
final class AccessoryEquipment {
    private final Accessory plugin;
    AccessoryEquipment(Accessory plugin) { this.plugin = plugin; }

    ItemStack[] activeContents(Player owner, ItemStack[] stored) {
        ItemStack[] active = new ItemStack[stored.length];
        var pages = plugin.pageManager(owner);
        for (int index = 0; index < stored.length; index++) {
            ItemStack item = stored[index];
            if (item == null || item.getType().isAir()) continue;
            int page = index / 54 + 1, slot = index % 54;
            if (page < 1 || !plugin.profiles().isSlotAvailable(owner, page, slot)) continue;
            if (pages.frameSlots(page, pages.pageSize(page)).contains(slot)) continue;
            String permission = pages.requiredPermission(page, slot);
            if (permission != null && !owner.hasPermission(permission)) continue;
            if (!LoreUtils.matchesAnyKeyword(LoreUtils.plainLore(item), pages.requiredLore(page, slot))) continue;
            active[index] = item.clone();
        }
        // Disabled/invalid slots must not consume the limit of an otherwise valid slot.
        var allowed = plugin.limitManager().allowedSlots(active);
        for (int index = 0; index < active.length; index++) if (!allowed.contains(index)) active[index] = null;
        return active;
    }
}

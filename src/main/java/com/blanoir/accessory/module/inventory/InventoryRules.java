package com.blanoir.accessory.module.inventory;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.api.AccessoryAction;
import com.blanoir.accessory.module.inventory.ui.*;
import com.blanoir.accessory.utils.LoreUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import java.util.*;

/** Owner rules are shared by manual transfers and quick equip. A drag is validated as one candidate. */
public final class InventoryRules {
    private final Accessory plugin;
    public InventoryRules(Accessory plugin) { this.plugin = plugin; }
    public boolean mayRemove(Player owner, ItemStack item) {
        return empty(item) || plugin.profiles().isAllowed(owner, AccessoryAction.UNEQUIP)
                && (plugin.antiUnequipLoreTags().isEmpty() || !LoreUtils.matchesAnyKeyword(LoreUtils.plainLore(item), plugin.antiUnequipLoreTags()));
    }
    public boolean mayPlace(Player owner, int page, int slot, ItemStack item) {
        var pages = plugin.pageManager(owner);
        if (!plugin.profiles().isAllowed(owner, AccessoryAction.EQUIP) || !plugin.profiles().isSlotAvailable(owner, page, slot)) return false;
        if (pages.frameSlots(page, pages.pageSize(page)).contains(slot)) return false;
        String permission = pages.requiredPermission(page, slot);
        return (permission == null || owner.hasPermission(permission))
                && LoreUtils.matchesAnyKeyword(LoreUtils.plainLore(item), pages.requiredLore(page, slot));
    }
    public boolean candidateAllowed(Player owner, ItemStack[] full, Map<Integer, ItemStack> replacements) {
        ItemStack[] next = full.clone();
        replacements.forEach((slot, item) -> next[slot] = item);
        // Hidden profile slots do not count towards equipped limits.
        var pages = plugin.pageManager(owner);
        for (int i = 0; i < next.length; i++) {
            int page = i / 54 + 1, slot = i % 54;
            if (!plugin.profiles().isSlotAvailable(owner, page, slot)
                    || page > pages.pageCount() || slot >= pages.pageSize(page)) next[i] = null;
        }
        for (var change : replacements.entrySet())
            if (plugin.limitManager().wouldExceedLimit(next, change.getKey(), change.getValue())) return false;
        return true;
    }
    public static boolean empty(ItemStack item) { return item == null || item.getType().isAir(); }
}

package com.blanoir.accessory.module.inventory.service;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.api.AccessoryQuickEquipService;
import com.blanoir.accessory.events.*;
import com.blanoir.accessory.module.inventory.InventoryRules;
import net.kyori.adventure.text.event.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** One-item main-hand transfer with a shared owner policy and reentrancy protection. */
public final class DefaultQuickEquipService implements AccessoryQuickEquipService {
    private record Target(int page, int slot) { }
    private final Accessory plugin;
    private final InventoryRules rules;
    private final Set<UUID> transfers = new HashSet<>();
    public DefaultQuickEquipService(Accessory plugin) { this.plugin = plugin; rules = new InventoryRules(plugin); }
    @Override public boolean tryEquipMainHand(Player player) {
        requireMainThread();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (InventoryRules.empty(item)) return false;
        List<Target> targets = new ArrayList<>();
        var pages = plugin.pageManager(player);
        for (int page = 1; page <= pages.pageCount(); page++)
            for (int slot : pages.configuredSlots(page))
                if (rules.mayPlace(player, page, slot, item)) targets.add(new Target(page, slot));
        if (targets.isEmpty()) return false;
        if (targets.size() == 1) return equipToSlot(player, targets.getFirst().page(), targets.getFirst().slot());
        player.sendMessage(plugin.lang().langComponent("Quick_equip_select_header"));
        for (Target target : targets) player.sendMessage(plugin.lang().langComponent("Quick_equip_select_option",
                Map.of("page", String.valueOf(target.page()), "slot", String.valueOf(target.slot())))
                .clickEvent(ClickEvent.runCommand("/accessory quickequip " + target.page() + " " + target.slot()))
                .hoverEvent(HoverEvent.showText(plugin.lang().langComponent("Quick_equip_select_hover"))));
        return true;
    }
    @Override public boolean equipToSlot(Player player, int page, int slot) {
        requireMainThread();
        if (!transfers.add(player.getUniqueId())) return false;
        try {
            plugin.menus().closeForOwner(player.getUniqueId());
            var pages = plugin.pageManager(player);
            if (page < 1 || page > pages.pageCount() || slot < 0 || slot >= pages.pageSize(page)) return false;
            ItemStack hand = player.getInventory().getItemInMainHand().clone();
            if (InventoryRules.empty(hand) || !rules.mayPlace(player, page, slot, hand)) return false;
            ItemStack[] contents = plugin.inventoryStore().getOrLoad(player.getUniqueId(), plugin.totalAccessoryStorageSize());
            int index = plugin.accessoryPageStart(page) + slot;
            ItemStack old = contents[index], placed = hand.clone();
            placed.setAmount(1); plugin.skillSystem().stamp(placed);
            if (!rules.mayRemove(player, old)) { player.sendMessage(plugin.lang().langComponent("Item_cannot_unequip")); return false; }
            if (!rules.candidateAllowed(player, contents, Map.of(index, placed))) {
                player.sendMessage(plugin.lang().langComponent("Item_limit_reached")); return false;
            }
            var event = new AccessoryPlaceEvent(player, player.getUniqueId(), page, slot, placed, old, AccessoryChangeCause.QUICK_EQUIP);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || !hand.equals(player.getInventory().getItemInMainHand())
                    || pages != plugin.pageManager(player) || !rules.mayPlace(player, page, slot, placed)
                    || !rules.mayRemove(player, old) || !Arrays.equals(contents, plugin.inventoryStore().getOrLoad(player.getUniqueId(), plugin.totalAccessoryStorageSize())))
                return false;
            contents[index] = placed;
            plugin.inventoryStore().update(player.getUniqueId(), contents, plugin.totalAccessoryStorageSize());
            if (hand.getAmount() == 1) player.getInventory().setItemInMainHand(old == null ? null : old.clone());
            else {
                hand.setAmount(hand.getAmount() - 1); player.getInventory().setItemInMainHand(hand);
                if (!InventoryRules.empty(old)) player.getInventory().addItem(old.clone()).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
            plugin.inventoryStore().flush(player.getUniqueId(), plugin.totalAccessoryStorageSize());
            plugin.lifecycle().refresh(player, contents, player, AccessoryChangeCause.QUICK_EQUIP);
            player.sendActionBar(plugin.lang().langComponent("Accessory_equipped", Map.of("page", "" + page, "slot", "" + slot)));
            return true;
        } finally { transfers.remove(player.getUniqueId()); }
    }
    private static void requireMainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Quick equip requires the server thread"); }
}

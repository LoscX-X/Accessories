package com.blanoir.accessory.api;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.events.AccessoryPlaceEvent;
import com.blanoir.accessory.module.attribute.loader.AccessoryLoad;
import com.blanoir.accessory.utils.LoreUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AccessoryQuickEquipService {
    private final Accessory plugin;
    private final AccessoryLoad accessoryLoad;

    public AccessoryQuickEquipService(Accessory plugin) {
        this.plugin = plugin;
        this.accessoryLoad = new AccessoryLoad(plugin);
    }

    public boolean tryEquipMainHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return false;

        List<TargetSlot> targets = findAccessoryTargets(hand);
        if (targets.isEmpty()) return false;

        if (targets.size() == 1) {
            equipToSlot(player, targets.getFirst().page(), targets.getFirst().slot());
            return true;
        }

        sendTargetSelection(player, targets);
        return true;
    }

    public void equipToSlot(Player player, int slot) {
        equipToSlot(player, 1, slot);
    }

    public void equipToSlot(Player player, int page, int slot) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isAir(hand)) return;
        equipItem(player, page, slot, hand, old -> replaceMainHandAfterEquip(player, hand, old));
    }


    private void equipItem(Player player, int page, int slot, ItemStack source, java.util.function.Consumer<ItemStack> oldItemHandler) {
        int pageSize = plugin.accessorySize(page);
        int pages = plugin.accessoryPages();
        if (page < 1 || page > pages || slot < 0 || slot >= pageSize) return;
        if (plugin.service() != null && plugin.service().isSlotDisabled(slot)) return;
        if (isFrameSlot(page, slot)) return;
        if (shouldRejectPlacement(player, page, slot, source)) return;

        ItemStack[] contents = plugin.inventoryStore().getOrLoad(player.getUniqueId(), plugin.totalAccessoryStorageSize());
        int absoluteSlot = plugin.accessoryPageStart(page) + slot;
        ItemStack old = contents[absoluteSlot];

        ItemStack placed = source.clone();
        placed.setAmount(1);
        if (plugin.skillEngine() != null) {
            plugin.skillEngine().stampKnownAccessoryItem(placed);
        }

        AccessoryPlaceEvent placeEvent = new AccessoryPlaceEvent(
                player,
                slot,
                placed.clone(),
                old == null ? null : old.clone()
        );
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) return;

        contents[absoluteSlot] = placed;
        oldItemHandler.accept(old);

        plugin.inventoryStore().update(player.getUniqueId(), contents, plugin.totalAccessoryStorageSize());
        plugin.inventoryStore().flush(player.getUniqueId(), plugin.totalAccessoryStorageSize());

        accessoryLoad.rebuildFromContents(player, contents);
        if (plugin.skillEngine() != null) {
            plugin.skillEngine().refreshPlayer(player, contents);
        }

        player.sendActionBar(plugin.lang().langComponent(
                "Accessory_equipped",
                Map.of("page", String.valueOf(page), "slot", String.valueOf(slot))
        ));
    }

    public int findAccessorySlot(ItemStack item) {
        List<TargetSlot> targets = findAccessoryTargets(item);
        return targets.isEmpty() ? -1 : targets.getFirst().slot();
    }

    private List<TargetSlot> findAccessoryTargets(ItemStack item) {
        if (plugin.skillEngine() != null) {
            plugin.skillEngine().stampKnownAccessoryItem(item);
        }
        List<String> lore = LoreUtils.plainLore(item);
        if (lore.isEmpty()) return List.of();

        List<TargetSlot> targets = new ArrayList<>();
        int pages = plugin.accessoryPages();
        for (int page = 1; page <= pages; page++) {
            ConfigurationSection pageSec = plugin.pageManager().pageAccessorySection(page);
            if (pageSec == null) continue;
            collectMatchingSlots(lore, page, pageSec, targets);
        }

        ConfigurationSection legacySec = plugin.pageManager().legacyAccessorySection();
        if (legacySec != null) {
            collectMatchingSlots(lore, 1, legacySec, targets);
        }
        return targets;
    }

    private void collectMatchingSlots(List<String> lore, int page, ConfigurationSection section, List<TargetSlot> targets) {
        int pageSize = plugin.accessorySize(page);
        for (String key : section.getKeys(false)) {
            if (key.startsWith("page_")) continue;

            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (slot < 0 || slot >= pageSize) continue;
            if (plugin.service() != null && plugin.service().isSlotDisabled(slot)) continue;
            if (isFrameSlot(page, slot)) continue;

            List<String> need = plugin.pageManager().requiredLore(page, slot);
            if (!need.isEmpty() && LoreUtils.matchesAnyKeyword(lore, need)) {
                TargetSlot target = new TargetSlot(page, slot);
                if (!targets.contains(target)) targets.add(target);
            }
        }
    }

    private boolean shouldRejectPlacement(Player player, int page, int slot, ItemStack item) {
        String permission = plugin.pageManager().requiredPermission(page, slot);
        if (permission != null && !player.hasPermission(permission)) {
            player.sendMessage(plugin.lang().langComponent("Slot_no_permission"));
            return true;
        }

        List<String> need = plugin.pageManager().requiredLore(page, slot);
        if (!LoreUtils.matchesAnyKeyword(LoreUtils.plainLore(item), need)) {
            player.sendMessage(plugin.lang().langComponent("Item_not_match"));
            return true;
        }
        return false;
    }

    private void replaceMainHandAfterEquip(Player player, ItemStack hand, ItemStack old) {
        replaceInventoryItemAfterEquip(player, hand, old, item -> player.getInventory().setItemInMainHand(item));
    }


    private void replaceInventoryItemAfterEquip(Player player, ItemStack source, ItemStack old, java.util.function.Consumer<ItemStack> sourceSetter) {
        if (source.getAmount() <= 1) {
            sourceSetter.accept(isAir(old) ? null : old.clone());
            return;
        }

        ItemStack remaining = source.clone();
        remaining.setAmount(source.getAmount() - 1);
        sourceSetter.accept(remaining);

        if (!isAir(old)) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(old.clone());
            left.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
        }
    }

    private void sendTargetSelection(Player player, List<TargetSlot> targets) {
        player.sendMessage(plugin.lang().langComponent("Quick_equip_select_header"));
        for (TargetSlot target : targets) {
            Component option = plugin.lang().langComponent(
                    "Quick_equip_select_option",
                    Map.of("page", String.valueOf(target.page()), "slot", String.valueOf(target.slot()))
            ).clickEvent(ClickEvent.runCommand("/accessory quickequip " + target.page() + " " + target.slot()))
                    .hoverEvent(HoverEvent.showText(plugin.lang().langComponent("Quick_equip_select_hover")));
            player.sendMessage(option);
        }
    }

    private boolean isAir(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private boolean isAir(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private boolean isFrameSlot(int page, int slot) {
        return plugin.pageManager().frameSlots(page, plugin.accessorySize(page)).contains(slot);
    }

    private record TargetSlot(int page, int slot) {
    }
}

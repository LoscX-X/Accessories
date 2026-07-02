package com.blanoir.accessory.api;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.events.AccessoryPlaceEvent;
import com.blanoir.accessory.module.attribute.loader.AccessoryLoad;
import com.blanoir.accessory.utils.LoreUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class AccessoryQuickEquipService {    //这个可以不用管
    private final Accessory plugin;
    private final AccessoryLoad accessoryLoad;

    public AccessoryQuickEquipService(Accessory plugin) {
        this.plugin = plugin;
        this.accessoryLoad = new AccessoryLoad(plugin);
    }

    /**
     * 给 KeyEvent / 右键 调用：尝试把主手物品穿戴到饰品槽
     */
    public void tryEquipMainHand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return;

        TargetSlot target = findAccessoryTarget(hand);
        if (target == null) return;

        equipToSlot(p, target.page(), target.slot());
    }

    /**
     * 兼容旧 API：把主手物品 equip 到第 1 页指定 slot（会替换旧的）
     */
    public void equipToSlot(Player p, int slot) {
        equipToSlot(p, 1, slot);
    }

    /**
     * 核心：把主手物品 equip 到指定 page/slot（会替换旧的）
     */
    public void equipToSlot(Player p, int page, int slot) {
        int pageSize = plugin.accessorySize(page);
        int pages = plugin.accessoryPages();
        if (page < 1 || page > pages || slot < 0 || slot >= pageSize) return;
        if (plugin.service() != null && plugin.service().isSlotDisabled(slot)) return;

        // 防止把饰品塞进外框锁定槽（如果 frame 槽位和 accessory 槽位配置冲突）
        if (isFrameSlot(page, slot)) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return;

        ItemStack[] contents = plugin.inventoryStore().getOrLoad(p.getUniqueId(), plugin.totalAccessoryStorageSize());
        int absoluteSlot = plugin.accessoryPageStart(page) + slot;
        ItemStack old = contents[absoluteSlot];

        ItemStack placed = hand.clone();
        placed.setAmount(1);
        if (plugin.skillEngine() != null) {
            plugin.skillEngine().stampKnownAccessoryItem(placed);
        }

        AccessoryPlaceEvent placeEvent = new AccessoryPlaceEvent(
                p,
                slot,
                placed.clone(),
                old == null ? null : old.clone()
        );
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            return;
        }

        contents[absoluteSlot] = placed;
        decrementMainHand(p);

        if (old != null && old.getType() != Material.AIR) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(old);
            left.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
        }

        plugin.inventoryStore().update(p.getUniqueId(), contents, plugin.totalAccessoryStorageSize());
        plugin.inventoryStore().flush(p.getUniqueId(), plugin.totalAccessoryStorageSize());

        accessoryLoad.rebuildFromContents(p, contents);
        if (plugin.skillEngine() != null) {
            plugin.skillEngine().refreshPlayer(p, contents);
        }

        p.sendActionBar(plugin.lang().langComponent("Accessory_equipped"));

    }

    /** lore -> 找到目标槽位：优先 page/*.yml 中 page 对应的 Accessory.<slot>.lore，再兼容旧版配置。 */
    public int findAccessorySlot(ItemStack item) {
        TargetSlot target = findAccessoryTarget(item);
        return target == null ? -1 : target.slot();
    }

    private TargetSlot findAccessoryTarget(ItemStack item) {
        if (plugin.skillEngine() != null) {
            plugin.skillEngine().stampKnownAccessoryItem(item);
        }
        List<String> lore = LoreUtils.plainLore(item);
        if (lore.isEmpty()) return null;

        int pages = plugin.accessoryPages();
        for (int page = 1; page <= pages; page++) {
            ConfigurationSection pageSec = plugin.pageManager().pageAccessorySection(page);
            if (pageSec == null) continue;

            TargetSlot target = findMatchingSlotInSection(lore, page, pageSec);
            if (target != null) return target;
        }

        ConfigurationSection legacySec = plugin.pageManager().legacyAccessorySection();
        return legacySec == null ? null : findMatchingSlotInSection(lore, 1, legacySec);
    }

    private TargetSlot findMatchingSlotInSection(List<String> lore, int page, ConfigurationSection section) {
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
            if (need.isEmpty()) continue;

            if (LoreUtils.matchesAnyKeyword(lore, need)) return new TargetSlot(page, slot);
        }
        return null;
    }

    private void decrementMainHand(Player p) {
        ItemStack cur = p.getInventory().getItemInMainHand();
        if (cur.getType().isAir()) return;

        int amt = cur.getAmount();
        if (amt <= 1) p.getInventory().setItemInMainHand(null);
        else {
            cur.setAmount(amt - 1);
            p.getInventory().setItemInMainHand(cur);
        }
    }

    private boolean isFrameSlot(int page, int slot) {
        return plugin.pageManager().frameSlots(page, plugin.accessorySize(page)).contains(slot);
    }

    private record TargetSlot(int page, int slot) {
    }
}

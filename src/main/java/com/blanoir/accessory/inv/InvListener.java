// com.blanoir.accessory.inv.InvListener
package com.blanoir.accessory.inv;

import com.blanoir.accessory.Utils.LoreUtils;
import com.blanoir.accessory.attributeload.AccessoryLoad;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;

public class InvListener implements Listener {
    private final JavaPlugin plugin;
    private final AccessoryLoad effects;
    private final NamespacedKey LOCKED;                 // ← 不在字段处初始化
    private static final Set<Integer> FRAME = Set.of(0,2,4,6,8);


    public InvListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.effects = new AccessoryLoad();
        this.LOCKED = new NamespacedKey(plugin, "locked"); // ← 在构造器里用已赋值的 plugin
    }

    private boolean isAccessoryTop(InventoryView view) {
        return view.getTopInventory().getHolder() instanceof InvCreate;
    }
    private List<String> required(int slot) {
        return plugin.getConfig().getStringList("rules." + slot + ".lore-includes");
    }
    private boolean canPlaceInSlot(int slot, ItemStack item) {
        // 默认拒绝：只有当该槽位在 rules 里配置了关键词，并且物品 lore 命中，才允许
        var need = required(slot);
        return LoreUtils.matchesAnyKeyword(LoreUtils.plainLore(item), need);
    }

    private boolean isLocked(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        ItemMeta meta = it.getItemMeta();
        return meta != null &&
                meta.getPersistentDataContainer().has(LOCKED, PersistentDataType.BYTE);
    }

    private void scheduleRefresh(Player p, InventoryView view) {
        Inventory top = view.getTopInventory();
        Bukkit.getScheduler().runTask(plugin, () -> effects.rebuildFromInventory(p, top)); // 下一 tick
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isAccessoryTop(e.getView())) return;

        Inventory top = e.getView().getTopInventory();
        int topSize = top.getSize();
        int raw = e.getRawSlot();                 // raw < topSize 表示点到上半区。:contentReference[oaicite:3]{index=3}
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && raw >= topSize) {
            // 禁止从下半区 shift-塞到上半区，避免绕过逐格校验
            e.setCancelled(true);
            return;
        }
        if (FRAME.contains(raw) || isLocked(e.getCurrentItem())) {
            e.setCancelled(true);
            return;
        }
        if (raw < topSize) {
            switch (e.getAction()) {
                case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR,
                     HOTBAR_SWAP -> {
                    ItemStack going = switch (e.getAction()) {
                        case HOTBAR_SWAP -> p.getInventory().getItem(e.getHotbarButton());
                        default -> e.getCursor();
                    };
                    if (going != null && !going.getType().isAir() && !canPlaceInSlot(raw, going)) {
                        e.setCancelled(true);
                        p.sendMessage("§c该槽位需要特定饰品（lore 不匹配）。");
                        return;
                    }
                }
                default -> {}
            }
        }
        // 下一 tick 再重建饰品效果，避免与本次修改流程冲突
        Bukkit.getScheduler().runTask(plugin, () -> effects.rebuildFromInventory(p, top));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isAccessoryTop(e.getView())) return;

        Inventory top = e.getView().getTopInventory();
        int topSize = top.getSize();
        // 逐个检查“将要写入”的目标槽位及其物品（官方提供 getNewItems/rawSlots）:contentReference[oaicite:4]{index=4}
        for (var en : e.getNewItems().entrySet()) {
            int raw = en.getKey();
            if (raw >= topSize) continue; // 只看上半区
            if (!canPlaceInSlot(raw, en.getValue())) {
                e.setCancelled(true);
                ((Player) e.getWhoClicked()).sendMessage("§cThe accessory doesn't match the slot!");
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> effects.rebuildFromInventory(p, top));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!isAccessoryTop(e.getView())) return;

        scheduleRefresh(p, e.getView()); // 关闭时再同步一次最终状态
    }
}
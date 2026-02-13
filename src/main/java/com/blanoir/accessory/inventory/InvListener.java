// com.blanoir.accessory.inv.InvListener
package com.blanoir.accessory.inventory;

import com.blanoir.accessory.utils.LoreUtils;
import com.blanoir.accessory.attributeload.AccessoryLoad;
import com.blanoir.accessory.events.AccessoryPlaceEvent;
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
import com.blanoir.accessory.Accessory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class InvListener implements Listener {
    private final Accessory plugin;
    private final AccessoryLoad effects;
    private final NamespacedKey LOCKED;                 // ← 不在字段处初始化

    public InvListener(Accessory plugin) {
        this.plugin = plugin;
        this.effects = new AccessoryLoad(plugin);
        this.LOCKED = new NamespacedKey(plugin, "locked"); //在构造器里用已赋值的 plugin
    }

    private boolean isAccessoryTop(InventoryView view) {
        return view.getTopInventory().getHolder() instanceof InvCreate;
    }
    private boolean isSlotConfigured(int slot) {
        return plugin.getConfig().isConfigurationSection("Accessory." + slot);
    }
    private boolean isSlotDisabled(int slot) {
        return plugin.service() != null && plugin.service().isSlotDisabled(slot);
    }
    private List<String> requiredLore(int slot) {
        return plugin.getConfig().getStringList("Accessory." + slot + ".lore");
    }
    private boolean canPlaceInSlot(int slot, ItemStack item) {
        if (isSlotDisabled(slot)) return false;
        // 未配置的槽位：直接不允许放入（更安全的默认）
        if (!isSlotConfigured(slot)) return false;

        // 只做你现有的 lore 匹配；需要更多条件可在这里扩展
        List<String> need = requiredLore(slot);
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

    private boolean callPlaceEvent(Player player, Inventory top, int slot, ItemStack item) {
        AccessoryPlaceEvent event = new AccessoryPlaceEvent(
                player,
                slot,
                item.clone(),
                top.getItem(slot) == null ? null : top.getItem(slot).clone()
        );
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }
    private List<Integer> frameSlots(InventoryView view) {
        int size = view.getTopInventory().getSize();

        // 1) 读配置（为空则按空列表处理）
        List<Integer> raw = plugin.getConfig().getIntegerList("frame.slots");
        if (raw == null) raw = List.of();

        // 2) 去重且保持配置顺序（LinkedHashSet 保序）【避免重复槽位】
        LinkedHashSet<Integer> set = new LinkedHashSet<>(); // preserves insertion order
        for (Integer i : raw) {
            if (i == null) continue;
            if (i < 0 || i >= size) {
                plugin.getLogger().warning("[Accessory] frame.slots out of bounds: " + i + " (invSize=" + size + ")");
                continue; // 3) 过滤越界槽位
            }
            set.add(i);
        }

        // 4) 若清洗后为空，回退默认（并裁剪到大小内）
        if (set.isEmpty()) {
            for (int d : new int[]{0, 2, 4, 6, 8}) if (d < size) set.add(d);
        }
        return new ArrayList<>(set);
    }
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isAccessoryTop(e.getView())) return;

        Inventory top = e.getView().getTopInventory();
        int topSize = top.getSize();
        int raw = e.getRawSlot();

        // 从下半区 shift-塞到上半区：直接拦（避免跳过逐格校验）
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && raw >= topSize) {
            e.setCancelled(true);
            return;
        }

        // 计算当前 GUI 的外框槽位（从 config 读、去重/越界过滤）
        List<Integer> FRAME = frameSlots(e.getView());

        // 只处理上半区
        if (raw < topSize) {
            boolean isFrame = FRAME.contains(raw);
            boolean isDisabled = isSlotDisabled(raw);
            ItemStack cur = e.getCurrentItem();

            if (isFrame || isDisabled) {
                switch (e.getAction()) {
                    // 这些是“往格子里放”的动作 → 禁止
                    case PLACE_ALL, PLACE_SOME, PLACE_ONE,
                         SWAP_WITH_CURSOR, HOTBAR_SWAP,
                          COLLECT_TO_CURSOR,
                         MOVE_TO_OTHER_INVENTORY -> { e.setCancelled(true); return; }
                    default -> {
                        // 非“放入”动作（如 PICKUP_*）：若当前是“锁面板”，禁止拿走；否则允许把玩家物品拿出来
                        if (isLocked(cur)) { e.setCancelled(true); return; }
                    }
                }
            } else {
                // 非外框槽位：仅在“放入”时做 lore 校验；其他动作不拦
                switch (e.getAction()) {
                    case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR, HOTBAR_SWAP -> {
                        ItemStack going = (e.getAction() == InventoryAction.HOTBAR_SWAP)
                                ? p.getInventory().getItem(e.getHotbarButton())
                                : e.getCursor();
                        if (going != null && !going.getType().isAir() && !canPlaceInSlot(raw, going)) {
                            e.setCancelled(true);
                            p.sendMessage(plugin.lang().lang("Item_not_match"));
                            return;
                        }
                        if (going != null && !going.getType().isAir() && !callPlaceEvent(p, top, raw, going)) {
                            e.setCancelled(true);
                            return;
                        }
                    }
                    default -> {}
                }
            }
        }

        // 下一 tick 再重建效果，避免与本次修改冲突（官方也提示本事件阶段并非所有库存操作都安全）。:contentReference[oaicite:3]{index=3}
        Bukkit.getScheduler().runTask(plugin, () -> effects.rebuildFromInventory(p, top));
    }


    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isAccessoryTop(e.getView())) return;

        Inventory top = e.getView().getTopInventory();
        int topSize = top.getSize();
        List<Integer> FRAME = frameSlots(e.getView());

        // 先拦外框目标（getNewItems/rawSlots 都可，用 getNewItems 读“将被写入”的物品更直观）:contentReference[oaicite:4]{index=4}
        for (var en : e.getNewItems().entrySet()) {
            int raw = en.getKey();
            if (raw < topSize && FRAME.contains(raw)) {
                e.setCancelled(true);
                p.sendMessage(plugin.lang().lang("Item_locked"));
                return;
            }
            if (raw < topSize && isSlotDisabled(raw)) {
                e.setCancelled(true);
                p.sendMessage(plugin.lang().lang("Item_locked"));
                return;
            }
        }

        // 再对落在非外框的目标格做 lore 校验
        for (var en : e.getNewItems().entrySet()) {
            int raw = en.getKey();
            if (raw >= topSize) continue;
            if (!canPlaceInSlot(raw, en.getValue())) {
                e.setCancelled(true);
                p.sendMessage(plugin.lang().lang("Item_not_match"));
                return;
            }
            if (!callPlaceEvent(p, top, raw, en.getValue())) {
                e.setCancelled(true);
                return;
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> effects.rebuildFromInventory(p, top));
    }
}

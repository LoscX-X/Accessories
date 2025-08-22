// com.blanoir.accessory.inv.InvListener
package com.blanoir.accessory.attributeload;

import com.blanoir.accessory.inv.InvCreate;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;

public class InvListener implements Listener {
    private final JavaPlugin plugin;
    private final AccessoryLoad effects;

    public InvListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.effects = new AccessoryLoad(); // 里面通过 AuraSkillsBukkit 拿 ItemManager
    }

    /** 顶部背包是不是你的自定义GUI */
    private boolean isAccessoryTop(InventoryView view) {
        return view.getTopInventory().getHolder() instanceof InvCreate;
    }

    /** 下一 tick 刷新（确保物品已落位/挪走） */
    private void scheduleRefresh(Player p, InventoryView view) {
        Inventory top = view.getTopInventory();
        Bukkit.getScheduler().runTask(plugin, () -> effects.rebuildFromInventory(p, top));
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isAccessoryTop(e.getView())) return;

        // 无论点的是上半区还是下半区（含 shift、数字键），都有可能影响顶部背包
        scheduleRefresh(p, e.getView());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isAccessoryTop(e.getView())) return;

        // Drag 可能同时改多个槽；只要影响到“上半区”就刷新
        int topSize = e.getView().getTopInventory().getSize();
        boolean touchesTop = e.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        if (touchesTop) scheduleRefresh(p, e.getView());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!isAccessoryTop(e.getView())) return;

        // 关闭时也再对最终状态做一次同步（可选）
        scheduleRefresh(p, e.getView());
    }
}

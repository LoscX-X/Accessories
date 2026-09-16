package com.blanoir.accessory.module.inventory.ui;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.config.AccessoryLayout.FrameItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** All menu entry points share the same save -> resolve layout -> load -> render flow. */
public final class AccessoryMenuController {
    private final Accessory plugin;
    private final AccessoryInventoryMenu menu;
    private final Map<UUID, Object> pendingOpens = new HashMap<>();

    public AccessoryMenuController(Accessory plugin) {
        this.plugin = plugin;
        this.menu = new AccessoryInventoryMenu(plugin);
    }

    public void openFor(Player viewer, Player owner) {
        openPage(viewer, owner.getUniqueId(), 1);
    }

    private void openPage(Player viewer, UUID ownerId, int requestedPage) {
        var pages = plugin.pageManager();
        int page = pages.normalizePage(requestedPage);
        // InventoryCloseEvent persists the old view before the new page is read.
        if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof AccessoryInventoryHolder) {
            viewer.closeInventory();
        }
        Inventory previousTop = viewer.getOpenInventory().getTopInventory();
        Object request = new Object();
        pendingOpens.put(viewer.getUniqueId(), request);
        plugin.inventoryStore().getSliceOrLoadAsync(ownerId, pages.pageStart(page), pages.pageSize(page),
                pages.totalStorageSize(), contents -> {
                    if (!pendingOpens.remove(viewer.getUniqueId(), request)) return;
                    if (!viewer.isOnline() || pages != plugin.pageManager()) return;
                    if (viewer.getOpenInventory().getTopInventory() != previousTop) return;
                    viewer.openInventory(menu.create(ownerId, page, pages.pageCount(), contents,
                            plugin.service().getDisabledSlots()));
                });
    }

    public void handleFrameClick(Player player, InventoryView view, int slot) {
        if (!(view.getTopInventory().getHolder() instanceof AccessoryInventoryHolder holder)) return;
        FrameItem item = plugin.pageManager().frameItemAt(holder.currentPage(), slot, view.getTopInventory().getSize());
        if (item == null) return;
        switch (item.action()) {
            case PREVIOUS_PAGE -> changePage(player, view, holder.currentPage() - 1);
            case NEXT_PAGE -> changePage(player, view, holder.currentPage() + 1);
            case COMMAND -> {
                AccessoryCommandExecutor.execute(player, holder.currentPage(), slot, item.consoleCommands(), true);
                AccessoryCommandExecutor.execute(player, holder.currentPage(), slot, item.playerCommands(), false);
            }
            case NONE -> { }
        }
    }

    private void changePage(Player viewer, InventoryView view, int requestedPage) {
        AccessoryInventoryHolder holder = (AccessoryInventoryHolder) view.getTopInventory().getHolder();
        var pages = plugin.pageManager();
        int page = pages.normalizePage(requestedPage);
        if (page == holder.currentPage()) return;
        // Opening/closing an inventory is deferred out of InventoryClickEvent.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (pages != plugin.pageManager() || !viewer.isOnline()) return;
            if (viewer.getOpenInventory().getTopInventory() != view.getTopInventory()) return;
            openPage(viewer, holder.getOwnerId(), page);
        });
    }
}

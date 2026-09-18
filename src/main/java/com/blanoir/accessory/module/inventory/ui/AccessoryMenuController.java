package com.blanoir.accessory.module.inventory.ui;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.api.*;
import com.blanoir.accessory.events.AccessoryChangeCause;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import java.util.*;

/** Owns window requests and commits. Exactly one window per owner, including asynchronous opens. */
public final class AccessoryMenuController {
    private final Accessory plugin;
    private final AccessoryInventoryMenu menu;
    private final MenuSnapshot snapshot;
    private final MenuRequests requests = new MenuRequests();
    public AccessoryMenuController(Accessory plugin) {
        this.plugin = plugin; menu = new AccessoryInventoryMenu(plugin); snapshot = new MenuSnapshot(plugin);
    }
    public void openFor(Player viewer, Player owner) { openFor(viewer, owner, AccessoryViewMode.EDIT); }
    public void openFor(Player viewer, Player owner, AccessoryViewMode mode) { openPage(viewer, owner, 1, mode); }
    private void openPage(Player viewer, Player owner, int requested, AccessoryViewMode mode) {
        requireMainThread();
        if (!viewer.isOnline() || !owner.isOnline() || !plugin.profiles().isAllowed(owner, AccessoryAction.OPEN)) return;
        var pages = plugin.pageManager(owner);
        var profile = plugin.profiles().resolve(owner);
        int selected = pages.normalizePage(requested);
        if (!profile.allowsPage(selected)) {
            selected = java.util.stream.IntStream.rangeClosed(1, pages.pageCount()).filter(profile::allowsPage).findFirst().orElse(0);
            if (selected == 0) return;
        }
        final int page = selected;
        if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof AccessoryInventoryHolder) viewer.closeInventory();
        closeForOwner(owner.getUniqueId());
        Inventory previous = viewer.getOpenInventory().getTopInventory();
        var token = requests.begin(viewer.getUniqueId(), owner.getUniqueId());
        plugin.inventoryStore().getSliceOrLoadAsync(owner.getUniqueId(), plugin.accessoryPageStart(page), pages.pageSize(page),
                plugin.totalAccessoryStorageSize(), ignored -> {
            if (!requests.complete(token)) return;
            if (!viewer.isOnline() || Bukkit.getPlayer(owner.getUniqueId()) != owner || pages != plugin.pageManager(owner)) return;
            if (viewer.getOpenInventory().getTopInventory() != previous || !plugin.profiles().isAllowed(owner, AccessoryAction.OPEN)) return;
            ItemStack[] current = plugin.inventoryStore().getSliceOrLoad(owner.getUniqueId(), plugin.accessoryPageStart(page),
                    pages.pageSize(page), plugin.totalAccessoryStorageSize());
            viewer.openInventory(menu.create(owner.getUniqueId(), page, pages.pageCount(), current, disabled(owner, page), mode, pages));
        });
    }
    public List<Integer> disabled(Player owner, int page) {
        var pages = plugin.pageManager(owner);
        return java.util.stream.IntStream.range(0, pages.pageSize(page))
                .filter(slot -> plugin.service().isSlotDisabled(slot) || !plugin.profiles().resolve(owner).allowsSlot(page, slot)).boxed().toList();
    }
    public void commit(Player actor, Inventory inventory) {
        if (!(inventory.getHolder() instanceof AccessoryInventoryHolder holder) || holder.mode() == AccessoryViewMode.READ_ONLY) return;
        ItemStack[] clean = snapshot.read(inventory);
        plugin.inventoryStore().updateSlice(holder.getOwnerId(), plugin.accessoryPageStart(holder.currentPage()), clean,
                inventory.getSize(), plugin.totalAccessoryStorageSize());
        Player owner = Bukkit.getPlayer(holder.getOwnerId());
        if (owner != null) plugin.lifecycle().refresh(owner,
                plugin.inventoryStore().getOrLoad(holder.getOwnerId(), plugin.totalAccessoryStorageSize()), actor, AccessoryChangeCause.GUI);
    }
    public void invalidateOwner(UUID owner) { requests.cancelOwner(owner); }
    public void forgetViewer(UUID viewer) { requests.cancelViewer(viewer); }
    public void closeForOwner(UUID owner) {
        invalidateOwner(owner);
        for (Player viewer : Bukkit.getOnlinePlayers())
            if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof AccessoryInventoryHolder holder
                    && holder.getOwnerId().equals(owner)) viewer.closeInventory();
    }
    public void closeAll() {
        requests.clear();
        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AccessoryInventoryHolder) player.closeInventory();
    }
    public void handleFrameClick(Player player, InventoryView view, int slot) {
        if (!(view.getTopInventory().getHolder() instanceof AccessoryInventoryHolder holder)) return;
        var pages = holder.pages();
        var item = pages.frameItemAt(holder.currentPage(), slot, view.getTopInventory().getSize());
        if (item == null) return;
        switch (item.action()) {
            case PREVIOUS_PAGE -> changePage(player, view, -1);
            case NEXT_PAGE -> changePage(player, view, 1);
            case COMMAND -> {
                // Read-only windows must not expose configured mutation commands.
                if (holder.mode() == AccessoryViewMode.READ_ONLY) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory() != view.getTopInventory()) return;
                    AccessoryCommandExecutor.execute(player, holder.currentPage(), slot, item.consoleCommands(), true);
                    AccessoryCommandExecutor.execute(player, holder.currentPage(), slot, item.playerCommands(), false);
                });
            }
            case NONE -> { }
        }
    }
    private void changePage(Player viewer, InventoryView view, int direction) {
        var holder = (AccessoryInventoryHolder) view.getTopInventory().getHolder();
        Player owner = Bukkit.getPlayer(holder.getOwnerId());
        if (owner == null) return;
        int page = holder.currentPage() + direction;
        var profile = plugin.profiles().resolve(owner);
        while (page >= 1 && page <= holder.totalPages() && !profile.allowsPage(page)) page += direction;
        if (page < 1 || page > holder.totalPages()) return;
        int next = page;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline() && viewer.getOpenInventory().getTopInventory() == view.getTopInventory())
                openPage(viewer, owner, next, holder.mode());
        });
    }
    private static void requireMainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Accessory menus require the server thread"); }
}

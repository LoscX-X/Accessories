package com.blanoir.accessory.module.inventory.listener;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.module.attribute.loader.AccessoryLoad;
import com.blanoir.accessory.events.AccessoryPlaceEvent;
import com.blanoir.accessory.module.inventory.AccessoryInventoryLifecycleListener;
import com.blanoir.accessory.module.inventory.ui.AccessoryInventoryHolder;
import com.blanoir.accessory.module.inventory.ui.AccessoryInventoryMenu;
import com.blanoir.accessory.utils.LoreUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InvListener implements Listener {
    private final Accessory plugin;
    private final AccessoryLoad effects;
    private final AccessoryInventoryLifecycleListener invSave;
    private final NamespacedKey LOCKED;
    private final NamespacedKey PRE_PAGE;
    private final NamespacedKey NEXT_PAGE;
    private final AccessoryInventoryMenu menu;
    private final Set<UUID> pendingRefresh = ConcurrentHashMap.newKeySet();

    public InvListener(Accessory plugin) {
        this.plugin = plugin;
        this.effects = new AccessoryLoad(plugin);
        this.menu = new AccessoryInventoryMenu(plugin);
        this.invSave = new AccessoryInventoryLifecycleListener(plugin);
        this.LOCKED = new NamespacedKey(plugin, "locked");
        this.PRE_PAGE = new NamespacedKey(plugin, "pre_page");
        this.NEXT_PAGE = new NamespacedKey(plugin, "next_page");
    }

    private boolean isNotAccessoryTop(InventoryView view) {
        return !(view.getTopInventory().getHolder() instanceof AccessoryInventoryHolder);
    }

    private int currentPage(InventoryView view) {
        if (view.getTopInventory().getHolder() instanceof AccessoryInventoryHolder holder) {
            return holder.currentPage();
        }
        return 1;
    }

    private boolean isSlotConfigured(int page, int slot) {
        return plugin.pageManager().isSlotConfigured(page, slot);
    }

    private boolean isSlotDisabled(int slot) {
        return plugin.service() != null && plugin.service().isSlotDisabled(slot);
    }

    private List<String> requiredLore(int page, int slot) {
        return plugin.pageManager().requiredLore(page, slot);
    }

    private String requiredPermission(int page, int slot) {
        return plugin.pageManager().requiredPermission(page, slot);
    }

    private boolean hasSlotPermission(Player player, int page, int slot) {
        String permission = requiredPermission(page, slot);
        return permission != null && !player.hasPermission(permission);
    }

    private boolean shouldRejectPlacement(Player player, int page, int slot, ItemStack item) {
        if (isSlotDisabled(slot)) return true;
        if (!isSlotConfigured(page, slot)) return true;

        if (hasSlotPermission(player, page, slot)) {
            return true;
        }

        List<String> need = requiredLore(page, slot);
        return !LoreUtils.matchesAnyKeyword(LoreUtils.plainLore(item), need);
    }

    private boolean hasMarker(ItemStack it, NamespacedKey marker) {
        if (it == null || it.getType().isAir()) return false;
        ItemMeta meta = it.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
    }

    private void scheduleRefresh(Player actor, InventoryView view) {
        Inventory top = view.getTopInventory();
        if (!(top.getHolder() instanceof AccessoryInventoryHolder holder)) {
            return;
        }

        UUID ownerId = holder.getOwnerId();

        if (!pendingRefresh.add(ownerId)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingRefresh.remove(ownerId);

            ItemStack[] snapshot = invSave.sanitize(top, holder.currentPage());

            plugin.inventoryStore().updateSlice(
                    ownerId,
                    plugin.accessoryPageStart(holder.currentPage()),
                    snapshot,
                    plugin.accessorySize(holder.currentPage()),
                    plugin.totalAccessoryStorageSize()
            );

            Player owner = Bukkit.getPlayer(ownerId);
            Player refreshTarget = owner != null ? owner : actor;

            ItemStack[] fullContents = plugin.inventoryStore()
                    .getOrLoad(ownerId, plugin.totalAccessoryStorageSize());

            effects.rebuildFromContents(refreshTarget, fullContents);

            if (plugin.skillEngine() != null) {
                plugin.skillEngine().refreshPlayer(refreshTarget, fullContents);
            }
        });
    }

    private boolean callPlaceEvent(Player player, Inventory top, int slot, ItemStack item) {
        AccessoryPlaceEvent event = new AccessoryPlaceEvent(
                player,
                slot,
                item.clone(),
                top.getItem(slot) == null ? null : top.getItem(slot).clone()
        );
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    private List<Integer> frameSlots(InventoryView view) {
        return plugin.pageManager().frameSlots(currentPage(view), view.getTopInventory().getSize());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (isNotAccessoryTop(e.getView())) return;

        Inventory top = e.getView().getTopInventory();
        int topSize = top.getSize();
        int raw = e.getRawSlot();
        int page = currentPage(e.getView());

        if (raw < topSize) {
            ItemStack clicked = top.getItem(raw);
            if (hasMarker(clicked, PRE_PAGE) || hasMarker(clicked, NEXT_PAGE)) {
                e.setCancelled(true);
                switchPage(p, e.getView(), hasMarker(clicked, PRE_PAGE) ? page - 1 : page + 1);
                return;
            }
        }

        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && raw >= topSize) {
            e.setCancelled(true);
            return;
        }

        List<Integer> FRAME = frameSlots(e.getView());

        if (raw < topSize) {
            boolean isFrame = FRAME.contains(raw);
            boolean isDisabled = isSlotDisabled(raw);
            ItemStack cur = e.getCurrentItem();

            if (isFrame || isDisabled) {
                switch (e.getAction()) {
                    case PLACE_ALL, PLACE_SOME, PLACE_ONE,
                         SWAP_WITH_CURSOR, HOTBAR_SWAP,
                         COLLECT_TO_CURSOR,
                         MOVE_TO_OTHER_INVENTORY -> {
                        e.setCancelled(true);
                        return;
                    }
                    default -> {
                        if (hasMarker(cur, LOCKED)) {
                            e.setCancelled(true);
                            plugin.pageManager().executeFrameCommand(p, page, raw, topSize);
                            return;
                        }
                    }
                }
            } else {
                switch (e.getAction()) {
                    case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR, HOTBAR_SWAP -> {
                        ItemStack going = (e.getAction() == InventoryAction.HOTBAR_SWAP)
                                ? p.getInventory().getItem(e.getHotbarButton())
                                : e.getCursor();
                        if (going != null && !going.getType().isAir() && shouldRejectPlacement(p, page, raw, going)) {
                            e.setCancelled(true);
                            if (hasSlotPermission(p, page, raw)) {
                                p.sendMessage(plugin.lang().langComponent("Slot_no_permission"));
                            } else {
                                p.sendMessage(plugin.lang().langComponent("Item_not_match"));
                            }
                            return;
                        }
                        if (going != null && !going.getType().isAir() && callPlaceEvent(p, top, raw, going)) {
                            e.setCancelled(true);
                            return;
                        }
                    }
                    default -> {
                    }
                }
            }
        }

        scheduleRefresh(p, e.getView());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (isNotAccessoryTop(e.getView())) return;

        Inventory top = e.getView().getTopInventory();
        int topSize = top.getSize();
        int page = currentPage(e.getView());
        List<Integer> FRAME = frameSlots(e.getView());

        for (var en : e.getNewItems().entrySet()) {
            int raw = en.getKey();
            if (raw < topSize && FRAME.contains(raw)) {
                e.setCancelled(true);
                p.sendMessage(plugin.lang().langComponent("Item_locked"));
                return;
            }
            if (raw < topSize && isSlotDisabled(raw)) {
                e.setCancelled(true);
                p.sendMessage(plugin.lang().langComponent("Item_locked"));
                return;
            }
        }

        for (var en : e.getNewItems().entrySet()) {
            int raw = en.getKey();
            if (raw >= topSize) continue;
            if (shouldRejectPlacement(p, page, raw, en.getValue())) {
                e.setCancelled(true);
                if (hasSlotPermission(p, page, raw)) {
                    p.sendMessage(plugin.lang().langComponent("Slot_no_permission"));
                } else {
                    p.sendMessage(plugin.lang().langComponent("Item_not_match"));
                }
                return;
            }
            if (callPlaceEvent(p, top, raw, en.getValue())) {
                e.setCancelled(true);
                return;
            }
        }

        scheduleRefresh(p, e.getView());
    }

    private void switchPage(Player viewer, InventoryView view, int targetPage) {
        if (!(view.getTopInventory().getHolder() instanceof AccessoryInventoryHolder holder)) {
            return;
        }

        int current = holder.currentPage();
        int total = holder.totalPages();
        int next = Math.max(1, Math.min(total, targetPage));

        if (next == current) {
            return;
        }

        UUID ownerId = holder.getOwnerId();
        Inventory top = view.getTopInventory();

        ItemStack[] snapshot = invSave.sanitize(top, current);

        plugin.inventoryStore().updateSlice(
                ownerId,
                plugin.accessoryPageStart(current),
                snapshot,
                plugin.accessorySize(current),
                plugin.totalAccessoryStorageSize()
        );

        ItemStack[] nextContents = plugin.inventoryStore().getSliceOrLoad(
                ownerId,
                plugin.accessoryPageStart(next),
                plugin.accessorySize(next),
                plugin.totalAccessoryStorageSize()
        );

        var service = plugin.service();

        Inventory nextInventory = menu.create(
                ownerId,
                next,
                total,
                nextContents,
                service != null ? service.getDisabledSlots() : null
        );

        viewer.openInventory(nextInventory);
        scheduleRefresh(viewer, viewer.getOpenInventory());
    }
}

package com.blanoir.accessory.inventory.listener;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.attribute.AccessoryLoad;
import com.blanoir.accessory.events.AccessoryPlaceEvent;
import com.blanoir.accessory.inventory.InvCreate;
import com.blanoir.accessory.inventory.InvSave;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class InvListener implements Listener {
    private final Accessory plugin;
    private final AccessoryLoad effects;
    private final InvSave invSave;
    private final NamespacedKey LOCKED;
    private final NamespacedKey PRE_PAGE;
    private final NamespacedKey NEXT_PAGE;

    public InvListener(Accessory plugin) {
        this.plugin = plugin;
        this.effects = new AccessoryLoad(plugin);
        this.invSave = new InvSave(plugin);
        this.LOCKED = new NamespacedKey(plugin, "locked");
        this.PRE_PAGE = new NamespacedKey(plugin, "pre_page");
        this.NEXT_PAGE = new NamespacedKey(plugin, "next_page");
    }

    private boolean isNotAccessoryTop(InventoryView view) {
        return !(view.getTopInventory().getHolder() instanceof InvCreate);
    }

    private int currentPage(InventoryView view) {
        if (view.getTopInventory().getHolder() instanceof InvCreate holder) {
            return holder.currentPage();
        }
        return 1;
    }

    private boolean isSlotConfigured(int page, int slot) {
        return plugin.getConfig().isConfigurationSection("Accessory.page_" + page + "." + slot)
                || plugin.getConfig().isConfigurationSection("Accessory." + slot);
    }

    private boolean isSlotDisabled(int slot) {
        return plugin.service() != null && plugin.service().isSlotDisabled(slot);
    }

    private List<String> requiredLore(int page, int slot) {
        String pagePath = "Accessory.page_" + page + "." + slot + ".lore";
        if (plugin.getConfig().isList(pagePath)) {
            return plugin.getConfig().getStringList(pagePath);
        }
        return plugin.getConfig().getStringList("Accessory." + slot + ".lore");
    }

    private String requiredPermission(int page, int slot) {
        String pagePath = "Accessory.page_" + page + "." + slot + ".permission";
        String path = plugin.getConfig().isString(pagePath) ? pagePath : "Accessory." + slot + ".permission";
        if (!plugin.getConfig().isString(path)) {
            return null;
        }
        String permission = plugin.getConfig().getString(path, "").trim();
        return permission.isEmpty() ? null : permission;
    }

    private boolean hasSlotPermission(Player player, int page, int slot) {
        String permission = requiredPermission(page, slot);
        return permission != null && !player.hasPermission(permission);
    }

    private boolean canPlaceInSlot(Player player, int page, int slot, ItemStack item) {
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
        Player target = actor;
        if (top.getHolder() instanceof InvCreate holder) {
            Player owner = Bukkit.getPlayer(holder.ownerId());
            if (owner != null) {
                target = owner;
            }
        }
        Player refreshTarget = target;
        Bukkit.getScheduler().runTask(plugin, () -> {
            effects.rebuildFromInventory(refreshTarget, top);
            if (plugin.skillEngine() != null) {
                plugin.skillEngine().refreshPlayer(refreshTarget, top);
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
        int size = view.getTopInventory().getSize();
        int page = currentPage(view);

        List<Integer> raw = plugin.getConfig().getIntegerList("frame.page_" + page + ".slots");
        if (raw.isEmpty()) {
            raw = plugin.getConfig().getIntegerList("frame.slots");
        }

        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        for (Integer i : raw) {
            if (i == null) continue;
            if (i < 0 || i >= size) {
                plugin.getLogger().warning("[Accessory] frame.slots out of bounds: " + i + " (invSize=" + size + ")");
                continue;
            }
            set.add(i);
        }

        if (set.isEmpty()) {
            for (int d : new int[]{0, 2, 4, 6, 8}) if (d < size) set.add(d);
        }
        return new ArrayList<>(set);
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
                        if (going != null && !going.getType().isAir() && canPlaceInSlot(p, page, raw, going)) {
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
            if (canPlaceInSlot(p, page, raw, en.getValue())) {
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
        if (!(view.getTopInventory().getHolder() instanceof InvCreate holder)) {
            return;
        }

        int current = holder.currentPage();
        int total = holder.totalPages();
        int next = Math.max(1, Math.min(total, targetPage));
        if (next == current) {
            return;
        }

        Inventory top = view.getTopInventory();
        ItemStack[] snapshot = invSave.sanitize(top, current);
        plugin.inventoryStore().updatePage(
                holder.ownerId(),
                current,
                snapshot,
                plugin.accessorySize(),
                plugin.accessoryPages()
        );

        holder.setCurrentPage(next);
        top.clear();
        top.setContents(plugin.inventoryStore().getPageOrLoad(
                holder.ownerId(),
                next,
                plugin.accessorySize(),
                plugin.accessoryPages()
        ));
        holder.decorate(plugin.service() != null ? plugin.service().getDisabledSlots() : null);
        scheduleRefresh(viewer, view);
    }
}

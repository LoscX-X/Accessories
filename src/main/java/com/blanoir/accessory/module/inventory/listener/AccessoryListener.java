package com.blanoir.accessory.module.inventory.listener;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.api.*;
import com.blanoir.accessory.events.*;
import com.blanoir.accessory.module.inventory.InventoryRules;
import com.blanoir.accessory.module.inventory.ui.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.util.*;

/** Event translation only: owner policy, proposed transfer, cancellable event, deferred commit. */
public final class AccessoryListener implements Listener {
    private final Accessory plugin;
    private final InventoryRules rules;
    private final MenuSnapshot snapshot;
    private final Set<Inventory> pending = Collections.newSetFromMap(new IdentityHashMap<>());
    public AccessoryListener(Accessory plugin) {
        this.plugin = plugin; rules = new InventoryRules(plugin); snapshot = new MenuSnapshot(plugin);
    }
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player actor)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof AccessoryInventoryHolder holder)) return;
        Player owner = Bukkit.getPlayer(holder.getOwnerId());
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < top.getSize() && snapshot.decoration(top.getItem(slot))) {
            event.setCancelled(true); plugin.menus().handleFrameClick(actor, event.getView(), slot); return;
        }
        if (owner == null || holder.mode() == AccessoryViewMode.READ_ONLY || holder.pages() != plugin.pageManager(owner)
                || !plugin.profiles().isAllowed(owner, AccessoryAction.OPEN)) { event.setCancelled(true); return; }
        // These actions affect an unknown set of top slots, or manufacture items.
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR || event.getAction() == InventoryAction.CLONE_STACK
                || event.getAction() == InventoryAction.UNKNOWN) { event.setCancelled(true); return; }
        if (slot >= top.getSize()) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) event.setCancelled(true);
            return;
        }
        if (slot < 0) return; // Dropping the cursor outside does not mutate the accessory inventory.
        ItemStack old = event.getCurrentItem();
        boolean removes = switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY,
                 SWAP_WITH_CURSOR, HOTBAR_SWAP, DROP_ONE_SLOT, DROP_ALL_SLOT -> true;
            default -> false;
        };
        if (removes && !rules.mayRemove(owner, old)) { deny(event, actor, "Item_cannot_unequip"); return; }
        ItemStack incoming = switch (event.getAction()) {
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR -> event.getCursor();
            case HOTBAR_SWAP -> event.getClick() == ClickType.SWAP_OFFHAND ? actor.getInventory().getItemInOffHand()
                    : event.getHotbarButton() >= 0 && event.getHotbarButton() < 9 ? actor.getInventory().getItem(event.getHotbarButton()) : null;
            default -> null;
        };
        if (!InventoryRules.empty(incoming)) {
            if (!rules.mayPlace(owner, holder.currentPage(), slot, incoming)
                    || !rules.candidateAllowed(owner, full(owner, top, holder), Map.of(plugin.accessoryPageStart(holder.currentPage()) + slot, incoming))) {
                deny(event, actor, "Item_not_match"); return;
            }
            ItemStack[] before = top.getContents();
            ItemStack cursor = event.getCursor() == null ? null : event.getCursor().clone();
            if (!place(actor, holder, slot, incoming, old) || !Arrays.equals(before, top.getContents())
                    || !Objects.equals(cursor, event.getCursor()) || holder.pages() != plugin.pageManager(owner)
                    || !rules.mayPlace(owner, holder.currentPage(), slot, incoming)) { event.setCancelled(true); return; }
        }
        schedule(actor, top);
    }
    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player actor)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof AccessoryInventoryHolder holder)) return;
        Map<Integer, ItemStack> changes = new LinkedHashMap<>();
        event.getNewItems().forEach((slot, item) -> { if (slot >= 0 && slot < top.getSize()) changes.put(slot, item); });
        if (changes.isEmpty()) return;
        Player owner = Bukkit.getPlayer(holder.getOwnerId());
        if (owner == null || holder.mode() == AccessoryViewMode.READ_ONLY || holder.pages() != plugin.pageManager(owner)
                || !plugin.profiles().isAllowed(owner, AccessoryAction.OPEN)) { event.setCancelled(true); return; }
        Map<Integer, ItemStack> absolute = new LinkedHashMap<>();
        for (var change : changes.entrySet()) {
            int slot = change.getKey();
            if (snapshot.decoration(top.getItem(slot)) || !rules.mayRemove(owner, top.getItem(slot))
                    || !rules.mayPlace(owner, holder.currentPage(), slot, change.getValue())) { event.setCancelled(true); return; }
            absolute.put(plugin.accessoryPageStart(holder.currentPage()) + slot, change.getValue());
        }
        if (!rules.candidateAllowed(owner, full(owner, top, holder), absolute)) {
            event.setCancelled(true); actor.sendMessage(plugin.lang().langComponent("Item_limit_reached")); return;
        }
        ItemStack[] before = top.getContents();
        for (var change : changes.entrySet()) if (!place(actor, holder, change.getKey(), change.getValue(), top.getItem(change.getKey()))) {
            event.setCancelled(true); return;
        }
        if (!Arrays.equals(before, top.getContents()) || holder.pages() != plugin.pageManager(owner)
                || changes.entrySet().stream().anyMatch(change -> !rules.mayPlace(owner, holder.currentPage(), change.getKey(), change.getValue()))) {
            event.setCancelled(true); return;
        }
        schedule(actor, top);
    }
    private ItemStack[] full(Player owner, Inventory top, AccessoryInventoryHolder holder) {
        ItemStack[] contents = plugin.inventoryStore().getOrLoad(owner.getUniqueId(), plugin.totalAccessoryStorageSize());
        ItemStack[] page = snapshot.read(top);
        System.arraycopy(page, 0, contents, plugin.accessoryPageStart(holder.currentPage()), page.length);
        return contents;
    }
    private boolean place(Player actor, AccessoryInventoryHolder holder, int slot, ItemStack item, ItemStack old) {
        var event = new AccessoryPlaceEvent(actor, holder.getOwnerId(), holder.currentPage(), slot, item, old, AccessoryChangeCause.GUI);
        Bukkit.getPluginManager().callEvent(event); return !event.isCancelled();
    }
    private void schedule(Player actor, Inventory top) {
        if (!pending.add(top)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            pending.remove(top);
            if (actor.isOnline() && actor.getOpenInventory().getTopInventory() == top) plugin.menus().commit(actor, top);
        });
    }
    private void deny(InventoryClickEvent event, Player actor, String message) {
        event.setCancelled(true); actor.sendMessage(plugin.lang().langComponent(message));
    }
}

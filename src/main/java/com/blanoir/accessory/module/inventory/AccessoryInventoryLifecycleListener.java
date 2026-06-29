package com.blanoir.accessory.module.inventory;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.module.inventory.ui.AccessoryInventoryHolder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class AccessoryInventoryLifecycleListener implements Listener {

    private final Accessory plugin;
    private final NamespacedKey locked;
    private final NamespacedKey disabled;
    private final NamespacedKey prePage;
    private final NamespacedKey nextPage;

    public AccessoryInventoryLifecycleListener(Accessory plugin) {
        this.plugin = plugin;
        this.locked = new NamespacedKey(plugin, "locked");
        this.disabled = new NamespacedKey(plugin, "disabled");
        this.prePage = new NamespacedKey(plugin, "pre_page");
        this.nextPage = new NamespacedKey(plugin, "next_page");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.inventoryStore().preload(player.getUniqueId(), plugin.totalAccessoryStorageSize());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.inventoryStore().saveAndRemove(player.getUniqueId(), plugin.totalAccessoryStorageSize());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof AccessoryInventoryHolder holder)) {
            return;
        }

        ItemStack[] snapshot = sanitize(top, holder.currentPage());
        UUID ownerId = holder.getOwnerId();
        plugin.inventoryStore().updateSlice(
                ownerId,
                plugin.accessoryPageStart(holder.currentPage()),
                snapshot,
                plugin.accessorySize(holder.currentPage()),
                plugin.totalAccessoryStorageSize()
        );

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && plugin.skillEngine() != null) {
            ItemStack[] fullContents = plugin.inventoryStore().getOrLoad(ownerId, plugin.totalAccessoryStorageSize());
            plugin.skillEngine().refreshPlayer(owner, fullContents);
        }
    }

    public ItemStack[] sanitize(Inventory inventory, int page) {
        ItemStack[] snapshot = inventory.getContents().clone();

        List<Integer> frameSlots = frameSlots(page);
        for (int slot : frameSlots) {
            if (slot < 0 || slot >= snapshot.length) {
                continue;
            }
            if (hasMarker(snapshot[slot], locked)) {
                snapshot[slot] = null;
            }
        }

        for (int slot = 0; slot < snapshot.length; slot++) {
            if (hasMarker(snapshot[slot], disabled)
                    || hasMarker(snapshot[slot], prePage)
                    || hasMarker(snapshot[slot], nextPage)) {
                snapshot[slot] = null;
            }
        }
        return snapshot;
    }

    private List<Integer> frameSlots(int page) {
        return plugin.pageManager().frameSlots(page, plugin.accessorySize(page));
    }

    private boolean hasMarker(ItemStack item, NamespacedKey markerKey) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }
}

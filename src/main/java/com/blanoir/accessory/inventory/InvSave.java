package com.blanoir.accessory.inventory;

import com.blanoir.accessory.Accessory;
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

public class InvSave implements Listener {

    private final Accessory plugin;
    private final NamespacedKey locked;
    private final NamespacedKey disabled;

    public InvSave(Accessory plugin) {
        this.plugin = plugin;
        this.locked = new NamespacedKey(plugin, "locked");
        this.disabled = new NamespacedKey(plugin, "disabled");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.inventoryStore().preload(player.getUniqueId(), plugin.accessorySize());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.inventoryStore().saveAndRemove(player.getUniqueId(), plugin.accessorySize());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof InvCreate)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        ItemStack[] snapshot = sanitize(top);
        plugin.inventoryStore().update(player.getUniqueId(), snapshot, plugin.accessorySize());

        if (plugin.skillEngine() != null) {
            Inventory tmp = Bukkit.createInventory(null, top.getSize());
            tmp.setContents(snapshot.clone());
            plugin.skillEngine().refreshPlayer(player, tmp);
        }
    }

    private ItemStack[] sanitize(Inventory inventory) {
        ItemStack[] snapshot = inventory.getContents().clone();

        List<Integer> frameSlots = plugin.getConfig().getIntegerList("frame.slots");
        if (frameSlots.isEmpty()) {
            frameSlots = List.of(0, 2, 4, 6, 8);
        }

        for (int slot : frameSlots) {
            if (slot < 0 || slot >= snapshot.length) {
                continue;
            }
            if (hasMarker(snapshot[slot], locked)) {
                snapshot[slot] = null;
            }
        }

        for (int slot = 0; slot < snapshot.length; slot++) {
            if (hasMarker(snapshot[slot], disabled)) {
                snapshot[slot] = null;
            }
        }
        return snapshot;
    }

    private boolean hasMarker(ItemStack item, NamespacedKey markerKey) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }
}

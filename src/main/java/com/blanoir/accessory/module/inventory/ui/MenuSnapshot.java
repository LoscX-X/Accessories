package com.blanoir.accessory.module.inventory.ui;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** The only UI-to-storage conversion. Decorations never enter persisted contents. */
public final class MenuSnapshot {
    private final NamespacedKey locked, disabled;
    public MenuSnapshot(JavaPlugin plugin) { locked = new NamespacedKey(plugin, "locked"); disabled = new NamespacedKey(plugin, "disabled"); }
    public boolean decoration(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(locked, PersistentDataType.BYTE) || pdc.has(disabled, PersistentDataType.BYTE);
    }
    public ItemStack[] read(Inventory inventory) {
        ItemStack[] source = inventory.getContents(), result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) if (source[i] != null && !decoration(source[i])) result[i] = source[i].clone();
        return result;
    }
}

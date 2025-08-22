package com.blanoir.accessory.inv;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class InvCreate implements InventoryHolder {
    private final Inventory inventory;
    private static final int[] FRAME_SLOTS = {0, 2, 4, 6, 8};

    public InvCreate(JavaPlugin plugin) {
        int size = plugin.getConfig().getInt("Size", 9);
        String mm = plugin.getConfig().getString("title", "<green>Accessory");
        Component title = MiniMessage.miniMessage().deserialize(mm);
        // 约束到 9..54 且为 9 的倍数
        size = Math.max(9, Math.min(54, size));
        size -= size % 9;

        this.inventory = Bukkit.createInventory(this, size, title); // 也可加标题重载

        // ★ 这里真正把“锁定的玻璃板”放进对应槽位
        ItemStack pane = lockedPane(plugin);
        for (int slot : FRAME_SLOTS) {
            if (slot < size) this.inventory.setItem(slot, pane);
        }
    }

    @Override public Inventory getInventory() { return this.inventory; }

    private ItemStack lockedPane(JavaPlugin plugin) {
        ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.setHideTooltip(true); // 需要 1.20.5+
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "locked"),
                PersistentDataType.BYTE, (byte) 1
        );
        it.setItemMeta(meta);
        return it;
    }
}


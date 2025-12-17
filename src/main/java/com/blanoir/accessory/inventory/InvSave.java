package com.blanoir.accessory.inventory;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class InvSave implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey LOCKED;

    public InvSave(JavaPlugin plugin) {
        this.plugin = plugin;
        this.LOCKED = new NamespacedKey(plugin, "locked"); // 复用同一个 key 是推荐做法
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // 只处理你自己的 GUI（用 Holder 判断最可靠）
        Inventory top = e.getView().getTopInventory(); // 只拿上半区
        if (!(top.getHolder() instanceof InvCreate)) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        ItemStack[] snapshot = top.getContents().clone();

        // 读 frame 槽位（默认 0,2,4,6,8），把“锁面板”清空后再保存
        List<Integer> frameSlots = plugin.getConfig().getIntegerList("frame.slots");
        if (frameSlots == null || frameSlots.isEmpty()) frameSlots = List.of(0, 2, 4, 6, 8);

        for (int s : frameSlots) {
            if (s < 0 || s >= snapshot.length) continue;
            ItemStack it = snapshot[s];
            if (it == null) { snapshot[s] = null; continue; }
            ItemMeta meta = it.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(LOCKED, PersistentDataType.BYTE)) {
                snapshot[s] = null; // 不把外框存进文件
            }
        }

        // 写到与 InvLoad 一致的目录：contains/
        File dir = new File(plugin.getDataFolder(), "contains");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, p.getUniqueId() + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("contents", Arrays.asList(snapshot)); // List<ItemStack>
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Save failed: " + p.getName());
            ex.printStackTrace();
        }
    }
}

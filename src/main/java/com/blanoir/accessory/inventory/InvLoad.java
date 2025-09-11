package com.blanoir.accessory.inventory;

import com.blanoir.accessory.Accessory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public class InvLoad {
    private final JavaPlugin plugin;
    public InvLoad(JavaPlugin plugin) { this.plugin = plugin; }

    public void openFor(Player p) {
        InvCreate holder = new InvCreate((Accessory) plugin);
        Inventory inv = holder.getInventory();

        File file = new File(plugin.getDataFolder(), "contains/" + p.getUniqueId() + ".yml");
        if (file.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            List<?> raw = cfg.getList("contents");
            if (raw != null) {
                // 将 List 逐一安全拷贝进一个与 inv.size 相同的数组
                ItemStack[] arr = new ItemStack[inv.getSize()];
                int limit = Math.min(arr.length, raw.size());
                for (int i = 0; i < limit; i++) {
                    Object o = raw.get(i);
                    if (o instanceof ItemStack is) arr[i] = is;
                }
                inv.setContents(arr); // 会覆盖掉先前构造器里铺的外框。:contentReference[oaicite:2]{index=2}
            }
        }

        // 关键：内容还原后再把“锁定格子”重铺一遍
        holder.applyFrames();

        p.openInventory(inv);
    }
}

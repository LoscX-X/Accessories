package com.blanoir.accessory.inv;

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

        // 从 /plugins/YourPlugin/data/<uuid>.yml 读回
        File file = new File(plugin.getDataFolder(), "data/" + p.getUniqueId() + ".yml");
        if (file.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            List<ItemStack> list = (List<ItemStack>) cfg.getList("contents");
            if (list != null) {
                // List -> ItemStack[]
                inv.setContents(list.toArray(ItemStack[]::new));
            }
        }
        p.openInventory(inv);
    }
}
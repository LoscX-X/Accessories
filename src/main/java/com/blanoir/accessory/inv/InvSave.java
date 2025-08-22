package com.blanoir.accessory.inv;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class InvSave implements Listener {

    private final JavaPlugin plugin;
    public InvSave(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        // 只处理你自己创建的 GUI
        if (!(e.getInventory().getHolder() instanceof InvCreate)) return;

        Player p = (Player) e.getPlayer();
        Inventory inv = e.getInventory();

        ItemStack[] contents = inv.getContents(); //Get the item the chest contain
        try {
            File dir = new File(plugin.getDataFolder(), "contain");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, p.getUniqueId() + ".yml");
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.set("contents", Arrays.asList(contents)); // List<ItemStack>
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Save failed: " + p.getName());
            ex.printStackTrace();
        }
    }
}

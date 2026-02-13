package com.blanoir.accessory.api;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.attributeload.AccessoryLoad;
import com.blanoir.accessory.inventory.InvCreate;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class AccessoryService {

    private final Accessory plugin;

    public AccessoryService(Accessory plugin) {
        this.plugin = plugin;
    }

    /** 清空某玩家的 accessory 背包（在线/离线都支持） */
    public boolean clear(UUID playerId) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerId);
        if (!saveEmptyAccessory(target)) return false;

        Player online = target.getPlayer();
        if (online != null) {
            clearOpenAccessoryInventory(online);
            Inventory empty = Bukkit.createInventory((InventoryHolder) null, accessorySize());
            new AccessoryLoad(plugin).rebuildFromInventory(online, empty);
        }
        return true;
    }

    private boolean saveEmptyAccessory(OfflinePlayer target) {
        File dir = new File(plugin.getDataFolder(), "contains");
        if (!dir.exists() && !dir.mkdirs()) return false;

        File file = new File(dir, target.getUniqueId().toString() + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("contents", List.of());
        try {
            cfg.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Accessory clear failed: " + target.getName());
            ex.printStackTrace();
            return false;
        }
    }

    private void clearOpenAccessoryInventory(Player player) {
        InventoryView view = player.getOpenInventory();
        if (view == null) return;

        InventoryHolder holder = view.getTopInventory().getHolder();
        if (holder instanceof InvCreate invHolder) {
            view.getTopInventory().clear();
            invHolder.applyFrames();
        }
    }

    private int accessorySize() {
        int size = plugin.getConfig().getInt("size", 9);
        size = Math.max(9, Math.min(54, size));
        return size - size % 9;
    }
}

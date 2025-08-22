package com.blanoir.accessory.inv;

import com.blanoir.accessory.Accessory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class InvReload implements CommandExecutor{
    private final Accessory plugin;
    public InvReload(Accessory plugin) { // 传入主类实例
        this.plugin = plugin;
    }
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("accessory.reload")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        plugin.reloadConfig();
        sender.sendMessage("§a[Accessory] Reload Success。");
        sender.sendMessage("§7Size = §e" + plugin.getConfig().getInt("Size"));
        return true;
        }

}


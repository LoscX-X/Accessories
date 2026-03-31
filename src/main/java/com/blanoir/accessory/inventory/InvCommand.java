package com.blanoir.accessory.inventory;

import com.blanoir.accessory.Accessory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class InvCommand implements CommandExecutor {

    private final Accessory plugin;



    public InvCommand(Accessory plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.lang().lang("No_permission"));
            return true;
        }

        InvCreate gui = new InvCreate(plugin);
        player.openInventory(gui.getInventory());
        return true;
    }
}

package com.blanoir.accessory.inventory;

import com.blanoir.accessory.Accessory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InvCommnad implements CommandExecutor {

    private final Accessory plugin;



    public InvCommnad(Accessory plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.lang().lang("No_permission"));
            return true; // 命令处理结束
        }

        InvCreate gui = new InvCreate(plugin);
        player.openInventory(gui.getInventory());
        return true; // 表示命令成功执行，无需发送错误提示
    }
}

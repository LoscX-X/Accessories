package com.blanoir.accessory.inv;

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
            sender.sendMessage("这个命令只能由玩家执行！");
            return true; // 命令处理结束
        }

        InvCreate gui = new InvCreate(plugin);
        player.openInventory(gui.getInventory());
        return true; // 表示命令成功执行，无需发送错误提示
    }
}

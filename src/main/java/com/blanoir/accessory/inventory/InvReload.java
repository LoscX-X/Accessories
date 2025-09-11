package com.blanoir.accessory.inventory;

import com.blanoir.accessory.Accessory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class InvReload implements CommandExecutor {
    private final Accessory plugin;
    public InvReload(Accessory plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // ① 用语言文件的无权限提示
        if (!sender.hasPermission("accessory.reload")) {
            sender.sendMessage(plugin.lang().lang("No_permission")); // Message.No_permission
            return true;
        }

        // ② 先重载 config（Lang 可能在 config.yml 改了）
        plugin.reloadConfig();
        // ③ 再重载语言缓存（只此一处读盘，其余时间走内存）
        plugin.lang().reload();

        // ④ 成功提示也走语言（可带占位符）
        sender.sendMessage(plugin.lang().lang("Reload_success"));
        return true;
    }
}

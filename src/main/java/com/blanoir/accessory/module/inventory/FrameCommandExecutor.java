package com.blanoir.accessory.module.inventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

final class FrameCommandExecutor {
    private FrameCommandExecutor() {
    }

    static void execute(Player player, int page, int slot, List<String> commands, boolean console) {
        for (String raw : commands) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            String command = raw
                    .replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{page}", String.valueOf(page))
                    .replace("{slot}", String.valueOf(slot));
            if (console) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } else {
                player.performCommand(command);
            }
        }
    }
}

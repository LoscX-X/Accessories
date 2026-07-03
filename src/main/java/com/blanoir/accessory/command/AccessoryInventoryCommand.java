package com.blanoir.accessory.command;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.module.attribute.loader.AccessoryLoad;
import com.blanoir.accessory.module.inventory.AccessoryInventoryLoad;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AccessoryInventoryCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_COMMANDS = List.of("open", "reload", "clear", "view");

    private final Accessory plugin;
    private final AccessoryLoad accessoryLoad;
    private final AccessoryInventoryLoad inventoryLoad;

    public AccessoryInventoryCommand(Accessory plugin) {
        this.plugin = plugin;
        this.accessoryLoad = new AccessoryLoad(plugin);
        this.inventoryLoad = new AccessoryInventoryLoad(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        String sub = args.length == 0 ? "open" : args[0].toLowerCase(Locale.ROOT);

        return switch (sub) {
            case "open" -> executeOpen(sender);
            case "reload" -> executeReload(sender, args);
            case "clear" -> executeClear(sender, args);
            case "view" -> executeView(sender, args);
            case "quickequip" -> executeQuickEquip(sender, args);
            default -> {
                sender.sendMessage(plugin.lang().lang("Accessory_unknown"));
                yield true;
            }
        };
    }

    private boolean executeOpen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.lang().lang("Only_player_open"));
            return true;
        }

        inventoryLoad.openFor(player);
        return true;
    }

    private boolean executeReload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accessory.reload")) {
            sender.sendMessage(plugin.lang().lang("No_permission"));
            return true;
        }

        if (args.length > 1) {
            sender.sendMessage(plugin.lang().lang("Reload_usage"));
            return true;
        }

        plugin.reloadPluginSettings();

        int totalSize = plugin.totalAccessoryStorageSize();

        for (Player online : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = plugin.inventoryStore().getOrLoad(online.getUniqueId(), totalSize);

            accessoryLoad.rebuildFromContents(online, contents);

            if (plugin.skillEngine() != null) {
                plugin.skillEngine().refreshPlayer(online, contents);
            }
        }

        sender.sendMessage(plugin.lang().lang("Reload_success"));
        return true;
    }

    private boolean executeClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accessory.clear")) {
            sender.sendMessage(plugin.lang().lang("No_permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.lang().lang("Clear_usage"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

        if (target.getName() == null && !target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(plugin.lang().lang("Player_not_found"));
            return true;
        }

        boolean success = plugin.service().clear(target.getUniqueId());
        sender.sendMessage(plugin.lang().lang(success ? "Clear_success" : "Clear_failed"));

        return true;
    }

    private boolean executeView(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(plugin.lang().lang("Only_player_open"));
            return true;
        }

        if (!viewer.hasPermission("accessory.view")) {
            sender.sendMessage(plugin.lang().lang("No_permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.lang().lang("View_usage"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            sender.sendMessage(plugin.lang().lang("Player_not_found"));
            return true;
        }

        inventoryLoad.openFor(viewer, target);
        return true;
    }

    private boolean executeQuickEquip(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.lang().lang("Only_player_open"));
            return true;
        }

        if (args.length < 3) {
            return true;
        }

        try {
            int page = Integer.parseInt(args[1]);
            int slot = Integer.parseInt(args[2]);
            plugin.quickEquipService().equipToSlot(player, page, slot);
        } catch (NumberFormatException ignored) {
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      String[] args) {
        if (args.length == 1) {
            return filterByPrefix(args[0], ROOT_COMMANDS);
        }

        if (args.length == 2 && ("clear".equalsIgnoreCase(args[0]) || "view".equalsIgnoreCase(args[0]))) {
            List<String> players = new ArrayList<>();

            for (Player online : Bukkit.getOnlinePlayers()) {
                players.add(online.getName());
            }

            return filterByPrefix(args[1], players);
        }

        return List.of();
    }

    private List<String> filterByPrefix(String input, List<String> options) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();

        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }

        return out;
    }
}
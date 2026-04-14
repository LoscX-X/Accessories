package com.blanoir.accessory.inventory;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.attribute.AccessoryLoad;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InvReload implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_SUB_COMMANDS = List.of("open", "reload", "clear", "view");

    private final Accessory plugin;
    private final AccessoryLoad accessoryLoad;

    public InvReload(Accessory plugin) {
        this.plugin = plugin;
        this.accessoryLoad = new AccessoryLoad(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command cmd,
                             @NotNull String label,
                             @NotNull String[] args) {
        String sub = args.length == 0 ? "open" : args[0].toLowerCase(Locale.ROOT);

        return switch (sub) {
            case "open" -> executeOpen(sender);
            case "reload" -> executeReload(sender, args);
            case "clear" -> executeClear(sender, args);
            case "view" -> executeView(sender, args);
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

        new InvLoad(plugin).openFor(player);
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
        if (plugin.skillEngine() != null) {
            plugin.skillEngine().loadConfig();
        }

        int size = plugin.accessorySize();
        int pages = plugin.accessoryPages();
        for (Player online : Bukkit.getOnlinePlayers()) {
            Inventory stored = Bukkit.createInventory(null, size);
            stored.setContents(plugin.inventoryStore().getPageOrLoad(online.getUniqueId(), 1, size, pages));
            accessoryLoad.rebuildFromInventory(online, stored);
            if (plugin.skillEngine() != null) {
                plugin.skillEngine().refreshPlayer(online, stored);
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

        int totalSize = plugin.totalAccessoryStorageSize();
        plugin.inventoryStore().clear(target.getUniqueId(), totalSize);
        plugin.inventoryStore().flush(target.getUniqueId(), totalSize);

        Player online = target.getPlayer();
        if (online != null) {
            int size = plugin.accessorySize();
            clearOpenAccessoryInventory(online);
            Inventory empty = Bukkit.createInventory(null, size);
            accessoryLoad.rebuildFromInventory(online, empty);
            if (plugin.skillEngine() != null) {
                plugin.skillEngine().refreshPlayer(online, empty);
            }
        }

        sender.sendMessage(plugin.lang().lang("Clear_success"));
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

        new InvLoad(plugin).openFor(viewer, target);
        return true;
    }

    private void clearOpenAccessoryInventory(Player player) {
        InventoryView view = player.getOpenInventory();
        if (!(view.getTopInventory().getHolder() instanceof InvCreate holder)) {
            return;
        }

        Inventory top = view.getTopInventory();
        top.clear();
        holder.applyFrames();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      String[] args) {
        if (args.length == 1) {
            return filterByPrefix(args[0], ROOT_SUB_COMMANDS);
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
        return getStrings(input, options);
    }

    @NotNull
    public static List<String> getStrings(String input, List<String> options) {
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

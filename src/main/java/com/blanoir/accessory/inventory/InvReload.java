package com.blanoir.accessory.inventory;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.attributeload.AccessoryLoad;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

public class InvReload implements CommandExecutor, TabCompleter {
    private final Accessory plugin;
    private final AccessoryLoad accessoryLoad;
    public InvReload(Accessory plugin) {
        this.plugin = plugin;
        this.accessoryLoad = new AccessoryLoad(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || "open".equalsIgnoreCase(args[0])) {
            return handleOpen(sender);
        }
        if (args.length > 0 && "clear".equalsIgnoreCase(args[0])) {
            return handleClear(sender, args);
        }
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            return handleReload(sender, args);
        }
        sender.sendMessage(plugin.lang().lang("Accessory_unknown"));
        sender.sendMessage(plugin.lang().lang("Accessory_help"));
        return true;
    }

    private boolean handleOpen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.lang().lang("Only_player_open"));
            return true;
        }
        new InvLoad(plugin).openFor(player);
        return true;
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accessory.reload")) {
            sender.sendMessage(plugin.lang().lang("No_permission"));
            return true;
        }
        if (args.length > 0 && !"reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage(plugin.lang().lang("Reload_usage"));
            return true;
        }
        plugin.reloadConfig();
        plugin.lang().reload();
        plugin.skillEngine().loadConfig();
        for (Player online : Bukkit.getOnlinePlayers()) {
            Inventory stored = loadStoredInventory(online, accessorySize());
            this.accessoryLoad.rebuildFromInventory(online, stored);
            plugin.skillEngine().refreshPlayer(online, stored);
        }
        sender.sendMessage(plugin.lang().lang("Reload_success"));
        return true;
    }

    private Inventory loadStoredInventory(Player player, int size) {
        Inventory tmp = Bukkit.createInventory(null, size);
        File file = new File(plugin.getDataFolder(), "contains/" + player.getUniqueId() + ".yml");
        if (!file.exists()) {
            return tmp;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<?> raw = cfg.getList("contents");
        if (raw == null || raw.isEmpty()) {
            return tmp;
        }

        for (int i = 0; i < Math.min(size, raw.size()); i++) {
            Object it = raw.get(i);
            if (it instanceof org.bukkit.inventory.ItemStack stack) {
                tmp.setItem(i, stack);
            }
        }
        return tmp;
    }

    private boolean handleClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("accessory.clear")) {
            sender.sendMessage(plugin.lang().lang("No_permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.lang().lang("Clear_usage"));
            return true;
        }
        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getName() == null && !target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(plugin.lang().lang("Player_not_found"));
            return true;
        }
        if (!saveEmptyAccessory(target)) {
            sender.sendMessage(plugin.lang().lang("Clear_failed"));
            return true;
        }
        Player online = target.getPlayer();
        if (online != null) {
            clearOpenAccessoryInventory(online);
            Inventory empty = Bukkit.createInventory(null, accessorySize());
            new AccessoryLoad(this.plugin).rebuildFromInventory(online, empty);
            plugin.skillEngine().refreshPlayer(online, empty);
        }
        sender.sendMessage(plugin.lang().lang("Clear_success"));
        return true;
    }

    private boolean saveEmptyAccessory(OfflinePlayer target) {
        File dir = new File(plugin.getDataFolder(), "contains");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, target.getUniqueId() + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("contents", List.of());
        try {
            cfg.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Clear failed: " + target.getName());
            ex.printStackTrace();
            return false;
        }
    }

    private void clearOpenAccessoryInventory(Player player) {
        InventoryView view = player.getOpenInventory();
        if (view == null) return;
        if (!(view.getTopInventory().getHolder() instanceof InvCreate holder)) return;
        Inventory top = view.getTopInventory();
        top.clear();
        holder.applyFrames();
    }

    private int accessorySize() {
        int size = plugin.getConfig().getInt("size", 9);
        size = Math.max(9, Math.min(54, size));
        return size - (size % 9);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("open", "reload", "clear"));
        }
        if (args.length == 2 && "clear".equalsIgnoreCase(args[0])) {
            List<String> players = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                players.add(online.getName());
            }
            return filter(args[1], players);
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
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

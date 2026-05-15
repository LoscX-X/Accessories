package com.blanoir.accessory.inventory;

import com.blanoir.accessory.Accessory;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class AccessoryPageManager {
    private static final String PAGE_DIR = "page";

    private final Accessory plugin;
    private final Map<Integer, YamlConfiguration> pages = new HashMap<>();

    public AccessoryPageManager(Accessory plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        pages.clear();

        File dir = new File(plugin.getDataFolder(), PAGE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().severe("[Accessory] Failed to create page config folder: " + dir.getAbsolutePath());
            return;
        }

        File[] files = dir.listFiles((file, name) -> name.toLowerCase().endsWith(".yml") || name.toLowerCase().endsWith(".yaml"));
        if (files == null || files.length == 0) {
            return;
        }

        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparing(File::getName));
        for (File file : sorted) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            if (!cfg.isInt("page")) {
                plugin.getLogger().severe("[Accessory] 页面配置 " + file.getName() + " 缺失必填页码 page，已跳过该文件");
                continue;
            }

            int page = cfg.getInt("page");
            if (page < 1) {
                plugin.getLogger().severe("[Accessory] Page config " + file.getName() + " has invalid page: " + page);
                continue;
            }

            if (pages.containsKey(page)) {
                plugin.getLogger().severe("[Accessory] Duplicate page config for page " + page + ": " + file.getName() + " skipped");
                continue;
            }
            pages.put(page, cfg);
        }
    }

    public int configuredPageCount(int configuredPages) {
        int max = Math.max(1, configuredPages);
        for (Integer page : pages.keySet()) {
            max = Math.max(max, page);
        }

        ConfigurationSection accessorySection = plugin.getConfig().getConfigurationSection("Accessory");
        if (accessorySection == null) {
            return max;
        }

        for (String key : accessorySection.getKeys(false)) {
            if (!key.startsWith("page_")) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(key.substring("page_".length())));
            } catch (NumberFormatException ignored) {
                // Ignore non-numeric page section suffixes.
            }
        }
        return max;
    }

    public int pageSize(int page) {
        YamlConfiguration pageConfig = pages.get(page);
        int size = pageConfig == null ? plugin.getConfig().getInt("size", 9) : pageConfig.getInt("size", plugin.getConfig().getInt("size", 9));
        size = Math.max(9, Math.min(54, size));
        return size - (size % 9);
    }

    public int maxPageSize() {
        int max = 9;
        for (int page = 1; page <= plugin.accessoryPages(); page++) {
            max = Math.max(max, pageSize(page));
        }
        return max;
    }

    public int pageStart(int page) {
        int start = 0;
        int normalizedPage = Math.max(1, Math.min(plugin.accessoryPages(), page));
        for (int i = 1; i < normalizedPage; i++) {
            start += pageSize(i);
        }
        return start;
    }

    public int totalStorageSize() {
        int total = 0;
        for (int page = 1; page <= plugin.accessoryPages(); page++) {
            total += pageSize(page);
        }
        return Math.max(9, total);
    }

    public int pageByAbsoluteSlot(int absoluteSlot) {
        int start = 0;
        for (int page = 1; page <= plugin.accessoryPages(); page++) {
            int size = pageSize(page);
            if (absoluteSlot >= start && absoluteSlot < start + size) {
                return page;
            }
            start += size;
        }
        return -1;
    }

    public int localSlot(int absoluteSlot) {
        int page = pageByAbsoluteSlot(absoluteSlot);
        return page == -1 ? -1 : absoluteSlot - pageStart(page);
    }

    public boolean isSlotConfigured(int page, int slot) {
        return pageSection(page, "Accessory." + slot) != null
                || plugin.getConfig().isConfigurationSection("Accessory.page_" + page + "." + slot)
                || plugin.getConfig().isConfigurationSection("Accessory." + slot);
    }

    public List<String> requiredLore(int page, int slot) {
        YamlConfiguration pageConfig = pages.get(page);
        String pageFilePath = "Accessory." + slot + ".lore";
        if (pageConfig != null && pageConfig.isList(pageFilePath)) {
            return pageConfig.getStringList(pageFilePath);
        }

        String pagePath = "Accessory.page_" + page + "." + slot + ".lore";
        if (plugin.getConfig().isList(pagePath)) {
            return plugin.getConfig().getStringList(pagePath);
        }
        return plugin.getConfig().getStringList("Accessory." + slot + ".lore");
    }

    public String requiredPermission(int page, int slot) {
        YamlConfiguration pageConfig = pages.get(page);
        String pageFilePath = "Accessory." + slot + ".permission";
        if (pageConfig != null && pageConfig.isString(pageFilePath)) {
            String permission = pageConfig.getString(pageFilePath, "").trim();
            return permission.isEmpty() ? null : permission;
        }

        String pagePath = "Accessory.page_" + page + "." + slot + ".permission";
        String path = plugin.getConfig().isString(pagePath) ? pagePath : "Accessory." + slot + ".permission";
        if (!plugin.getConfig().isString(path)) {
            return null;
        }
        String permission = plugin.getConfig().getString(path, "").trim();
        return permission.isEmpty() ? null : permission;
    }

    public List<Integer> frameSlots(int page, int size) {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        for (FrameItem item : frameItems(page, size)) {
            out.addAll(item.slots());
        }

        if (out.isEmpty()) {
            for (int d : new int[]{0, 2, 4, 6, 8}) {
                if (d < size) out.add(d);
            }
        }
        return new ArrayList<>(out);
    }

    public List<FrameItem> frameItems(int page, int size) {
        List<FrameItem> items = new ArrayList<>();
        YamlConfiguration pageConfig = pages.get(page);
        ConfigurationSection pageFrame = pageConfig == null ? null : pageConfig.getConfigurationSection("frame");
        if (pageFrame != null) {
            for (String key : pageFrame.getKeys(false)) {
                ConfigurationSection itemSection = pageFrame.getConfigurationSection(key);
                if (itemSection == null || !itemSection.isList("slots")) {
                    continue;
                }
                items.add(new FrameItem(key, itemSection, validSlots(page, key, itemSection.getIntegerList("slots"), size)));
            }
        }

        if (!items.isEmpty()) {
            return items;
        }

        ConfigurationSection legacyItem = plugin.getConfig().getConfigurationSection("frame.item");
        if (legacyItem != null) {
            items.add(new FrameItem("item", legacyItem, validSlots(page, "item", rawLegacyFrameSlots(page), size)));
        }
        return items;
    }

    public FrameItem frameItemAt(int page, int slot, int size) {
        for (FrameItem item : frameItems(page, size)) {
            if (item.slots().contains(slot)) {
                return item;
            }
        }
        return null;
    }

    public boolean executeFrameCommand(Player player, int page, int slot, int size) {
        FrameItem item = frameItemAt(page, slot, size);
        if (item == null || !"command".equalsIgnoreCase(item.section().getString("drag", ""))) {
            return false;
        }

        executeCommands(player, page, slot, commands(item.section(), "command.console"), true);
        executeCommands(player, page, slot, commands(item.section(), "command.player"), false);
        return true;
    }

    public ConfigurationSection pageAccessorySection(int page) {
        YamlConfiguration pageConfig = pages.get(page);
        if (pageConfig != null && pageConfig.isConfigurationSection("Accessory")) {
            return pageConfig.getConfigurationSection("Accessory");
        }
        return plugin.getConfig().getConfigurationSection("Accessory.page_" + page);
    }

    public ConfigurationSection legacyAccessorySection() {
        return plugin.getConfig().getConfigurationSection("Accessory");
    }


    private List<String> commands(ConfigurationSection section, String path) {
        if (section.isList(path)) {
            return section.getStringList(path);
        }
        if (section.isString(path)) {
            return List.of(section.getString(path, ""));
        }
        return List.of();
    }

    private void executeCommands(Player player, int page, int slot, List<String> commands, boolean console) {
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

    private List<Integer> validSlots(int page, String itemKey, List<Integer> raw, int size) {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        for (Integer slot : raw) {
            if (slot == null) continue;
            if (slot < 0 || slot >= size) {
                plugin.getLogger().warning("[Accessory] frame item " + itemKey + " slot out of bounds on page " + page + ": " + slot + " (invSize=" + size + ")");
                continue;
            }
            out.add(slot);
        }
        return new ArrayList<>(out);
    }

    private ConfigurationSection pageSection(int page, String path) {
        YamlConfiguration pageConfig = pages.get(page);
        if (pageConfig == null) {
            return null;
        }
        return pageConfig.getConfigurationSection(path);
    }

    private List<Integer> rawLegacyFrameSlots(int page) {
        String pagePath = "frame.page_" + page + ".slots";
        List<Integer> slots = plugin.getConfig().getIntegerList(pagePath);
        if (slots.isEmpty()) {
            slots = plugin.getConfig().getIntegerList("frame.slots");
        }
        return slots;
    }

    public record FrameItem(String key, ConfigurationSection section, List<Integer> slots) {
    }
}

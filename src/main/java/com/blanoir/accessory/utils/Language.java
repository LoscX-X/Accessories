package com.blanoir.accessory.utils;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Language {
    private final JavaPlugin plugin;
    private static final String SUBFOLDER = "Language"; // 改成 "Language" 也行，和你的资源目录保持一致

    // 当前语言文件名，例如 en_US.yml
    private String currentFileName;
    private final String zh = "zh_CN.yml";

    // 缓存：单行消息 & 多行消息
    private final Map<String, String> STR = new HashMap<>();
    private final Map<String, List<String>> LINES = new HashMap<>();

    public Language(JavaPlugin plugin) {
        this.plugin = plugin;
        reload(); // 首次加载就构建缓存
    }

    /** 仅在 reload 时从磁盘读一次，并刷新缓存 */
    public void reload() {
        // 1) 从 config.yml 读取
        String code = plugin.getConfig().getString("Lang", "en_US");
        this.currentFileName = code.endsWith(".yml") ? code : (code + ".yml");

        // 2) 确保数据目录与语言文件存在
        File langDir = new File(plugin.getDataFolder(), SUBFOLDER);
        langDir.mkdirs();
        File file = new File(langDir, currentFileName);
        if (!file.exists()) {
            plugin.saveResource(SUBFOLDER + "/" + currentFileName, false);
            plugin.saveResource(SUBFOLDER + "/" + zh , false);
        }
        // 3) 读取 yml
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource(SUBFOLDER + "/" + currentFileName)) {
            if (in != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                cfg.setDefaults(def);
            }
        } catch (Exception ignored) {}

        // 4) 清空并重建缓存（只做一次内存操作）
        STR.clear();
        LINES.clear();
        ConfigurationSection root = cfg.getConfigurationSection("Message");
        if (root != null) {
            for (String k : root.getKeys(false)) {
                String path = "Message." + k;
                if (cfg.isList(path)) {
                    List<String> list = colorize(cfg.getStringList(path));
                    LINES.put(k, list);
                } else {
                    String s = cfg.getString(path, "Missing:" + k);
                    STR.put(k, colorize(s));
                }
            }
        }
    }

    /** 取单行消息（不读盘，只查缓存） */
    public String lang(String key) {
        return STR.getOrDefault(key, "Missing:" + key);
    }

    /** 取多行消息（不读盘，只查缓存） */
    public List<String> langLines(String key) {
        return LINES.getOrDefault(key, Collections.emptyList());
    }

    private static String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
    private static List<String> colorize(List<String> src) {
        List<String> out = new ArrayList<>(src.size());
        for (String s : src) out.add(colorize(s));
        return out;
    }
}

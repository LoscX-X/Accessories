package com.blanoir.accessory.utils;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Language {
    private static final String LANGUAGE_DIR = "Language";
    private static final String DEFAULT_LANG_CODE = "en_US";
    private static final String FALLBACK_FILE = "zh_CN.yml";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final Map<String, String> singleLineMessages = new HashMap<>();
    private final Map<String, List<String>> multiLineMessages = new HashMap<>();
    private final Map<String, Component> singleLineComponents = new HashMap<>();
    private final Map<String, List<Component>> multiLineComponents = new HashMap<>();


    public Language(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reload language config from disk and rebuild message cache.
     */
    public void reload() {
        String currentFileName = resolveLanguageFileName();
        FileConfiguration cfg = loadLanguageConfiguration(currentFileName);

        singleLineMessages.clear();
        multiLineMessages.clear();
        singleLineComponents.clear();
        multiLineComponents.clear();

        ConfigurationSection root = cfg.getConfigurationSection("Message");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            String path = "Message." + key;
            if (cfg.isList(path)) {
                List<String> source = cfg.getStringList(path);
                multiLineMessages.put(key, formatLines(source));
                multiLineComponents.put(key, formatComponents(source));
            } else {
                String raw = cfg.getString(path, "Missing:" + key);
                singleLineMessages.put(key, format(raw));
                singleLineComponents.put(key, formatComponent(raw));
            }
        }
    }

    public String lang(String key) {
        return singleLineMessages.getOrDefault(key, "Missing:" + key);
    }

    public List<String> langLines(String key) {
        return multiLineMessages.getOrDefault(key, Collections.emptyList());
    }

    public Component langComponent(String key) {
        return singleLineComponents.getOrDefault(key, Component.text("Missing:" + key));
    }

    public List<Component> langComponentLines(String key) {
        return multiLineComponents.getOrDefault(key, Collections.emptyList());
    }

    private String resolveLanguageFileName() {
        String code = plugin.getConfig().getString("Lang", DEFAULT_LANG_CODE);
        return code.endsWith(".yml") ? code : code + ".yml";
    }

    private FileConfiguration loadLanguageConfiguration(String fileName) {
        File langDir = new File(plugin.getDataFolder(), LANGUAGE_DIR);
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        ensureLanguageFile(fileName);
        ensureLanguageFile(FALLBACK_FILE);

        File file = new File(langDir, fileName);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        attachDefaultsFromJar(cfg, fileName);
        return cfg;
    }

    private void ensureLanguageFile(String fileName) {
        File file = new File(plugin.getDataFolder(), LANGUAGE_DIR + "/" + fileName);
        if (!file.exists()) {
            plugin.saveResource(LANGUAGE_DIR + "/" + fileName, false);
        }
    }

    private void attachDefaultsFromJar(FileConfiguration cfg, String fileName) {
        try (InputStream in = plugin.getResource(LANGUAGE_DIR + "/" + fileName)) {
            if (in == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8)
            );
            cfg.setDefaults(defaults);
        } catch (Exception ignored) {
        }
    }

    private String format(String input) {
        String raw = input == null ? "" : input;

        try {
            String miniMessageParsed = LEGACY_SERIALIZER.serialize(MINI_MESSAGE.deserialize(raw));
            return ChatColor.translateAlternateColorCodes('&', miniMessageParsed);
        } catch (Exception ignored) {
            return ChatColor.translateAlternateColorCodes('&', raw);
        }
    }

    private List<String> formatLines(List<String> source) {
        List<String> out = new ArrayList<>(source.size());
        for (String line : source) {
            out.add(format(line));
        }
        return out;
    }

    private Component formatComponent(String input) {
        String raw = input == null ? "" : input;
        try {
            return MINI_MESSAGE.deserialize(raw);
        } catch (Exception ignored) {
            String legacy = ChatColor.translateAlternateColorCodes('&', raw);
            return LEGACY_SERIALIZER.deserialize(legacy);
        }
    }

    private List<Component> formatComponents(List<String> source) {
        List<Component> out = new ArrayList<>(source.size());
        for (String line : source) {
            out.add(formatComponent(line));
        }
        return out;
    }
}

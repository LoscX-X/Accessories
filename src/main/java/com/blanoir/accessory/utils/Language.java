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
import java.util.*;

public final class Language {
    private static final String LANGUAGE_DIR = "Language";
    private static final String DEFAULT_LANG_CODE = "en_US";
    private static final String FALLBACK_FILE = "zh_CN.yml";

    private static final String MESSAGE_ROOT = "Message";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final Map<String, String> singleLineMessages = new HashMap<>();
    private final Map<String, List<String>> multiLineMessages = new HashMap<>();
    private final Map<String, Component> singleLineComponents = new HashMap<>();
    private final Map<String, List<Component>> multiLineComponents = new HashMap<>();

    private Map<String, String> singleLineMessages = Collections.emptyMap();
    private Map<String, List<String>> multiLineMessages = Collections.emptyMap();

    public Language(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String currentFileName = resolveLanguageFileName();

        singleLineMessages.clear();
        multiLineMessages.clear();
        singleLineComponents.clear();
        multiLineComponents.clear();

        FileConfiguration primary = loadLanguageConfiguration(currentFileName);
        FileConfiguration fallback = loadLanguageConfiguration(FALLBACK_FILE);

        Map<String, String> singles = new HashMap<>();
        Map<String, List<String>> multis = new HashMap<>();

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

        this.singleLineMessages = Collections.unmodifiableMap(singles);
        this.multiLineMessages = Collections.unmodifiableMap(multis);
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

    private void ensureLanguageDirectory() {
        File langDir = new File(plugin.getDataFolder(), LANGUAGE_DIR);
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
    }

    private FileConfiguration loadLanguageConfiguration(String fileName) {
        ensureLanguageFileIfPossible(fileName);
        File file = new File(plugin.getDataFolder(), LANGUAGE_DIR + "/" + fileName);
        return YamlConfiguration.loadConfiguration(file);
    }

    private void ensureLanguageFileIfPossible(String fileName) {
        File file = new File(plugin.getDataFolder(), LANGUAGE_DIR + "/" + fileName);
        if (file.exists()) {
            return;
        }

        String resourcePath = LANGUAGE_DIR + "/" + fileName;
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in != null) {
                plugin.saveResource(resourcePath, false);
            }
        } catch (Exception ignored) {
        }
    }

    private Set<String> collectMessageKeys(FileConfiguration primary, FileConfiguration fallback) {
        Set<String> keys = new LinkedHashSet<>();

        ConfigurationSection primaryRoot = primary.getConfigurationSection(MESSAGE_ROOT);
        if (primaryRoot != null) {
            keys.addAll(primaryRoot.getKeys(false));
        }

        ConfigurationSection fallbackRoot = fallback.getConfigurationSection(MESSAGE_ROOT);
        if (fallbackRoot != null) {
            keys.addAll(fallbackRoot.getKeys(false));
        }

        return keys;
    }

    private boolean isList(FileConfiguration cfg, String path) {
        return cfg.contains(path) && cfg.isList(path);
    }

    private String format(String input) {
        String raw = input == null ? "" : input;
        try {
            return ChatColor.translateAlternateColorCodes(
                    '&',
                    LEGACY_SERIALIZER.serialize(MINI_MESSAGE.deserialize(raw))
            );
        } catch (Exception ignored) {
            return ChatColor.translateAlternateColorCodes('&', raw);
        }
    }

    private List<String> formatLines(List<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

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

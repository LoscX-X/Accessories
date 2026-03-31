package com.blanoir.accessory.utils;

import net.kyori.adventure.text.minimessage.MiniMessage;
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

    private Map<String, String> singleLineMessages = Collections.emptyMap();
    private Map<String, List<String>> multiLineMessages = Collections.emptyMap();

    public Language(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String currentFileName = resolveLanguageFileName();

        ensureLanguageDirectory();

        FileConfiguration primary = loadLanguageConfiguration(currentFileName);
        FileConfiguration fallback = loadLanguageConfiguration(FALLBACK_FILE);

        Map<String, String> singles = new HashMap<>();
        Map<String, List<String>> multis = new HashMap<>();

        Set<String> keys = collectMessageKeys(primary, fallback);

        for (String key : keys) {
            String path = MESSAGE_ROOT + "." + key;

            if (isList(primary, path) || isList(fallback, path)) {
                List<String> raw = primary.getStringList(path);
                if (raw.isEmpty()) {
                    raw = fallback.getStringList(path);
                }
                multis.put(key, Collections.unmodifiableList(formatLines(raw)));
            } else {
                String raw = primary.getString(path);
                if (raw == null) {
                    raw = fallback.getString(path, "Missing:" + key);
                }
                singles.put(key, format(raw));
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
}
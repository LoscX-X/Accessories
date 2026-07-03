package com.blanoir.accessory.utils.lang;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.*;

public final class Lang {

    private final String LangFile;

    private static final String LANGUAGE_DIR = "Language";

    private static final String DEFAULT_LANG = "en_US.yml";

    private static final String MESSAGE_ROOT = "Message";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final Map<String, String> rawSingleLineMessages = new HashMap<>();
    private final Map<String, String> singleLineMessages = new HashMap<>();
    private final Map<String, List<String>> multiLineMessages = new HashMap<>();
    private final Map<String, Component> singleLineComponents = new HashMap<>();
    private final Map<String, List<Component>> multiLineComponents = new HashMap<>();



    public Lang(JavaPlugin plugin,String langFile) {
        this.plugin = plugin;
        this.LangFile = langFile;
        reload();
    }

    public void reload() {
        FileConfiguration primary = loadLanguageConfiguration(LangFile);
        FileConfiguration fallback = loadLanguageConfiguration(DEFAULT_LANG);

        Map<String, String> raws = new HashMap<>();
        Map<String, String> singles = new HashMap<>();
        Map<String, List<String>> multis = new HashMap<>();

        Map<String, Component> singlesC = new HashMap<>();
        Map<String, List<Component>> multisC = new HashMap<>();

        Set<String> keys = collectMessageKeys(primary, fallback);

        for (String key : keys) {
            String path = MESSAGE_ROOT + "." + key;

            if (isList(primary, path) || isList(fallback, path)) {
                List<String> raw = primary.getStringList(path);
                if (raw.isEmpty()) raw = fallback.getStringList(path);

                multis.put(key, formatLines(raw));
                multisC.put(key, formatComponents(raw));

            } else {
                String raw = primary.getString(path);
                if (raw == null) raw = fallback.getString(path, "Missing:" + key);

                raws.put(key, raw);
                singles.put(key, format(raw));
                singlesC.put(key, formatComponent(raw));
            }
        }


// 写入字符串缓存
        this.rawSingleLineMessages.clear();
        this.rawSingleLineMessages.putAll(raws);

        this.singleLineMessages.clear();
        this.singleLineMessages.putAll(singles);

        this.multiLineMessages.clear();
        this.multiLineMessages.putAll(multis);

// 写入 Component 缓存
        this.singleLineComponents.clear();
        this.singleLineComponents.putAll(singlesC);

        this.multiLineComponents.clear();
        this.multiLineComponents.putAll(multisC);

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

    public Component langComponent(String key, Map<String, String> replacements) {
        String raw = rawSingleLineMessages.getOrDefault(key, "Missing:" + key);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return formatComponent(raw);
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
        return LEGACY_SERIALIZER.serialize(formatComponent(input));
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
            return AMPERSAND_SERIALIZER.deserialize(raw);
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
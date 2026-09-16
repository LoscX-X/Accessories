package com.blanoir.accessory.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/** Resource setup and deterministic YAML discovery shared by page and skill configuration. */
public final class ConfigFiles {
    private ConfigFiles() { }

    public static void initialize(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        saveIfMissing(plugin, "stats.yml");
        initializeDirectory(plugin, "layouts", List.of("default.yml", "extra.yml"));
        initializeDirectory(plugin, "skill", List.of("skill.yml", "example.yml"));
    }

    private static void initializeDirectory(JavaPlugin plugin, String name, List<String> examples) {
        // Examples are only installed for a new directory. Deleted/renamed examples stay deleted.
        if (new File(plugin.getDataFolder(), name).exists()) return;
        for (String example : examples) saveIfMissing(plugin, name + "/" + example);
    }

    private static void saveIfMissing(JavaPlugin plugin, String path) {
        if (!new File(plugin.getDataFolder(), path).exists()) plugin.saveResource(path, false);
    }

    public static List<File> yamlFiles(File directory) {
        File[] files = directory.listFiles(file -> {
            String name = file.getName().toLowerCase(Locale.ROOT);
            return file.isFile() && (name.endsWith(".yml") || name.endsWith(".yaml"));
        });
        if (files == null) return List.of();
        return Arrays.stream(files)
                .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(File::getName))
                .toList();
    }

    public static YamlConfiguration load(File file, Logger logger) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
            return config;
        } catch (IOException | InvalidConfigurationException ex) {
            logger.severe("Cannot load config " + file.getPath() + ": " + ex.getMessage());
            return null;
        }
    }
}

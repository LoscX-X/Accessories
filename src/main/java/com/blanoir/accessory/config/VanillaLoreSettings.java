package com.blanoir.accessory.config;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Immutable configuration. Loaders do not read YAML or select providers during a player refresh. */
public record VanillaLoreSettings(boolean enabled, List<Mapping> mappings) {
    public VanillaLoreSettings { mappings = List.copyOf(mappings); }
    public record Mapping(String id, NamespacedKey attribute, List<String> keywords) {
        public Mapping { keywords = List.copyOf(keywords); }
    }

    public static VanillaLoreSettings read(ConfigurationSection config) {
        List<Mapping> result = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("attribute.lore.mappings");
        if (section != null) for (String id : section.getKeys(false)) {
            ConfigurationSection mapping = section.getConfigurationSection(id);
            if (mapping == null) continue;
            String raw = mapping.getString("attribute", id).trim().toLowerCase(Locale.ROOT);
            NamespacedKey key = NamespacedKey.fromString(raw.contains(":") ? raw : "minecraft:" + raw);
            if (key == null) throw new IllegalArgumentException("Invalid vanilla attribute key for mapping " + id + ": " + raw);
            List<String> keywords = mapping.getStringList("keywords");
            result.add(new Mapping(id, key, keywords.isEmpty() ? List.of(id) : keywords));
        }
        return new VanillaLoreSettings(config.getBoolean("attribute.lore.enable", false), result);
    }
}

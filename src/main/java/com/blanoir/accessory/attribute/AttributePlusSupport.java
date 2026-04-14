package com.blanoir.accessory.attribute;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.serverct.ersha.api.AttributeAPI;
import org.serverct.ersha.attribute.data.AttributeData;

import java.util.LinkedHashSet;
import java.util.Set;

final class AttributePlusSupport {

    private static final String SOURCE_PREFIX = "accessory:attribute_plus/slot";
    private static final String PLAYER_SOURCES_PDC_KEY = "attribute_plus_sources";

    private final JavaPlugin plugin;
    private AttributeData currentData;
    private final Set<String> currentSources = new LinkedHashSet<>();

    AttributePlusSupport(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void begin(Player player) {
        currentData = AttributeAPI.getAttrData(player);
        clearAppliedSources(player);
        currentSources.clear();
    }

    void apply(Player player, ItemStack item, int slot) {
        if (currentData == null || item == null) return;

        String source = SOURCE_PREFIX + slot;
        AttributeAPI.addSourceAttribute(currentData, source, item, true);
        currentSources.add(source);
    }

    void finish(Player player) {
        saveAppliedSources(player);
        if (currentData != null) {
            AttributeAPI.updateAttribute(player);
        }
        currentData = null;
        currentSources.clear();
    }

    private void clearAppliedSources(Player player) {
        if (currentData == null) return;

        NamespacedKey key = new NamespacedKey(plugin, PLAYER_SOURCES_PDC_KEY);
        String raw = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return;

        for (String source : raw.split("\\n")) {
            String cleaned = source.trim();
            if (!cleaned.isEmpty()) {
                AttributeAPI.takeSourceAttribute(currentData, cleaned);
            }
        }
    }

    private void saveAppliedSources(Player player) {
        NamespacedKey key = new NamespacedKey(plugin, PLAYER_SOURCES_PDC_KEY);
        if (currentSources.isEmpty()) {
            player.getPersistentDataContainer().remove(key);
            return;
        }

        player.getPersistentDataContainer().set(
                key,
                PersistentDataType.STRING,
                String.join("\n", currentSources)
        );
    }
}

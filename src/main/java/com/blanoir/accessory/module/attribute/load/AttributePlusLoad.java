package com.blanoir.accessory.module.attribute.load;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.serverct.ersha.api.AttributeAPI;
import org.serverct.ersha.attribute.data.AttributeData;

import java.util.LinkedHashSet;
import java.util.Set;

final class AttributePlusLoad extends AbstractAttributeLoader<AttributePlusLoad.Context> {

    private static final String SOURCE_PREFIX = "accessory:attribute_plus/slot";
    private static final String PLAYER_SOURCES_PDC_KEY = "attribute_plus_sources";

    private final JavaPlugin plugin;
    record Context(Player player, AttributeData data, Set<String> sources) { }

    AttributePlusLoad(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected Context begin(Player player) {
        AttributeData data = AttributeAPI.getAttrData(player);
        if (data == null) return null;
        clearAppliedSources(player, data);
        return new Context(player, data, new LinkedHashSet<>());
    }

    @Override
    protected void apply(Context context, ItemStack item, int slot) {
        String source = SOURCE_PREFIX + slot;
        AttributeAPI.addSourceAttribute(context.data(), source, item);
        context.sources().add(source);
    }

    @Override
    protected void finish(Context context) {
        saveAppliedSources(context.player(), context.sources());
        AttributeAPI.updateAttribute(context.player());
    }

    private void clearAppliedSources(Player player, AttributeData data) {
        NamespacedKey key = new NamespacedKey(plugin, PLAYER_SOURCES_PDC_KEY);
        String raw = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return;

        for (String source : raw.split("\\n")) {
            String cleaned = source.trim();
            if (!cleaned.isEmpty()) {
                AttributeAPI.takeSourceAttribute(data, cleaned);
            }
        }
    }

    private void saveAppliedSources(Player player, Set<String> currentSources) {
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

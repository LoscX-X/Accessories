package com.blanoir.accessory.module.lifecycle;

import com.blanoir.accessory.utils.LoreUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.Set;

/** Scoreboard tags are equipment effects, independent of the selected attribute provider. */
final class AccessoryTags {
    private final NamespacedKey key;

    AccessoryTags(JavaPlugin plugin) { key = new NamespacedKey(plugin, "applied_tags"); }

    void rebuild(Player player, ItemStack[] contents) {
        String previous = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (previous != null) for (String tag : previous.split("\n")) {
            if (!tag.isBlank()) player.removeScoreboardTag(tag);
        }
        Set<String> tags = new LinkedHashSet<>();
        for (ItemStack item : contents) for (String line : LoreUtils.plainLore(item)) {
            int start = 0;
            while (true) {
                int left = line.indexOf('[', start), right = left < 0 ? -1 : line.indexOf(']', left + 1);
                if (right < 0) break;
                String tag = line.substring(left + 1, right).trim();
                if (!tag.isEmpty()) tags.add(tag);
                start = right + 1;
            }
        }
        tags.forEach(player::addScoreboardTag);
        if (tags.isEmpty()) player.getPersistentDataContainer().remove(key);
        else player.getPersistentDataContainer().set(key, PersistentDataType.STRING, String.join("\n", tags));
    }
}

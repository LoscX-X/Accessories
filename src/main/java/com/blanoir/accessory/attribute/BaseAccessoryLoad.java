package com.blanoir.accessory.attribute;

import com.blanoir.accessory.utils.LoreUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

abstract class BaseAccessoryLoad implements AccessoryLoadHandler {

    protected static final String PREFIX = "accessory:";
    private static final String ATTR_PATH_PREFIX = "accessory/";
    private static final String PLAYER_TAGS_PDC_KEY = "applied_tags";

    protected final JavaPlugin plugin;

    protected BaseAccessoryLoad(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public final void rebuildFromInventory(Player player, Inventory inventory) {
        clearTraitModifiers(player);
        clearAccessoryAttributes(player);
        clearAppliedTags(player);

        Set<String> currentTags = new LinkedHashSet<>();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;

            applyTraitModifiers(player, item, slot);
            applyItemAttributes(player, item, slot);

            for (String tag : parseTags(item)) {
                player.addScoreboardTag(tag);
                currentTags.add(tag);
            }
        }

        saveAppliedTags(player, currentTags);
    }

    protected abstract void clearTraitModifiers(Player player);

    protected abstract void applyTraitModifiers(Player player, ItemStack item, int slot);

    private void clearAppliedTags(Player player) {
        NamespacedKey key = new NamespacedKey(plugin, PLAYER_TAGS_PDC_KEY);
        String raw = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return;

        for (String tag : raw.split("\\n")) {
            String cleaned = tag.trim();
            if (!cleaned.isEmpty()) {
                player.removeScoreboardTag(cleaned);
            }
        }
    }

    private void saveAppliedTags(Player player, Set<String> tags) {
        NamespacedKey key = new NamespacedKey(plugin, PLAYER_TAGS_PDC_KEY);
        if (tags.isEmpty()) {
            player.getPersistentDataContainer().remove(key);
            return;
        }

        player.getPersistentDataContainer().set(
                key,
                PersistentDataType.STRING,
                String.join("\n", tags)
        );
    }

    private void clearAccessoryAttributes(Player player) {
        for (Attribute attr : Registry.ATTRIBUTE) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) continue;

            inst.getModifiers().stream()
                    .filter(mod -> {
                        NamespacedKey key = mod.getKey();
                        return key.getNamespace().equalsIgnoreCase(plugin.getName()) && key.getKey().startsWith(ATTR_PATH_PREFIX);
                    })
                    .toList()
                    .forEach(inst::removeModifier);
        }
    }

    private void applyItemAttributes(Player player, ItemStack item, int slot) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var map = meta.getAttributeModifiers();
        if (map == null || map.isEmpty()) return;

        for (var entry : map.entries()) {
            Attribute attr = entry.getKey();
            AttributeModifier original = entry.getValue();

            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) continue;

            AttributeModifier copy = getAttributeModifier(slot, original, attr);
            inst.addModifier(copy);
        }
    }

    @NotNull
    private AttributeModifier getAttributeModifier(int slot, AttributeModifier original, Attribute attr) {
        NamespacedKey origKey = original.getKey();
        String origPart = origKey.getNamespace() + "/" + origKey.getKey();

        String origHash = Integer.toHexString(origPart.hashCode());
        NamespacedKey attrKey = attr.getKey();
        String attrPart = attrKey.getNamespace() + "/" + attrKey.getKey();

        NamespacedKey myKey = new NamespacedKey(
                plugin,
                ATTR_PATH_PREFIX + "slot" + slot + "/" + Integer.toHexString(attrPart.hashCode()) + "/" + origHash
        );

        AttributeModifier copy = new AttributeModifier(myKey, original.getAmount(), original.getOperation());
        return copy;
    }

    private List<String> parseTags(ItemStack item) {
        List<String> lore = LoreUtils.plainLore(item);
        List<String> out = new ArrayList<>();
        for (String line : lore) {
            String trimmed = line.trim();
            int start = 0;
            while (true) {
                int left = trimmed.indexOf('[', start);
                if (left == -1) break;
                int right = trimmed.indexOf(']', left + 1);
                if (right == -1) break;

                String tag = trimmed.substring(left + 1, right).trim();
                if (!tag.isEmpty()) {
                    out.add(tag);
                }
                start = right + 1;
            }
        }
        return out;
    }
}

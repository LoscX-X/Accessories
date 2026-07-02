package com.blanoir.accessory.module.inventory.ui;

import com.blanoir.accessory.Accessory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AccessoryInventoryItem {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Accessory plugin;

    public AccessoryInventoryItem(Accessory plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public ItemStack frameItem(ConfigurationSection section, int currentPage, int totalPages) {
        return markedItem(section, currentPage, totalPages, "locked");
    }

    public ItemStack disabledItem(ConfigurationSection section, int currentPage, int totalPages) {
        return markedItem(section, currentPage, totalPages, "disabled", "locked");
    }

    public ItemStack previousPageItem(ConfigurationSection section, int currentPage, int totalPages) {
        return markedItem(section, currentPage, totalPages, "pre_page");
    }

    public ItemStack nextPageItem(ConfigurationSection section, int currentPage, int totalPages) {
        return markedItem(section, currentPage, totalPages, "next_page");
    }

    public ItemStack markedItem(ConfigurationSection section,
                                int currentPage,
                                int totalPages,
                                String... markers) {
        String typeName = section == null
                ? "BLACK_STAINED_GLASS_PANE"
                : section.getString("type", "BLACK_STAINED_GLASS_PANE");

        Material material = Material.matchMaterial(typeName, false);
        if (material == null || !material.isItem()) {
            material = Material.BLACK_STAINED_GLASS_PANE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        boolean hideTooltip = section == null || section.getBoolean("hide-tooltip", true);
        try {
            meta.setHideTooltip(hideTooltip);
        } catch (NoSuchMethodError ignored) {
            // Older API compatibility.
        }

        int customModelData = section == null ? -1 : section.getInt("custom-model-data", -1);
        if (customModelData > 0) {
            var customModelDataComponent = meta.getCustomModelDataComponent();
            customModelDataComponent.setFloats(List.of((float) customModelData));
            meta.setCustomModelDataComponent(customModelDataComponent);
        }

        String name = section == null ? null : section.getString("name");
        if (name != null && !name.isBlank()) {
            meta.displayName(parse(name, currentPage, totalPages));
        }

        List<String> rawLore = section == null ? List.of() : section.getStringList("lore");
        if (!rawLore.isEmpty()) {
            List<Component> lore = new ArrayList<>(rawLore.size());
            for (String line : rawLore) {
                lore.add(parse(line, currentPage, totalPages));
            }
            meta.lore(lore);
        }

        for (String marker : markers) {
            if (marker == null || marker.isBlank()) {
                continue;
            }

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, marker),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
        }

        item.setItemMeta(meta);
        return item;
    }

    private Component parse(String raw, int currentPage, int totalPages) {
        return MINI_MESSAGE.deserialize(
                raw.replace("{page}", String.valueOf(currentPage))
                        .replace("{max_page}", String.valueOf(totalPages))
        );
    }
}
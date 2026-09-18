package com.blanoir.accessory.module.attribute.load;

import com.blanoir.accessory.config.VanillaLoreSettings;
import com.blanoir.accessory.module.attribute.parse.VanillaLoreParser;
import com.blanoir.accessory.utils.LoreUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Applies only Bukkit modifiers. Text parsing and configuration are separate components. */
public final class VanillaAttributeLoad extends AbstractAttributeLoader<Player> {
    private final JavaPlugin plugin;
    private final VanillaLoreParser lore;

    public VanillaAttributeLoad(JavaPlugin plugin, VanillaLoreSettings settings) {
        this.plugin = plugin;
        lore = new VanillaLoreParser(settings);
    }

    @Override
    protected Player begin(Player player) {
        for (Attribute attr : Registry.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attr);
            if (instance == null) continue;
            instance.getModifiers().stream().filter(modifier -> {
                NamespacedKey key = modifier.getKey();
                return key.getNamespace().equalsIgnoreCase(plugin.getName()) && key.getKey().startsWith("accessory/");
            }).toList().forEach(instance::removeModifier);
        }
        return player;
    }

    @Override
    protected void apply(Player player, ItemStack item, int slot) {
        var meta = item.getItemMeta();
        var attributes = meta == null ? null : meta.getAttributeModifiers();
        if (attributes != null) for (var entry : attributes.entries()) {
            AttributeModifier original = entry.getValue();
            String path = "accessory/slot" + slot + "/" + entry.getKey().getKey().asString().replace(':', '/')
                    + "/" + original.getKey().asString().replace(':', '/');
            add(player, entry.getKey(), path, original.getAmount(), original.getOperation());
        }
        for (var value : lore.parse(LoreUtils.plainLore(item))) {
            Attribute attribute = Registry.ATTRIBUTE.get(value.attribute());
            if (attribute == null) continue;
            String path = "accessory/lore/slot" + slot + "/" + Integer.toHexString(value.id().hashCode())
                    + "/" + value.attribute().asString().replace(':', '/');
            add(player, attribute, path, value.amount(), value.operation());
        }
    }

    private void add(Player player, Attribute attribute, String path, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.addModifier(new AttributeModifier(new NamespacedKey(plugin, path), amount, operation));
    }
}

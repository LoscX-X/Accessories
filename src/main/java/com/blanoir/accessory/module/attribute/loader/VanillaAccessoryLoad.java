package com.blanoir.accessory.module.attribute.loader;

import com.blanoir.accessory.utils.LoreUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class VanillaAccessoryLoad extends BaseAccessoryLoad {

    private static final Pattern VALUE_PATTERN = Pattern.compile("([+\\-xX*×])\\s*([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+))");
    private static final String CONFIG_PATH = "vanilla-lore-attributes";

    VanillaAccessoryLoad(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void clearTraitModifiers(Player player) {
        // No AuraSkills: nothing to clear.
    }

    @Override
    protected void applyTraitModifiers(Player player, ItemStack item, int slot) {
        applyLoreAttributeMappings(player, item, slot);
    }

    private void applyLoreAttributeMappings(Player player, ItemStack item, int slot) {
        if (!plugin.getConfig().getBoolean(CONFIG_PATH + ".enable", false)) return;

        ConfigurationSection mappings = plugin.getConfig().getConfigurationSection(CONFIG_PATH + ".mappings");
        if (mappings == null) return;

        List<String> lore = LoreUtils.plainLore(item);
        if (lore.isEmpty()) return;

        for (String mappingKey : mappings.getKeys(false)) {
            ConfigurationSection mapping = mappings.getConfigurationSection(mappingKey);
            if (mapping == null) continue;

            Attribute attribute = parseAttribute(mapping.getString("attribute", mappingKey));
            if (attribute == null) {
                if (plugin.getConfig().getBoolean("debug-mode", false)) {
                    plugin.getLogger().warning("Invalid vanilla lore attribute mapping: " + mappingKey);
                }
                continue;
            }

            List<String> keywords = mapping.getStringList("keywords");
            if (keywords.isEmpty()) {
                keywords = List.of(mappingKey);
            }

            for (String line : lore) {
                if (!containsAnyKeyword(line, keywords)) continue;

                Matcher matcher = VALUE_PATTERN.matcher(line);
                if (!matcher.find()) continue;

                AttributeModifier.Operation operation = parseOperation(matcher.group(1));
                double amount = Double.parseDouble(matcher.group(2));
                if (operation != AttributeModifier.Operation.ADD_NUMBER) {
                    amount -= 1.0D;
                }

                applyLoreModifier(player, attribute, operation, amount, slot, mappingKey, line);
                break;
            }
        }
    }

    private boolean containsAnyKeyword(String line, List<String> keywords) {
        String lowerLine = line.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && lowerLine.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Attribute parseAttribute(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (!key.contains(":")) {
            key = "minecraft:" + key;
        }

        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        if (namespacedKey == null) return null;

        return Registry.ATTRIBUTE.get(namespacedKey);
    }

    private AttributeModifier.Operation parseOperation(String raw) {
        return ("x".equalsIgnoreCase(raw) || "*".equals(raw) || "×".equals(raw))
                ? AttributeModifier.Operation.MULTIPLY_SCALAR_1
                : AttributeModifier.Operation.ADD_NUMBER;
    }

    private void applyLoreModifier(Player player, Attribute attribute, AttributeModifier.Operation operation,
                                   double amount, int slot, String mappingKey, String line) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        NamespacedKey attributeKey = attribute.getKey();
        String path = "accessory/lore/slot" + slot + "/"
                + Integer.toHexString((mappingKey + attributeKey.asString() + line).hashCode());
        AttributeModifier modifier = new AttributeModifier(new NamespacedKey(plugin, path), amount, operation);
        instance.addModifier(modifier);
    }
}

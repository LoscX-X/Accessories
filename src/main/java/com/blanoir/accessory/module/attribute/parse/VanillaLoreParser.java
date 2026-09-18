package com.blanoir.accessory.module.attribute.parse;

import com.blanoir.accessory.config.VanillaLoreSettings;
import com.blanoir.accessory.utils.LoreUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Converts text to values; no player, registry, storage or plugin calls. */
public final class VanillaLoreParser {
    private static final Pattern VALUE = Pattern.compile("([+\\-xX*×])\\s*([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+))");
    private final VanillaLoreSettings settings;
    public VanillaLoreParser(VanillaLoreSettings settings) { this.settings = settings; }
    public record Value(String id, NamespacedKey attribute, double amount, AttributeModifier.Operation operation) { }

    public List<Value> parse(List<String> lore) {
        if (!settings.enabled()) return List.of();
        List<Value> out = new ArrayList<>();
        for (var mapping : settings.mappings()) for (String line : lore) {
            String lower = line.toLowerCase(Locale.ROOT);
            boolean matches = mapping.keywords().stream().anyMatch(key -> !key.isBlank()
                    && LoreUtils.containsKeyword(lower, key.toLowerCase(Locale.ROOT)));
            if (!matches) continue;
            var matcher = VALUE.matcher(line);
            if (!matcher.find()) continue;
            String operator = matcher.group(1);
            double amount = Double.parseDouble(matcher.group(2));
            boolean multiply = "x".equalsIgnoreCase(operator) || "*".equals(operator) || "×".equals(operator);
            if (multiply) amount -= 1.0;
            else if ("-".equals(operator)) amount = -amount;
            if (!Double.isFinite(amount)) continue;
            out.add(new Value(mapping.id(), mapping.attribute(), amount, multiply
                    ? AttributeModifier.Operation.MULTIPLY_SCALAR_1 : AttributeModifier.Operation.ADD_NUMBER));
            break;
        }
        return List.copyOf(out);
    }
}

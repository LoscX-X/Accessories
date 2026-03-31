package com.blanoir.accessory.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class LoreUtils {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private LoreUtils() {}

    public static List<String> plainLore(ItemStack item) {
        if (item == null) return Collections.emptyList();

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Collections.emptyList();

        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) return Collections.emptyList();

        List<String> out = new ArrayList<>(lore.size());
        for (Component component : lore) {
            if (component == null) continue;

            String plain = PLAIN.serialize(component).trim();
            if (!plain.isEmpty()) {
                out.add(plain);
            }
        }

        return out.isEmpty() ? Collections.emptyList() : out;
    }

    public static boolean matchesAnyKeyword(List<String> itemLore, List<String> normalizedKeywords) {
        if (normalizedKeywords == null || normalizedKeywords.isEmpty()) return true;
        if (itemLore == null || itemLore.isEmpty()) return false;

        for (String line : itemLore) {
            if (line == null || line.isEmpty()) continue;

            String lowerLine = line.toLowerCase(Locale.ROOT);
            for (String keyword : normalizedKeywords) {
                if (lowerLine.contains(keyword)) {
                    return true;
                }
            }
        }

        return false;
    }
}
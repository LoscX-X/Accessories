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

    private LoreUtils() {
    }

    public static List<String> plainLore(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Collections.emptyList();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Collections.emptyList();
        }

        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>(lore.size());
        for (Component component : lore) {
            if (component == null) {
                continue;
            }

            String plain = normalize(PLAIN.serialize(component));
            if (!plain.isEmpty()) {
                out.add(plain);
            }
        }

        return out.isEmpty() ? Collections.emptyList() : out;
    }

    public static boolean matchesAnyKeyword(List<String> itemLore, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }

        if (itemLore == null || itemLore.isEmpty()) {
            return false;
        }

        List<String> normalizedKeywords = new ArrayList<>();
        for (String keyword : keywords) {
            String normalized = normalize(keyword);
            if (!normalized.isEmpty()) {
                normalizedKeywords.add(normalized);
            }
        }

        if (normalizedKeywords.isEmpty()) {
            return true;
        }

        for (String line : itemLore) {
            String normalizedLine = normalize(line);
            if (normalizedLine.isEmpty()) {
                continue;
            }

            for (String keyword : normalizedKeywords) {
                if (normalizedLine.contains(keyword)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }

        return text.trim().toLowerCase(Locale.ROOT);
    }
}
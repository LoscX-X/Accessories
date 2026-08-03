package com.blanoir.accessory.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LoreUtils {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /**
     * 严格匹配 lore 属性词条：带 +/-/x/×/* 符号的数值、带 % 的数值，或 %xxx% 占位符。
     * 只有包含这些词条才认为该饰品携带 lore 属性。
     */
    private static final Pattern ATTRIBUTE_ENTRY = Pattern.compile(
            "[+\\-×xX*]\\s*\\d+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?\\s*%|%[^%\\s]+%"
    );

    private LoreUtils() {
    }

    /**
     * 判断物品 lore 中是否包含属性词条。
     */
    public static boolean hasAttributeLore(ItemStack item) {
        for (String line : plainLore(item)) {
            if (ATTRIBUTE_ENTRY.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 严格关键词匹配：关键词必须作为独立词条出现（前后不能是字母/数字/下划线），
     * 不能是其他单词的一部分。
     */
    public static boolean containsKeyword(String line, String keyword) {
        if (line == null || keyword == null || keyword.isEmpty()) {
            return false;
        }

        int from = 0;
        while (true) {
            int index = line.indexOf(keyword, from);
            if (index < 0) {
                return false;
            }

            int end = index + keyword.length();
            boolean leftBound = index == 0 || !isWordChar(line.charAt(index - 1));
            boolean rightBound = end >= line.length() || !isWordChar(line.charAt(end));
            if (leftBound && rightBound) {
                return true;
            }
            from = index + 1;
        }
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
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
                if (containsKeyword(normalizedLine, keyword)) {
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

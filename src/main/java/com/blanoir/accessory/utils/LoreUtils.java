// LoreUtils.java
package com.blanoir.accessory.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LoreUtils {
    private LoreUtils() {}

    public static List<String> plainLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Collections.emptyList();
        ItemMeta meta = item.getItemMeta();
        try {
            var lore = (List<Component>) ItemMeta.class.getMethod("lore").invoke(meta);
            if (lore == null) return Collections.emptyList();
            var plain = PlainTextComponentSerializer.plainText();
            List<String> out = new ArrayList<>(lore.size());
            for (Component c : lore) out.add(plain.serialize(c).trim());
            return out;
        } catch (ReflectiveOperationException ignored) {
            try {
                List<String> legacy = (List<String>) ItemMeta.class.getMethod("getLore").invoke(meta);
                return legacy != null ? legacy : Collections.emptyList();
            } catch (ReflectiveOperationException e) {
                return Collections.emptyList();
            }
        }
    }

    //是否至少命中一个关键词
    public static boolean matchesAnyKeyword(List<String> itemLore, List<String> requiredKeywords) {
        if (requiredKeywords == null || requiredKeywords.isEmpty()) return true; // 没规则=放行
        for (String need : requiredKeywords) {
            String needle = need.trim().toLowerCase();
            for (String line : itemLore) {
                if (line.toLowerCase().contains(needle)) return true;
            }
        }
        return false;
    }
}

package com.blanoir.accessory.module.inventory;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.utils.LoreUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 同一物品最多可装备次数的检测（skilled_item 数量限制）。
 *
 * <p>配置路径: config.yml 的 item-limits 节点。</p>
 */
public final class ItemLimitManager {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Accessory plugin;
    private final NamespacedKey accItemId;
    private final NamespacedKey legacyItemId;
    private final List<LimitRule> rules = new ArrayList<>();

    public ItemLimitManager(Accessory plugin) {
        this.plugin = plugin;
        this.accItemId = new NamespacedKey(plugin, "acc_item_id");
        this.legacyItemId = new NamespacedKey(plugin, "dun_item_id");
    }

    public void reload() {
        rules.clear();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("item-limits");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection ruleSection = section.getConfigurationSection(key);
            if (ruleSection == null) {
                continue;
            }

            int max = ruleSection.getInt("max", 0);
            if (max <= 0) {
                continue;
            }

            boolean idExplicit = ruleSection.isString("id");
            String id = ruleSection.getString("id", key).trim();
            List<String> names = stringList(ruleSection, "name");
            List<String> lore = stringList(ruleSection, "lore");
            String material = ruleSection.getString("material", "").trim();

            if (!idExplicit) {
                // 键名同时作为显示名 / lore 关键词兜底，方便直接写中文名。
                names.add(key);
                lore.add(key);
            }

            rules.add(new LimitRule(id, names, lore, material, max));
        }
    }

    /**
     * 同一物品可同时装备/生效的最大数量；未配置或 max<=0 返回 0（不限制）。
     */
    public int maxFor(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        for (LimitRule rule : rules) {
            if (matches(item, rule)) {
                return rule.max();
            }
        }
        return 0;
    }

    /**
     * 指定槽位是否在数量限制内（按槽位顺序，前 max 个生效）。
     */
    public boolean isSlotAllowed(ItemStack[] contents, int slot) {
        if (contents == null || slot < 0 || slot >= contents.length) {
            return false;
        }

        ItemStack item = contents[slot];
        if (item == null || item.getType().isAir()) {
            return false;
        }

        int max = maxFor(item);
        if (max <= 0) {
            return true;
        }

        int count = 0;
        for (int i = 0; i <= slot; i++) {
            if (sameItem(contents[i], item)) {
                count++;
            }
        }
        return count <= max;
    }

    /**
     * 返回所有在数量限制内允许生效的槽位。
     */
    public Set<Integer> allowedSlots(ItemStack[] contents) {
        Set<Integer> out = new LinkedHashSet<>();
        if (contents == null) {
            return out;
        }
        for (int i = 0; i < contents.length; i++) {
            if (isSlotAllowed(contents, i)) {
                out.add(i);
            }
        }
        return out;
    }

    /**
     * 若把 item 放到 targetSlot（排除该槽位上已有的同物品），是否会超过数量限制。
     */
    public boolean wouldExceedLimit(ItemStack[] contents, int targetSlot, ItemStack item) {
        if (contents == null || item == null || item.getType().isAir()) {
            return false;
        }

        int max = maxFor(item);
        if (max <= 0) {
            return false;
        }

        int count = 0;
        for (int i = 0; i < contents.length; i++) {
            if (i == targetSlot) {
                continue;
            }
            if (sameItem(contents[i], item)) {
                count++;
            }
        }
        return count >= max;
    }

    public int countEquipped(ItemStack[] contents, ItemStack item) {
        int count = 0;
        if (contents == null || item == null) {
            return 0;
        }
        for (ItemStack other : contents) {
            if (sameItem(other, item)) {
                count++;
            }
        }
        return count;
    }

    private boolean matches(ItemStack item, LimitRule rule) {
        String itemId = itemId(item);
        if (itemId != null && !rule.id().isBlank() && itemId.equalsIgnoreCase(rule.id())) {
            return true;
        }
        if (!rule.names().isEmpty() && matchesName(item, rule.names())) {
            return true;
        }
        if (!rule.lore().isEmpty() && matchesLore(item, rule.lore())) {
            return true;
        }
        return !rule.material().isBlank() && item.getType().name().equalsIgnoreCase(rule.material());
    }

    private boolean matchesLore(ItemStack item, List<String> keywords) {
        for (String line : LoreUtils.plainLore(item)) {
            String lowerLine = line.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isBlank()
                        && lowerLine.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesName(ItemStack item, List<String> keywords) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || meta.displayName() == null) {
            return false;
        }

        String name = PLAIN.serialize(meta.displayName()).toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && name.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean sameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.getType() != b.getType()) {
            return false;
        }

        String idA = itemId(a);
        String idB = itemId(b);
        if (idA != null && idB != null) {
            return idA.equalsIgnoreCase(idB);
        }

        // 有一方还没写 PDC 时，按 材料+显示名+lore 判断是否同一物品。
        return identityKey(a).equals(identityKey(b));
    }

    private String itemId(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String id = meta.getPersistentDataContainer().get(accItemId, PersistentDataType.STRING);
        if (id != null && !id.isBlank()) {
            return id;
        }
        String legacy = meta.getPersistentDataContainer().get(legacyItemId, PersistentDataType.STRING);
        return legacy != null && !legacy.isBlank() ? legacy : null;
    }

    private String identityKey(ItemStack item) {
        StringBuilder key = new StringBuilder(item.getType().name());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return key.toString();
        }

        if (meta.hasDisplayName() && meta.displayName() != null) {
            key.append('|').append(PLAIN.serialize(meta.displayName()));
        }

        List<Component> lore = meta.lore();
        if (lore != null) {
            for (Component line : lore) {
                if (line != null) {
                    key.append('|').append(PLAIN.serialize(line));
                }
            }
        }
        return key.toString();
    }

    private List<String> stringList(ConfigurationSection section, String path) {
        if (section.isString(path)) {
            String value = section.getString(path, "").trim();
            return value.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(value));
        }
        if (section.isList(path)) {
            List<String> out = new ArrayList<>();
            for (String value : section.getStringList(path)) {
                if (value != null && !value.isBlank()) {
                    out.add(value.trim());
                }
            }
            return out;
        }
        return new ArrayList<>();
    }

    private record LimitRule(String id, List<String> names, List<String> lore, String material, int max) {
    }
}

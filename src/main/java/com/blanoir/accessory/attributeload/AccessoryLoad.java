package com.blanoir.accessory.attributeload;

import com.blanoir.accessory.utils.LoreUtils;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.AuraSkillsBukkit;
import dev.aurelium.auraskills.api.item.ItemManager;
import dev.aurelium.auraskills.api.item.ModifierType;
import dev.aurelium.auraskills.api.trait.TraitModifier;
import dev.aurelium.auraskills.api.user.SkillsUser;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AccessoryLoad {

    // AuraSkills Trait 的前缀（你原来的）
    private static final String PREFIX = "accessory:";

    // 我们写入玩家 AttributeModifier 的 key path 前缀（用于清理）
    // 实际存储为：<pluginNamespace>:accessory/slotX/...
    private static final String ATTR_PATH_PREFIX = "accessory/";

    private final JavaPlugin plugin;
    private final AuraSkillsApi api;
    private final ItemManager itemManager;

    public AccessoryLoad(JavaPlugin plugin) {
        this.plugin = plugin;
        this.api = AuraSkillsApi.get();
        this.itemManager = AuraSkillsBukkit.get().getItemManager();
    }

    public void rebuildFromInventory(Player p, Inventory inv) {
        SkillsUser user = api.getUser(p.getUniqueId());
        if (user == null || !user.isLoaded()) return;

        // 1) 清除旧 trait modifier
        for (String name : Map.copyOf(user.getTraitModifiers()).keySet()) {
            if (name.startsWith(PREFIX)) {
                user.removeTraitModifier(name);
            }
        }

        // 2) 清除旧 accessory AttributeModifier（我们自己加的）
        clearAccessoryAttributes(p);

        // 3) 清除旧 tag
        for (String t : new ArrayList<>(p.getScoreboardTags())) {
            if (t.startsWith("acc_")) {
                p.removeScoreboardTag(t);
            }
        }

        // 4) 循环物品：Trait + Attribute + Tags
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;

            // ① Trait modifier
            List<TraitModifier> itemMods = itemManager.getTraitModifiers(item, ModifierType.ITEM);
            List<TraitModifier> armorMods = itemManager.getTraitModifiers(item, ModifierType.ARMOR);
            applyAll(user, itemMods, slot);
            applyAll(user, armorMods, slot);

            // ② Item 自带 Attribute（忽略 slot，饰品装上就生效）
            applyItemAttributes(p, item, slot);

            // ③ Lore Tags
            for (String tag : parseTags(item)) {
                p.addScoreboardTag(tag);
            }
        }
    }

    private void applyAll(SkillsUser user, List<TraitModifier> mods, int slot) {
        if (mods == null || mods.isEmpty()) return;

        for (TraitModifier m : mods) {
            // 结构例： accessory:slot3/heal_regeneration
            String traitId = m.trait().toString();
            String name = PREFIX + "slot" + slot + "/" + traitId;

            TraitModifier copy = new TraitModifier(name, m.trait(), m.value(), m.operation());
            copy.setNonPersistent();
            user.addTraitModifier(copy);
        }
    }

    // 清除玩家身上所有我们加的 accessory attribute modifiers
    private void clearAccessoryAttributes(Player p) {
        for (Attribute attr : Registry.ATTRIBUTE) { // ✅ 替代 Attribute.values()
            AttributeInstance inst = p.getAttribute(attr);
            if (inst == null) continue;

            inst.getModifiers().stream()
                    .filter(mod -> {
                        NamespacedKey k = mod.getKey();
                        return k != null
                                && k.getNamespace().equalsIgnoreCase(plugin.getName())
                                && k.getKey().startsWith(ATTR_PATH_PREFIX);
                    })
                    .toList()
                    .forEach(inst::removeModifier);
        }
    }

    // 读取 item 上的 AttributeModifiers，并以我们自己的 NamespacedKey 复制到玩家身上
    private void applyItemAttributes(Player p, ItemStack item, int slot) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var map = meta.getAttributeModifiers();
        if (map == null || map.isEmpty()) return;

        for (var entry : map.entries()) {
            Attribute attr = entry.getKey();
            AttributeModifier original = entry.getValue();

            AttributeInstance inst = p.getAttribute(attr);
            if (inst == null) continue;

            // 原始 modifier 的 key（新版本用 key 标识，可能为 null，做兜底）
            NamespacedKey origKey = original.getKey();
            String origPart = (origKey == null)
                    ? "no_key"
                    : (origKey.getNamespace() + "/" + origKey.getKey());

            // 为了避免 path 太长/包含奇怪字符：做个稳定 hash
            String origHash = Integer.toHexString(origPart.hashCode());

            // attribute 的 key（替代 attr.name()）
            NamespacedKey attrKey = attr.getKey();
            String attrPart = attrKey.getNamespace() + "/" + attrKey.getKey();

            // 我们自己的 modifier key：<plugin>:accessory/slotX/<attrHash>/<origHash>
            NamespacedKey myKey = new NamespacedKey(
                    plugin,
                    ATTR_PATH_PREFIX + "slot" + slot + "/" +
                            Integer.toHexString(attrPart.hashCode()) + "/" + origHash
            );

            // ✅ 新构造器：用 NamespacedKey + amount + operation（避免 UUID/name 弃用）
            AttributeModifier copy = new AttributeModifier(
                    myKey,
                    original.getAmount(),
                    original.getOperation()
            );

            inst.addModifier(copy);
        }
    }

    // 为玩家添加 Tags
    private List<String> parseTags(ItemStack item) {
        List<String> lore = LoreUtils.plainLore(item);
        List<String> out = new ArrayList<>();
        boolean inTags = false;

        for (String line : lore) {
            line = line.trim();
            if (line.equalsIgnoreCase("tags:")) {
                inTags = true;
                continue;
            }
            if (!inTags) continue;

            int start = 0;
            while (true) {
                int l = line.indexOf('[', start);
                if (l == -1) break;
                int r = line.indexOf(']', l + 1);
                if (r == -1) break;

                String tag = line.substring(l + 1, r).trim();
                if (!tag.isEmpty()) {
                    out.add("acc_" + tag);
                }
                start = r + 1;
            }
        }
        return out;
    }
}

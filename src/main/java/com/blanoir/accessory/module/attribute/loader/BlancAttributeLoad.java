package com.blanoir.accessory.module.attribute.loader;

import com.blanoir.blancattribute.BlancAttribute;
import com.blanoir.blancattribute.api.AttributeModifier;
import com.blanoir.blancattribute.api.AttributeUser;
import com.blanoir.blancattribute.api.BlancAttributeApi;
import com.blanoir.blancattribute.core.lore.LoreParser;
import com.blanoir.blancattribute.core.lore.ParsedLore;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

final class BlancAttributeLoad {

    private static final String MODIFIER_TAG = "blanc_accessory";
    private static final String MODIFIER_PREFIX = "accessory:slot";
    private static final NamespacedKey ACCESSORY_PDC = new NamespacedKey("bla", "accessory");

    private BlancAttributeApi api;
    private BlancAttribute blancAttribute;
    private AttributeUser user;

    void begin(Player player) {
        api = BlancAttributeApi.get();
        blancAttribute = (BlancAttribute) Bukkit.getPluginManager().getPlugin("BlancAttribute");
        user = api.getUser(player.getUniqueId());
        user.removeModifiersByTag(MODIFIER_TAG);
    }

    void apply(ItemStack item, int slot) {
        if (user == null || blancAttribute == null || item == null) return;

        // 饰品专属属性（BlancAttribute 中 options.accessory: true）只有带 bla:accessory PDC 标记的物品才解析。
        // 这里只给解析用的副本打标记，不改动仓库里实际存储的物品，
        // 这样玩家把饰品从槽位移出后拿在手上不会继续触发饰品专属属性。
        ItemStack parseSource = withAccessoryMarker(item);

        int index = 0;
        for (ParsedLore parsed : LoreParser.parse(
                parseSource,
                api.getAttributeRegistry(),
                blancAttribute.getAttributeLoader().getLoreSettings()
        )) {
            String name = MODIFIER_PREFIX + slot + "/" + parsed.attribute().asString() + "/" + index++;
            user.addModifier(AttributeModifier.nonPersistent(
                    name,
                    parsed.attribute(),
                    parsed.value(),
                    parsed.operation(),
                    MODIFIER_TAG
            ));
        }
    }

    private ItemStack withAccessoryMarker(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(ACCESSORY_PDC, PersistentDataType.BYTE)) {
            return item;
        }

        ItemStack copy = item.clone();
        ItemMeta copyMeta = copy.getItemMeta();
        if (copyMeta == null) {
            return copy;
        }
        copyMeta.getPersistentDataContainer().set(ACCESSORY_PDC, PersistentDataType.BYTE, (byte) 1);
        copy.setItemMeta(copyMeta);
        return copy;
    }

    void finish(Player player) {
        if (api != null) {
            api.recalculateAll(player.getUniqueId());
        }
        api = null;
        blancAttribute = null;
        user = null;
    }
}

package com.blanoir.accessory.module.attribute.load;

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

final class BlancAttributeLoad extends AbstractAttributeLoader<BlancAttributeLoad.Context> {

    private static final String MODIFIER_TAG = "blanc_accessory";
    private static final String MODIFIER_PREFIX = "accessory:slot";
    private static final NamespacedKey ACCESSORY_PDC = new NamespacedKey("bla", "accessory");

    record Context(Player player, BlancAttributeApi api, BlancAttribute plugin, AttributeUser user) { }

    @Override
    protected Context begin(Player player) {
        BlancAttributeApi api = BlancAttributeApi.get();
        BlancAttribute blancAttribute = (BlancAttribute) Bukkit.getPluginManager().getPlugin("BlancAttribute");
        AttributeUser user = api.getUser(player.getUniqueId());
        if (user == null || blancAttribute == null) return null;
        user.removeModifiersByTag(MODIFIER_TAG);
        return new Context(player, api, blancAttribute, user);
    }

    @Override
    protected void apply(Context context, ItemStack item, int slot) {
        // 饰品专属属性（BlancAttribute 中 options.accessory: true）只有带 bla:accessory PDC 标记的物品才解析。
        // 这里只给解析用的副本打标记，不改动仓库里实际存储的物品，
        // 这样玩家把饰品从槽位移出后拿在手上不会继续触发饰品专属属性。
        ItemStack parseSource = withAccessoryMarker(item);

        int index = 0;
        for (ParsedLore parsed : LoreParser.parse(
                parseSource,
                context.api().getAttributeRegistry(),
                context.plugin().getAttributeLoader().getLoreSettings()
        )) {
            String name = MODIFIER_PREFIX + slot + "/" + parsed.attribute().asString() + "/" + index++;
            context.user().addModifier(AttributeModifier.nonPersistent(
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

    @Override
    protected void finish(Context context) {
        context.api().recalculateAll(context.player().getUniqueId());
    }
}

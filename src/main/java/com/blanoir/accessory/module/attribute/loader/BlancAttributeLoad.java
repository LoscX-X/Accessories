package com.blanoir.accessory.module.attribute.loader;

import com.blanoir.blancattribute.BlancAttribute;
import com.blanoir.blancattribute.api.AttributeModifier;
import com.blanoir.blancattribute.api.AttributeUser;
import com.blanoir.blancattribute.api.BlancAttributeApi;
import com.blanoir.blancattribute.core.lore.LoreParser;
import com.blanoir.blancattribute.core.lore.ParsedLore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class BlancAttributeLoad {

    private static final String MODIFIER_TAG = "blanc_accessory";
    private static final String MODIFIER_PREFIX = "accessory:slot";

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

        int index = 0;
        for (ParsedLore parsed : LoreParser.parse(
                item,
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

    void finish(Player player) {
        if (api != null) {
            api.recalculateAll(player.getUniqueId());
        }
        api = null;
        blancAttribute = null;
        user = null;
    }
}

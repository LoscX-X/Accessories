package com.blanoir.accessory.module.attribute.load;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.AuraSkillsBukkit;
import dev.aurelium.auraskills.api.item.ItemManager;
import dev.aurelium.auraskills.api.item.ModifierType;
import dev.aurelium.auraskills.api.trait.TraitModifier;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

final class AuraSkillsLoad extends AbstractAttributeLoader<SkillsUser> {

    private static final String PREFIX = "accessory:";

    private final AuraSkillsApi api;
    private final ItemManager itemManager;

    AuraSkillsLoad(AuraSkillsApi api) {
        this.api = api;
        this.itemManager = AuraSkillsBukkit.get().getItemManager();
    }

    @Override
    protected SkillsUser begin(Player player) {
        SkillsUser user = api.getUser(player.getUniqueId());
        if (user == null || !user.isLoaded()) return null;
        for (String name : Map.copyOf(user.getTraitModifiers()).keySet()) {
            if (name.startsWith(PREFIX)) {
                user.removeTraitModifier(name);
            }
        }
        return user;
    }

    @Override
    protected void apply(SkillsUser user, ItemStack item, int slot) {
        List<TraitModifier> itemMods = itemManager.getTraitModifiers(item, ModifierType.ITEM);
        List<TraitModifier> armorMods = itemManager.getTraitModifiers(item, ModifierType.ARMOR);
        applyAll(user, itemMods, slot, "item");
        applyAll(user, armorMods, slot, "armor");
    }

    @Override
    public boolean isReady(Player player) {
        SkillsUser user = api.getUser(player.getUniqueId());
        return user != null && user.isLoaded();
    }

    private void applyAll(SkillsUser user, List<TraitModifier> mods, int slot, String kind) {
        if (mods == null || mods.isEmpty()) return;

        int index = 0;
        for (TraitModifier modifier : mods) {
            String traitId = modifier.trait().toString();
            String name = PREFIX + "slot" + slot + "/" + kind + "/" + index++ + "/" + traitId;

            TraitModifier temp = new TraitModifier(name, modifier.trait(), modifier.value(), modifier.operation());
            temp.setNonPersistent();
            user.addTraitModifier(temp);
        }
    }
}

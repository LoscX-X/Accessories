package com.blanoir.accessory.attributeload;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.AuraSkillsBukkit;
import dev.aurelium.auraskills.api.item.ItemManager;
import dev.aurelium.auraskills.api.item.ModifierType;
import dev.aurelium.auraskills.api.trait.TraitModifier;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

final class AuraAccessoryLoad extends BaseAccessoryLoad {

    private final AuraSkillsApi api;
    private final ItemManager itemManager;
    private SkillsUser currentUser;

    AuraAccessoryLoad(JavaPlugin plugin, AuraSkillsApi api) {
        super(plugin);
        this.api = api;
        this.itemManager = AuraSkillsBukkit.get().getItemManager();
    }

    @Override
    protected void clearTraitModifiers(Player player) {
        this.currentUser = api.getUser(player.getUniqueId());
        if (currentUser == null || !currentUser.isLoaded()) {
            this.currentUser = null;
            return;
        }

        for (String name : Map.copyOf(currentUser.getTraitModifiers()).keySet()) {
            if (name.startsWith(PREFIX)) {
                currentUser.removeTraitModifier(name);
            }
        }
    }

    @Override
    protected void applyTraitModifiers(Player player, ItemStack item, int slot) {
        if (currentUser == null || !currentUser.isLoaded()) return;

        List<TraitModifier> itemMods = itemManager.getTraitModifiers(item, ModifierType.ITEM);
        List<TraitModifier> armorMods = itemManager.getTraitModifiers(item, ModifierType.ARMOR);
        applyAll(currentUser, itemMods, slot);
        applyAll(currentUser, armorMods, slot);
    }

    private void applyAll(SkillsUser user, List<TraitModifier> mods, int slot) {
        if (mods == null || mods.isEmpty()) return;

        for (TraitModifier modifier : mods) {
            String traitId = modifier.trait().toString();
            String name = PREFIX + "slot" + slot + "/" + traitId;

            TraitModifier copy = new TraitModifier(name, modifier.trait(), modifier.value(), modifier.operation());
            copy.setNonPersistent();
            user.addTraitModifier(copy);
        }
    }
}

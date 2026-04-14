package com.blanoir.accessory.attribute;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

class VanillaAccessoryLoad extends BaseAccessoryLoad {

    VanillaAccessoryLoad(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void clearTraitModifiers(Player player) {
        // No AuraSkills: nothing to clear.
    }

    @Override
    protected void applyTraitModifiers(Player player, ItemStack item, int slot) {
        // No AuraSkills: skip trait modifier application.
    }
}

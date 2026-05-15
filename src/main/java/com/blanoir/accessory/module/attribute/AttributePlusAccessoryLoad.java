package com.blanoir.accessory.module.attribute;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class AttributePlusAccessoryLoad extends VanillaAccessoryLoad {

    private final AttributePlusLoad attributePlus;

    AttributePlusAccessoryLoad(JavaPlugin plugin) {
        super(plugin);
        this.attributePlus = new AttributePlusLoad(plugin);
    }

    @Override
    protected void clearExternalModifiers(Player player) {
        attributePlus.begin(player);
    }

    @Override
    protected void applyExternalModifiers(Player player, ItemStack item, int slot) {
        attributePlus.apply(item, slot);
    }

    @Override
    protected void finishExternalModifiers(Player player) {
        attributePlus.finish(player);
    }
}

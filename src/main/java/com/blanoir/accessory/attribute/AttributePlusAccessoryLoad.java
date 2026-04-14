package com.blanoir.accessory.attribute;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class AttributePlusAccessoryLoad extends VanillaAccessoryLoad {

    private final AttributePlusSupport attributePlus;

    AttributePlusAccessoryLoad(JavaPlugin plugin) {
        super(plugin);
        this.attributePlus = new AttributePlusSupport(plugin);
    }

    @Override
    protected void clearExternalModifiers(Player player) {
        attributePlus.begin(player);
    }

    @Override
    protected void applyExternalModifiers(Player player, ItemStack item, int slot) {
        attributePlus.apply(player, item, slot);
    }

    @Override
    protected void finishExternalModifiers(Player player) {
        attributePlus.finish(player);
    }
}

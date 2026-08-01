package com.blanoir.accessory.module.attribute.loader;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class BlancAttributeAccessoryLoad extends VanillaAccessoryLoad {

    private final BlancAttributeLoad blancAttribute;

    BlancAttributeAccessoryLoad(JavaPlugin plugin) {
        super(plugin);
        this.blancAttribute = new BlancAttributeLoad();
    }

    @Override
    protected void clearExternalModifiers(Player player) {
        blancAttribute.begin(player);
    }

    @Override
    protected void applyExternalModifiers(Player player, ItemStack item, int slot) {
        blancAttribute.apply(item, slot);
    }

    @Override
    protected void finishExternalModifiers(Player player) {
        blancAttribute.finish(player);
    }
}

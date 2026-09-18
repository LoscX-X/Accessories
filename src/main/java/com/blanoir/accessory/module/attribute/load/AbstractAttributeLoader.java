package com.blanoir.accessory.module.attribute.load;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** A refresh owns its context; singleton loaders never retain a current player or mutable sources. */
abstract class AbstractAttributeLoader<C> implements AttributeLoader {
    @Override
    public final void rebuild(Player player, ItemStack[] activeContents) {
        C context = begin(player);
        if (context == null) return;
        try {
            for (int slot = 0; slot < activeContents.length; slot++) {
                ItemStack item = activeContents[slot];
                if (item != null && !item.getType().isAir()) apply(context, item, slot);
            }
        } finally {
            finish(context);
        }
    }

    /** Remove this provider's old sources and create a context for this refresh only. */
    protected abstract C begin(Player player);
    protected abstract void apply(C context, ItemStack item, int absoluteSlot);
    protected void finish(C context) { }
}

package com.blanoir.accessory.module.attribute;

import com.blanoir.accessory.config.AccessorySettings;
import com.blanoir.accessory.module.attribute.load.AttributeLoader;
import com.blanoir.accessory.module.attribute.load.AttributeLoaders;
import com.blanoir.accessory.module.attribute.load.VanillaAttributeLoad;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Attribute execution only; slot eligibility, item events and storage belong to other services. */
public final class AccessoryAttributes {
    private final AttributeLoader external;
    private final AttributeLoader vanilla;
    private final boolean vanillaEnabled;

    public AccessoryAttributes(JavaPlugin plugin, AccessorySettings.Attributes settings) {
        this(AttributeLoaders.external(plugin, settings.provider()), new VanillaAttributeLoad(plugin, settings.lore()), settings.vanilla());
    }

    AccessoryAttributes(AttributeLoader external, AttributeLoader vanilla, boolean vanillaEnabled) {
        this.external = external;
        this.vanilla = vanilla;
        this.vanillaEnabled = vanillaEnabled;
    }

    public boolean isReady(Player player) { return external == null || external.isReady(player); }

    public void rebuild(Player player, ItemStack[] activeContents) {
        // Always clear our old vanilla modifiers, including after disabling vanilla in config.
        vanilla.rebuild(player, vanillaEnabled ? activeContents : new ItemStack[0]);
        if (external != null) external.rebuild(player, activeContents);
    }

    public void clear(Player player) { rebuild(player, new ItemStack[0]); }
}

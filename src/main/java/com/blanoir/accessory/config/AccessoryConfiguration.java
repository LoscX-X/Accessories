package com.blanoir.accessory.config;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.module.inventory.AccessoryPageManager;
import com.blanoir.accessory.utils.lang.Lang;

/** A completely parsed configuration, published only after validation succeeds. */
public record AccessoryConfiguration(AccessorySettings settings, AccessoryPageManager pages, Lang language) {
    public static AccessoryConfiguration read(Accessory plugin) {
        AccessorySettings settings = AccessorySettings.read(plugin.getConfig());
        AccessoryPageManager pages = new AccessoryPageManager(plugin.getDataFolder(), plugin.getLogger());
        pages.reload(settings.gui());
        AccessoryConfiguration result = new AccessoryConfiguration(settings, pages, new Lang(plugin, settings.language()));
        return result;
    }
}

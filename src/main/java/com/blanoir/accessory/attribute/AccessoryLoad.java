package com.blanoir.accessory.attribute;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class AccessoryLoad {

    private final AccessoryLoadHandler delegate;

    public AccessoryLoad(JavaPlugin plugin) {
        this.delegate = createDelegate(plugin);
    }

    public void rebuildFromInventory(Player player, Inventory inventory) {
        delegate.rebuildFromInventory(player, inventory);
    }

    private AccessoryLoadHandler createDelegate(JavaPlugin plugin) {
        boolean attributePlusEnabled = plugin.getConfig().getBoolean("attribute-plus.enable", false);
        boolean hasAttributePlus = Bukkit.getPluginManager().getPlugin("AttributePlus") != null;

        if (attributePlusEnabled) {
            if (hasAttributePlus) {
                plugin.getLogger().info("[Accessory] AttributePlus mode enabled by config, AuraSkills traits are ignored.");
                return new AttributePlusAccessoryLoad(plugin);
            }
            plugin.getLogger().warning("[Accessory] AttributePlus mode enabled, but AttributePlus plugin not found. Using vanilla item attributes only.");
            return new VanillaAccessoryLoad(plugin);
        }

        boolean hasAura = Bukkit.getPluginManager().getPlugin("AuraSkills") != null;
        if (!hasAura) {
            plugin.getLogger().warning("[Accessory] AuraSkills not found, using vanilla item attributes only.");
            return new VanillaAccessoryLoad(plugin);
        }

        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            if (api == null) {
                plugin.getLogger().warning("[Accessory] AuraSkills API unavailable, using vanilla item attributes only.");
                return new VanillaAccessoryLoad(plugin);
            }
            return new AuraAccessoryLoad(plugin, api);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Accessory] AuraSkills hook failed, using vanilla item attributes only: " + t.getClass().getSimpleName());
            return new VanillaAccessoryLoad(plugin);
        }
    }
}

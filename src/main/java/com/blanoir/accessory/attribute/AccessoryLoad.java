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
        boolean hasAttributePlus = Bukkit.getPluginManager().getPlugin("AttributePlus") != null;
        boolean hasAura = Bukkit.getPluginManager().getPlugin("AuraSkills") != null;

        if (hasAttributePlus) {
            plugin.getLogger().info("[Accessory] AttributePlus hook enabled.");
            return new AttributePlusAccessoryLoad(plugin);
        }

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

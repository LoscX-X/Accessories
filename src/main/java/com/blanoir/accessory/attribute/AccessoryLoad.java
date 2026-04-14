package com.blanoir.accessory.attribute;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AccessoryLoad {

    private static final Set<String> LOGGED = Collections.synchronizedSet(new HashSet<>());
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
            infoOnce(plugin, "attributeplus-enabled", "AttributePlus hook enabled.");
            return new AttributePlusAccessoryLoad(plugin);
        }

        if (!hasAura) {
            warnOnce(plugin, "auraskills-missing", "AuraSkills not found, using vanilla item attributes only.");
            return new VanillaAccessoryLoad(plugin);
        }

        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            if (api == null) {
                warnOnce(plugin, "auraskills-api-unavailable", "AuraSkills API unavailable, using vanilla item attributes only.");
                return new VanillaAccessoryLoad(plugin);
            }
            return new AuraAccessoryLoad(plugin, api);
        } catch (Throwable t) {
            warnOnce(plugin, "auraskills-hook-failed-" + t.getClass().getSimpleName(),
                    "AuraSkills hook failed, using vanilla item attributes only: " + t.getClass().getSimpleName());
            return new VanillaAccessoryLoad(plugin);
        }
    }

    private static void infoOnce(JavaPlugin plugin, String key, String msg) {
        if (LOGGED.add(key)) {
            plugin.getLogger().info(msg);
        }
    }

    private static void warnOnce(JavaPlugin plugin, String key, String msg) {
        if (LOGGED.add(key)) {
            plugin.getLogger().warning(msg);
        }
    }
}

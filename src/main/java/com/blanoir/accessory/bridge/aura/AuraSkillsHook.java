package com.blanoir.accessory.bridge.aura;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.attribute.aura.CustomStats;
import com.blanoir.accessory.attribute.aura.CustomTraits;
import com.blanoir.accessory.bridge.myhic.MmPlaceHolder;
import com.blanoir.accessory.bridge.placeholder.AbsorbPlaceholder;
import com.blanoir.accessory.bridge.placeholder.MagicAbsorbPlaceholder;
import com.blanoir.accessory.command.ShieldCurCommand;
import com.blanoir.accessory.traits.Absorb;
import com.blanoir.accessory.traits.Defence;
import com.blanoir.accessory.traits.HealRegDecrease;
import com.blanoir.accessory.traits.HealRegeneration;
import com.blanoir.accessory.traits.Health;
import com.blanoir.accessory.traits.LifeSteal;
import com.blanoir.accessory.traits.MagicAbsorb;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import org.bukkit.Bukkit;

public final class AuraSkillsHook {

    private final Accessory plugin;

    public AuraSkillsHook(Accessory plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            plugin.getLogger().info("[Accessory][Aura] Step 1: getting AuraSkills API");
            AuraSkillsApi api = AuraSkillsApi.get();

            if (api == null) {
                plugin.getLogger().warning("[Accessory] AuraSkills API unavailable, using vanilla item attributes only.");
                return;
            }

            plugin.getLogger().info("[Accessory][Aura] Step 2: registering traits/stats");
            var registry = api.useRegistry("accessory", plugin.getDataFolder());
            registry.registerTrait(CustomTraits.HEAL_REGENERATION);
            registry.registerTrait(CustomTraits.DEFENCE);
            registry.registerTrait(CustomTraits.HEALTH);
            registry.registerTrait(CustomTraits.ABSORB);
            registry.registerTrait(CustomTraits.HEAL_DECREASE);
            registry.registerTrait(CustomTraits.LIFE_STEAL);
            registry.registerStat(CustomStats.CUSTOM_STAT);
            registry.registerTrait(CustomTraits.MAGICABSORB);

            plugin.getLogger().info("[Accessory][Aura] Step 3: creating trait handlers");
            HealRegeneration hr = new HealRegeneration(plugin, api);
            HealRegDecrease dec = new HealRegDecrease(plugin, api);
            Defence def = new Defence(plugin, api);
            Health health = new Health(plugin, api);
            LifeSteal life = new LifeSteal(plugin, api);
            Absorb absorb = new Absorb(plugin, api);
            MagicAbsorb ms = new MagicAbsorb(plugin, api);

            plugin.getLogger().info("[Accessory][Aura] Step 4: registering shield commands");
            var shieldCmd = plugin.getCommand("shield");
            if (shieldCmd != null) {
                var exec = new ShieldCurCommand(absorb);
                shieldCmd.setExecutor(exec);
                shieldCmd.setTabCompleter(exec);
            } else {
                plugin.getLogger().warning("[Accessory][Aura] Command 'shield' not found.");
            }

            var magicShieldCmd = plugin.getCommand("magicshield");
            if (magicShieldCmd != null) {
                var exec = new ShieldCurCommand(ms::addShield, ms::addShieldPercent, "accessory.magicshield");
                magicShieldCmd.setExecutor(exec);
                magicShieldCmd.setTabCompleter(exec);
            } else {
                plugin.getLogger().warning("[Accessory][Aura] Command 'magicshield' not found.");
            }

            plugin.getLogger().info("[Accessory][Aura] Step 5: registering Mythic placeholder bridge");
            if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
                MmPlaceHolder.registerShieldPlaceholder(plugin, absorb);
            } else {
                plugin.getLogger().warning("[Accessory] MythicMobs not found, skip Mythic placeholder bridge.");
            }

            plugin.getLogger().info("[Accessory][Aura] Step 6: registering AuraSkills trait handlers");
            var handlers = api.getHandlers();
            handlers.registerTraitHandler(ms);
            handlers.registerTraitHandler(life);
            handlers.registerTraitHandler(hr);
            handlers.registerTraitHandler(dec);
            handlers.registerTraitHandler(def);
            handlers.registerTraitHandler(health);
            handlers.registerTraitHandler(absorb);

            plugin.getLogger().info("[Accessory][Aura] Step 7: registering listeners");
            plugin.getServer().getPluginManager().registerEvents(ms, plugin);
            plugin.getServer().getPluginManager().registerEvents(absorb, plugin);
            plugin.getServer().getPluginManager().registerEvents(health, plugin);
            plugin.getServer().getPluginManager().registerEvents(dec, plugin);

            plugin.getLogger().info("[Accessory][Aura] Step 8: starting tasks / PlaceholderAPI");
            hr.startTask();
            hookPlaceholderApi(absorb, ms);

            plugin.getLogger().info("[Accessory] AuraSkills hook enabled (delayed init).");
        } catch (Throwable t) {
            plugin.getLogger().warning("[Accessory] AuraSkills hook failed, using vanilla item attributes only.");
            t.printStackTrace();

            Throwable cause = t.getCause();
            while (cause != null) {
                plugin.getLogger().warning("[Accessory] Caused by:");
                cause.printStackTrace();
                cause = cause.getCause();
            }
        }
    }

    private void hookPlaceholderApi(Absorb absorb, MagicAbsorb ms) {
        plugin.getLogger().info("[Accessory][Aura] Step 8.1: checking PlaceholderAPI");

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().warning("[Accessory] PlaceholderAPI not found! Placeholders will not work!");
            return;
        }

        plugin.getLogger().info("[Accessory][Aura] Step 8.2: registering PlaceholderAPI expansions");
        new AbsorbPlaceholder(absorb).register();
        new MagicAbsorbPlaceholder(ms).register();
        plugin.getLogger().info("[Accessory] PlaceholderAPI expansions registered!");
    }
}
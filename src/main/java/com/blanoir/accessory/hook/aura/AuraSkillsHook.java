package com.blanoir.accessory.hook.aura;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.module.attribute.aura.CustomStats;
import com.blanoir.accessory.module.attribute.aura.CustomTraits;
import com.blanoir.accessory.hook.placeholderapi.AbsorbPlaceholder;
import com.blanoir.accessory.hook.placeholderapi.MagicAbsorbPlaceholder;
import com.blanoir.accessory.module.attribute.aura.traits.Absorb;
import com.blanoir.accessory.module.attribute.aura.traits.Defence;
import com.blanoir.accessory.module.attribute.aura.traits.HealRegDecrease;
import com.blanoir.accessory.module.attribute.aura.traits.HealRegeneration;
import com.blanoir.accessory.module.attribute.aura.traits.Health;
import com.blanoir.accessory.module.attribute.aura.traits.LifeSteal;
import com.blanoir.accessory.module.attribute.aura.traits.MagicAbsorb;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import org.bukkit.Bukkit;

public final class AuraSkillsHook {

    private final Accessory plugin;

    public AuraSkillsHook(Accessory plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            debugStartup("[Accessory][Aura] Step 1: getting AuraSkills API");
            AuraSkillsApi api = AuraSkillsApi.get();

            if (api == null) {
                plugin.getLogger().warning("[Accessory] AuraSkills API unavailable, using vanilla item attributes only.");
                return;
            }

            debugStartup("[Accessory][Aura] Step 2: registering traits/stats");
            var registry = api.useRegistry("accessory", plugin.getDataFolder());
            registry.registerTrait(CustomTraits.HEAL_REGENERATION);
            registry.registerTrait(CustomTraits.DEFENCE);
            registry.registerTrait(CustomTraits.HEALTH);
            registry.registerTrait(CustomTraits.ABSORB);
            registry.registerTrait(CustomTraits.HEAL_DECREASE);
            registry.registerTrait(CustomTraits.LIFE_STEAL);
            registry.registerStat(CustomStats.CUSTOM_STAT);
            registry.registerTrait(CustomTraits.MAGICABSORB);

            debugStartup("[Accessory][Aura] Step 3: creating trait handlers");
            HealRegeneration hr = new HealRegeneration(plugin, api);
            HealRegDecrease dec = new HealRegDecrease(plugin, api);
            Defence def = new Defence(plugin, api);
            Health health = new Health(plugin, api);
            LifeSteal life = new LifeSteal(plugin, api);
            Absorb absorb = new Absorb(plugin, api);
            MagicAbsorb ms = new MagicAbsorb(plugin, api);

            debugStartup("[Accessory][Aura] Step 4: configuring shield commands");
            plugin.configureShieldCommands(absorb, ms);

            debugStartup("[Accessory][Aura] Step 5: registering Mythic placeholder bridge");
            if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
                tryRegisterMythicPlaceholder(absorb);
            } else {
                debugStartup("[Accessory][Aura] MythicMobs not found, skip Mythic placeholder bridge.");
            }

            debugStartup("[Accessory][Aura] Step 6: registering AuraSkills trait handlers");
            var handlers = api.getHandlers();
            handlers.registerTraitHandler(ms);
            handlers.registerTraitHandler(life);
            handlers.registerTraitHandler(hr);
            handlers.registerTraitHandler(dec);
            handlers.registerTraitHandler(def);
            handlers.registerTraitHandler(health);
            handlers.registerTraitHandler(absorb);

            debugStartup("[Accessory][Aura] Step 7: registering listeners");
            plugin.getServer().getPluginManager().registerEvents(ms, plugin);
            plugin.getServer().getPluginManager().registerEvents(absorb, plugin);
            plugin.getServer().getPluginManager().registerEvents(health, plugin);
            plugin.getServer().getPluginManager().registerEvents(dec, plugin);

            debugStartup("[Accessory][Aura] Step 8: starting tasks / PlaceholderAPI");
            hr.startTask();
            hookPlaceholderApi(absorb, ms);

            plugin.getLogger().info("[Accessory] AuraSkills hook enabled (delayed init).");
        } catch (Throwable t) {
            plugin.getLogger().warning("[Accessory] AuraSkills hook failed, using vanilla item attributes only.");
            if (isStartupDebugEnabled()) {
                t.printStackTrace();
            } else {
                plugin.getLogger().warning("[Accessory] Reason: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private void tryRegisterMythicPlaceholder(Absorb absorb) {
        try {
            Class<?> mmPlaceHolderClass = Class.forName("com.blanoir.accessory.hook.myhic.MmPlaceHolder");
            mmPlaceHolderClass
                    .getMethod("registerShieldPlaceholder", org.bukkit.plugin.java.JavaPlugin.class, Absorb.class)
                    .invoke(null, plugin, absorb);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Accessory][Aura] Mythic placeholder bridge failed, AuraSkills traits remain enabled.");
            if (isStartupDebugEnabled()) {
                t.printStackTrace();
            } else {
                plugin.getLogger().warning("[Accessory] Reason: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private void hookPlaceholderApi(Absorb absorb, MagicAbsorb ms) {
        debugStartup("[Accessory][Aura] Step 8.1: checking PlaceholderAPI");

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            debugStartup("[Accessory][Aura] PlaceholderAPI not found, skipping placeholder registration.");
            return;
        }

        debugStartup("[Accessory][Aura] Step 8.2: registering PlaceholderAPI expansions");
        new AbsorbPlaceholder(absorb).register();
        new MagicAbsorbPlaceholder(ms).register();
        debugStartup("[Accessory] PlaceholderAPI expansions registered!");
    }

    private void debugStartup(String message) {
        if (isStartupDebugEnabled()) {
            plugin.getLogger().info(message);
        }
    }

    private boolean isStartupDebugEnabled() {
        return plugin.getConfig().getBoolean("debug-mode", false);
    }
}

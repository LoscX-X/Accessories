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
            AuraSkillsApi api = AuraSkillsApi.get();

            if (api == null) {
                plugin.getLogger().warning("[Accessory] AuraSkills API unavailable; custom traits are inactive.");
                return;
            }

            var registry = api.useRegistry("accessory", plugin.getDataFolder());
            registry.registerTrait(CustomTraits.HEAL_REGENERATION);
            registry.registerTrait(CustomTraits.DEFENCE);
            registry.registerTrait(CustomTraits.HEALTH);
            registry.registerTrait(CustomTraits.ABSORB);
            registry.registerTrait(CustomTraits.HEAL_DECREASE);
            registry.registerTrait(CustomTraits.LIFE_STEAL);
            registry.registerStat(CustomStats.CUSTOM_STAT);
            registry.registerTrait(CustomTraits.MAGICABSORB);

            HealRegeneration hr = new HealRegeneration(plugin, api);
            HealRegDecrease dec = new HealRegDecrease(plugin, api);
            Defence def = new Defence(plugin, api);
            Health health = new Health(plugin, api);
            LifeSteal life = new LifeSteal(plugin, api);
            Absorb absorb = new Absorb(plugin, api);
            MagicAbsorb ms = new MagicAbsorb(plugin, api);

            plugin.configureShieldCommands(absorb, ms);

            if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
                tryRegisterMythicPlaceholder(absorb);
            }

            var handlers = api.getHandlers();
            handlers.registerTraitHandler(ms);
            handlers.registerTraitHandler(life);
            handlers.registerTraitHandler(hr);
            handlers.registerTraitHandler(dec);
            handlers.registerTraitHandler(def);
            handlers.registerTraitHandler(health);
            handlers.registerTraitHandler(absorb);

            plugin.getServer().getPluginManager().registerEvents(ms, plugin);
            plugin.getServer().getPluginManager().registerEvents(absorb, plugin);
            plugin.getServer().getPluginManager().registerEvents(health, plugin);
            plugin.getServer().getPluginManager().registerEvents(dec, plugin);

            hr.startTask();
            hookPlaceholderApi(absorb, ms);

            plugin.getLogger().info("[Accessory] AuraSkills hook enabled (delayed init).");
        } catch (RuntimeException | LinkageError error) {
            plugin.debug().failure("hooks", "AuraSkills custom traits could not initialize", error);
        }
    }

    private void tryRegisterMythicPlaceholder(Absorb absorb) {
        try {
            Class<?> mmPlaceHolderClass = Class.forName("com.blanoir.accessory.hook.myhic.MmPlaceHolder");
            mmPlaceHolderClass
                    .getMethod("registerShieldPlaceholder", org.bukkit.plugin.java.JavaPlugin.class, Absorb.class)
                    .invoke(null, plugin, absorb);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            plugin.debug().failure("hooks", "Mythic shield placeholder could not initialize", error);
        }
    }

    private void hookPlaceholderApi(Absorb absorb, MagicAbsorb ms) {

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }

        new AbsorbPlaceholder(absorb).register();
        new MagicAbsorbPlaceholder(ms).register();
    }

}

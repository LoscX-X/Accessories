package com.blanoir.accessory.bridge;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.attributeload.CustomStats;
import com.blanoir.accessory.attributeload.CustomTraits;
import com.blanoir.accessory.traits.Absorb;
import com.blanoir.accessory.traits.Defence;
import com.blanoir.accessory.traits.HealRegDecrease;
import com.blanoir.accessory.traits.HealRegeneration;
import com.blanoir.accessory.traits.Health;
import com.blanoir.accessory.traits.LifeSteal;
import com.blanoir.accessory.traits.MagicAbsorb;
import com.blanoir.accessory.utils.ShieldCurCommand;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import org.bukkit.Bukkit;

public final class AuraSkillsHook {

    private final Accessory plugin;

    public AuraSkillsHook(Accessory plugin) {
        this.plugin = plugin;
    }

    public HookBundle load() {
        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            if (api == null) {
                plugin.getLogger().warning("[Accessory] AuraSkills API unavailable, using vanilla item attributes only.");
                return HookBundle.disabled();
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

            var shieldCmd = plugin.getCommand("shield");
            if (shieldCmd != null) {
                var exec = new ShieldCurCommand(absorb);
                shieldCmd.setExecutor(exec);
                shieldCmd.setTabCompleter(exec);
            }

            var magicShieldCmd = plugin.getCommand("magicshield");
            if (magicShieldCmd != null) {
                var exec = new ShieldCurCommand(ms::addShield, ms::addShieldPercent, "accessory.magicshield");
                magicShieldCmd.setExecutor(exec);
                magicShieldCmd.setTabCompleter(exec);
            }

            if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
                MmPlaceHolder.registerShieldPlaceholder(plugin, absorb);
            } else {
                plugin.getLogger().warning("[Accessory] MythicMobs not found, skip Mythic placeholder bridge.");
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

            plugin.getLogger().info("[Accessory] AuraSkills hook enabled (delayed init).");
            return new HookBundle(hr, absorb, ms);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Accessory] AuraSkills hook failed, using vanilla item attributes only: " + t.getClass().getSimpleName());
            return HookBundle.disabled();
        }
    }

    public record HookBundle(HealRegeneration hr, Absorb absorb, MagicAbsorb ms) {
        public static HookBundle disabled() {
            return new HookBundle(null, null, null);
        }
    }
}

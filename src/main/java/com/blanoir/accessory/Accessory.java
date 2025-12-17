package com.blanoir.accessory;

import com.blanoir.accessory.attributeload.CustomStats;
import com.blanoir.accessory.traits.*;
import com.blanoir.accessory.attributeload.CustomTraits;
import com.blanoir.accessory.inventory.InvListener;
import com.blanoir.accessory.inventory.InvLoad;
import com.blanoir.accessory.inventory.InvReload;
import com.blanoir.accessory.inventory.InvSave;
import com.blanoir.accessory.utils.AbsorbPlaceholder;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.registry.NamespacedRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.blanoir.accessory.utils.Language;

import java.io.File;

public final class Accessory extends JavaPlugin {

    private Language lang;
    File file = new File(getDataFolder(), "stats.yml");
    public Language lang() { return lang; }

    @Override
    public void onEnable() {

        getDataFolder().mkdirs();
        if (!file.exists()) {
            saveResource("stats.yml" , false);
        }
        saveDefaultConfig();
        lang = new Language(this);


        getServer().getPluginManager().registerEvents(new InvSave(this), this);
        getServer().getPluginManager().registerEvents(new InvListener(this), this);
        //Listener
        getCommand("inv").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) return true;
            new InvLoad(this).openFor(p);
            return true;
        });
        getCommand("accessory").setExecutor(new InvReload(this));
        //Command

        AuraSkillsApi api = AuraSkillsApi.get();
        NamespacedRegistry registry = api.useRegistry("accessory", getDataFolder());
        registry.registerTrait(CustomTraits.HEAL_REGENERATION);
        registry.registerTrait(CustomTraits.DEFENCE);
        registry.registerTrait(CustomTraits.ABSORB);
        registry.registerTrait(CustomTraits.HEAL_DECREASE);
        registry.registerTrait(CustomTraits.MAX_HEALTH_FLAT_REDUCTION);
        registry.registerTrait(CustomTraits.MAX_HEALTH_PERCENT_REDUCTION);
        registry.registerTrait(CustomTraits.LIFE_STEAL);
        registry.registerStat(CustomStats.CUSTOM_STAT);

        HealRegeneration hr = new HealRegeneration(this, api);
        HealRegDecrease dec = new HealRegDecrease(this, api);
        Defence def = new Defence(this, api);
        LifeSteal life = new LifeSteal(this, api, this);
        Absorb absorb = new Absorb(this, api);
        MaxHealthFlatReductionHandler flat = new MaxHealthFlatReductionHandler(this, api);
        MaxHealthPercentReductionHandler percent = new MaxHealthPercentReductionHandler(this, api);

        api.getHandlers().registerTraitHandler(flat);
        api.getHandlers().registerTraitHandler(percent);

        api.getHandlers().registerTraitHandler(life);
        api.getHandlers().registerTraitHandler(hr);
        api.getHandlers().registerTraitHandler(dec);
        api.getHandlers().registerTraitHandler(def);
        api.getHandlers().registerTraitHandler(absorb);

        hr.startTask();

        // 注册事件监听器..
        getServer().getPluginManager().registerEvents(absorb, this);

        // 注册 PlaceholderAPI 扩展
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AbsorbPlaceholder(absorb).register();
            getLogger().info("Absorb PlaceholderAPI expansion registered!");
        } else {
            getLogger().warning("PlaceholderAPI not found! Absorb placeholders will not work!");
        }
    }



    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Byebye");
    }
}

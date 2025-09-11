package com.blanoir.accessory;

import com.blanoir.accessory.attributeload.CustomStats;
import com.blanoir.accessory.traits.Defence;
import com.blanoir.accessory.traits.HealRegDecrease;
import com.blanoir.accessory.traits.HealRegeneration;
import com.blanoir.accessory.attributeload.CustomTraits;
import com.blanoir.accessory.inventory.InvListener;
import com.blanoir.accessory.inventory.InvLoad;
import com.blanoir.accessory.inventory.InvReload;
import com.blanoir.accessory.inventory.InvSave;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.registry.NamespacedRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.blanoir.accessory.Utils.Language;

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
        registry.registerTrait(CustomTraits.HEAL_DECREASE);
        registry.registerStat(CustomStats.CUSTOM_STAT);
        HealRegeneration hr = new HealRegeneration(this, api);
        HealRegDecrease dec = new HealRegDecrease(this, api);
        Defence def = new Defence(this, api);
        api.getHandlers().registerTraitHandler(hr);
        api.getHandlers().registerTraitHandler(dec);
        api.getHandlers().registerTraitHandler(def);
        hr.startTask();
        //AuraSkills
    }



    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Byebye");
    }
}

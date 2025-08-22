package com.blanoir.accessory;

import com.blanoir.accessory.attribute.Defence;
import com.blanoir.accessory.attribute.HealRegeneration;
import com.blanoir.accessory.attributeload.CustomTraits;
import com.blanoir.accessory.attributeload.InvListener;
import com.blanoir.accessory.inv.InvLoad;
import com.blanoir.accessory.inv.InvSave;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.registry.NamespacedRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.common.returnsreceiver.qual.This;

import java.io.File;

public final class Accessory extends JavaPlugin {

    @Override
    public void onEnable() {
        getDataFolder().mkdirs(); // 这个目录有时还未创建。:contentReference[oaicite:0]{index=0}
        saveIfAbsent("stats.yml");
        getServer().getPluginManager().registerEvents(new InvSave(this), this);
        getServer().getPluginManager().registerEvents(new InvListener(this), this);
        getCommand("inv").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) return true;
            new InvLoad(this).openFor(p);
            return true;
        });

        AuraSkillsApi api = AuraSkillsApi.get();
        NamespacedRegistry registry = api.useRegistry("accessory", getDataFolder());

        registry.registerTrait(CustomTraits.HEAL_REGENERATION);
        registry.registerTrait(CustomTraits.DEFENCE);

        HealRegeneration hr = new HealRegeneration(this, api);
        Defence def = new Defence(this, api);
        api.getHandlers().registerTraitHandler(hr);
        api.getHandlers().registerTraitHandler(def);
        hr.startTask();
    }
    private void saveIfAbsent(String path) {
        File out = new File(getDataFolder(), path);
        if (!out.getParentFile().exists()) out.getParentFile().mkdirs();
        if (!out.exists()) {
            // false = 如果目标已存在则不覆盖；true = 覆盖
            saveResource(path, false);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}

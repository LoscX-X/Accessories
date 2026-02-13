package com.blanoir.accessory;

import com.blanoir.accessory.api.AccessoryService;
import com.blanoir.accessory.attributeload.CustomStats;
import com.blanoir.accessory.bridge.MmPlaceHolder;
import com.blanoir.accessory.bridge.MythicBridgeListener;
import com.blanoir.accessory.bridge.RealAttackMechanic;
import com.blanoir.accessory.hooks.AccessoryKeybindHook;
import com.blanoir.accessory.hooks.MagicAbsorbPlaceholder;
import com.blanoir.accessory.traits.*;
import com.blanoir.accessory.attributeload.CustomTraits;
import com.blanoir.accessory.inventory.InvListener;
import com.blanoir.accessory.inventory.InvLoad;
import com.blanoir.accessory.inventory.InvReload;
import com.blanoir.accessory.inventory.InvSave;
import com.blanoir.accessory.hooks.AbsorbPlaceholder;
import com.blanoir.accessory.utils.ShieldCurCommand;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.blanoir.accessory.utils.Language;

import java.io.File;

public final class Accessory extends JavaPlugin {

    private Language lang;
    private File statsFile;
    private AccessoryService accessoryService;

    public Language lang() { return lang; }

    @Override
    public void onEnable() {
        initFiles();
        initLang();

        registerCommands();
        registerListeners();

        AuraSkillsApi api = AuraSkillsApi.get();
        var bundle = initAuraSkills(api);

        startTasks(bundle);

        hookPlaceholderApi(bundle.absorb(),bundle.ms());
        hookKeyBind();
        this.accessoryService = new AccessoryService(this);
        getServer().getPluginManager().registerEvents(new MythicBridgeListener(this), this);

    }
    public AccessoryService service() {
        return accessoryService;
    }
    @Override
    public void onDisable() {
        getLogger().info("Bye");
    }



    private void initFiles() {
        getDataFolder().mkdirs();
        statsFile = new File(getDataFolder(), "stats.yml");
        if (!statsFile.exists()) {
            saveResource("stats.yml", false);
        }
        saveDefaultConfig();
    }

    private void initLang() {
        lang = new Language(this);
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new InvSave(this), this);
        pm.registerEvents(new InvListener(this), this);
    }

    private void registerCommands() {
        getCommand("inv").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) return true;
            new InvLoad(this).openFor(p);
            return true;
        });
        getCommand("accessory").setExecutor(new InvReload(this));
    }

    private AuraBundle initAuraSkills(AuraSkillsApi api) {
        var registry = api.useRegistry("accessory", getDataFolder());

        registry.registerTrait(CustomTraits.HEAL_REGENERATION);
        registry.registerTrait(CustomTraits.DEFENCE);
        registry.registerTrait(CustomTraits.ABSORB);
        registry.registerTrait(CustomTraits.HEAL_DECREASE);
        registry.registerTrait(CustomTraits.LIFE_STEAL);
        registry.registerStat(CustomStats.CUSTOM_STAT);
        registry.registerTrait(CustomTraits.MAGICABSORB);
        HealRegeneration hr = new HealRegeneration(this, api);
        HealRegDecrease dec = new HealRegDecrease(this, api);
        Defence def = new Defence(this, api);
        LifeSteal life = new LifeSteal(this, api);
        Absorb absorb = new Absorb(this, api);
        MagicAbsorb ms = new MagicAbsorb(this, api);
        var cmd = getCommand("shieldcur");
        if (cmd != null) {
            var exec = new ShieldCurCommand(absorb);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }
        MmPlaceHolder.registerShieldPlaceholder(this, absorb);

        var handlers = api.getHandlers();
        handlers.registerTraitHandler(ms);
        handlers.registerTraitHandler(life);
        handlers.registerTraitHandler(hr);
        handlers.registerTraitHandler(dec);
        handlers.registerTraitHandler(def);
        handlers.registerTraitHandler(absorb);
        getServer().getPluginManager().registerEvents(ms, this);
        // 事件监听器（Absorb 需要监听事件）
        getServer().getPluginManager().registerEvents(absorb, this);
        getServer().getPluginManager().registerEvents(dec, this);
        return new AuraBundle(hr, absorb, ms);
    }

    private void startTasks(AuraBundle bundle) {
        bundle.hr().startTask();

    }

    private void hookPlaceholderApi(Absorb absorb, MagicAbsorb magicAbsorb) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AbsorbPlaceholder(absorb).register();
            new MagicAbsorbPlaceholder(magicAbsorb).register();
            getLogger().info("PlaceholderAPI expansions registered!");
        } else {
            getLogger().warning("PlaceholderAPI not found! Placeholders will not work!");
        }
    }



    private void hookKeyBind() {
        if (Bukkit.getPluginManager().getPlugin("KeyBind") != null) {
            getServer().getPluginManager().registerEvents(
                    new AccessoryKeybindHook(this),
                    this
            );
            getLogger().info("[Accessory] KeyBind hook enabled: action=accessory.try_equip");
        } else {
            getLogger().warning("[Accessory] KeyBind not found, payload equip disabled.");
        }
    }


    // 只收纳你后续还要用到的对象（避免全局字段乱飘）
    private record AuraBundle(HealRegeneration hr, Absorb absorb, MagicAbsorb ms) {}
}

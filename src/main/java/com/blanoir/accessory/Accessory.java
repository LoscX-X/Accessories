package com.blanoir.accessory;

import com.blanoir.accessory.api.AccessoryService;
import com.blanoir.accessory.bridge.MythicBridgeListener;
import com.blanoir.accessory.bridge.AccessoryKeybindHook;
import com.blanoir.accessory.bridge.AccessorySkillEngine;
import com.blanoir.accessory.bridge.AuraSkillsHook;
import com.blanoir.accessory.bridge.AccessorySkillListener;
import com.blanoir.accessory.inventory.InvListener;
import com.blanoir.accessory.inventory.InvReload;
import com.blanoir.accessory.inventory.InvSave;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.blanoir.accessory.utils.Language;

import java.io.File;

public final class Accessory extends JavaPlugin {

    private Language lang;
    private File statsFile;
    private AccessoryService accessoryService;
    private AccessorySkillEngine skillEngine;


    public Language lang() { return lang; }
    public AccessorySkillEngine skillEngine() { return skillEngine; }

    @Override
    public void onEnable() {
        initFiles();
        initLang();

        registerCommands();
        registerListeners();

        checkAndScheduleMythicHook();
        checkAndScheduleAuraHook();
        hookKeyBind();
        this.accessoryService = new AccessoryService(this);
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

    private void initSkillConfigs() {
        File skillFolder = new File(getDataFolder(), "skill");
        if (!skillFolder.exists()) {
            skillFolder.mkdirs();
        }
        saveResource("skill/skill.yml", false);
        saveResource("skill/example.yml", false);
    }

    private void checkAndScheduleMythicHook() {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            getLogger().warning("[Accessory] MythicMobs not found, Mythic skill bridge disabled.");
            this.skillEngine = null;
            return;
        }
        Bukkit.getScheduler().runTask(this, this::initMythicBridgeHook);
    }

    private void initMythicBridgeHook() {
        this.skillEngine = new AccessorySkillEngine(this);
        initSkillConfigs();
        this.skillEngine.loadConfig();
        this.skillEngine.startTimer();
        getServer().getPluginManager().registerEvents(new MythicBridgeListener(this), this);
        getServer().getPluginManager().registerEvents(new AccessorySkillListener(this), this);
        for (Player online : Bukkit.getOnlinePlayers()) {
            this.skillEngine.refreshFromStored(online);
        }
        getLogger().info("[Accessory] MythicMobs hook enabled (delayed init).");
    }

    private void checkAndScheduleAuraHook() {
        if (Bukkit.getPluginManager().getPlugin("AuraSkills") == null) {
            getLogger().warning("[Accessory] AuraSkills not found, using vanilla item attributes only.");
            return;
        }
        Bukkit.getScheduler().runTask(this, this::initAuraHook);
    }

    private void initAuraHook() {
        try {
            Class<?> hookClass = Class.forName("com.blanoir.accessory.bridge.AuraSkillsHook");
            Object hook = hookClass.getConstructor(Accessory.class).newInstance(this);
            hookClass.getMethod("load").invoke(hook);
        } catch (Throwable t) {
            getLogger().warning("[Accessory] AuraSkills hook failed, using vanilla item attributes only: " + t.getClass().getSimpleName());
        }
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new InvSave(this), this);
        pm.registerEvents(new InvListener(this), this);
    }

    private void registerCommands() {
        var accessoryCmd = new InvReload(this);
        var accessory = getCommand("accessory");
        if (accessory != null) {
            accessory.setExecutor(accessoryCmd);
            accessory.setTabCompleter(accessoryCmd);
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
}
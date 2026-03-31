package com.blanoir.accessory;

import com.blanoir.accessory.api.AccessoryService;
import com.blanoir.accessory.bridge.myhic.MythicBridgeListener;
import com.blanoir.accessory.bridge.myhic.skills.AccessorySkillEngine;
import com.blanoir.accessory.bridge.aura.AuraSkillsHook;
import com.blanoir.accessory.bridge.myhic.skills.AccessorySkillListener;
import com.blanoir.accessory.inventory.AccessoryInventoryStore;
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
    private AccessoryService accessoryService;
    private AccessorySkillEngine skillEngine;
    private AccessoryInventoryStore inventoryStore;


    public Language lang() { return lang; }
    public AccessorySkillEngine skillEngine() { return skillEngine; }
    public AccessoryInventoryStore inventoryStore() { return inventoryStore; }

    @Override
    public void onEnable() {
        initFiles();
        initLang();
        this.inventoryStore = new AccessoryInventoryStore(this);

        registerCommands();
        registerListeners();

        checkAndScheduleMythicHook();
        checkAndScheduleAuraHook();
        this.accessoryService = new AccessoryService(this);
    }
    public AccessoryService service() {
        return accessoryService;
    }
    @Override
    public void onDisable() {
        if (inventoryStore != null) {
            int size = accessorySize();
            for (Player player : Bukkit.getOnlinePlayers()) {
                inventoryStore.flush(player.getUniqueId(), size);
            }
        }
        getLogger().info("Bye");
    }



    private void initFiles() {
        getDataFolder().mkdirs();
        File statsFile = new File(getDataFolder(), "stats.yml");
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
        try {
            this.skillEngine = new AccessorySkillEngine(this);
            this.skillEngine.loadConfig();
            this.skillEngine.startTimer();
            initSkillConfigs();
            getServer().getPluginManager().registerEvents(new MythicBridgeListener(this), this);
            getServer().getPluginManager().registerEvents(new AccessorySkillListener(this), this);
            for (Player online : Bukkit.getOnlinePlayers()) {
                this.skillEngine.refreshFromStored(online);
            }
            getLogger().info("MythicMobs hook enabled (delayed init).");
        } catch (Throwable t) {
            this.skillEngine = null;
            getLogger().warning("MythicMobs hook failed, Mythic skill bridge disabled.");
        }
    }

    private void checkAndScheduleAuraHook() {
        if (Bukkit.getPluginManager().getPlugin("AuraSkills") == null) {
            getLogger().warning("AuraSkills not found.");
            return;
        }

        Bukkit.getScheduler().runTask(this, this::initAuraHook);
    }

    private void initAuraHook() {
        new AuraSkillsHook(this).load();
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


    public int accessorySize() {
        int size = getConfig().getInt("size", 9);
        size = Math.max(9, Math.min(54, size));
        return size - (size % 9);
    }

}

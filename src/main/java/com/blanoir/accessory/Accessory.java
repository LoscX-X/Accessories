package com.blanoir.accessory;

import com.blanoir.accessory.api.AccessoryService;
import com.blanoir.accessory.hook.aura.AuraSkillsHook;
import com.blanoir.accessory.hook.myhic.MythicBridgeListener;
import com.blanoir.accessory.hook.myhic.skills.AccessorySkillListener;
import com.blanoir.accessory.hook.myhic.skills.AccessorySkills;
import com.blanoir.accessory.database.mysql.SqlManager;
import com.blanoir.accessory.module.inventory.AccessoryPageManager;
import com.blanoir.accessory.module.inventory.InvReload;
import com.blanoir.accessory.module.inventory.InvSave;
import com.blanoir.accessory.module.inventory.InvStore;
import com.blanoir.accessory.module.inventory.listener.InvListener;
import com.blanoir.accessory.utils.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class Accessory extends JavaPlugin {

    private Lang lang;
    private AccessoryService accessoryService;
    private AccessorySkills skillEngine;
    private InvStore inventoryStore;
    private AccessoryPageManager pageManager;
    private SqlManager sqlManager;

    public Lang lang() { return lang; }
    public AccessorySkills skillEngine() { return skillEngine; }
    public InvStore inventoryStore() { return inventoryStore; }
    public AccessoryPageManager pageManager() { return pageManager; }

    @Override
    public void onEnable() {
        initFiles();
        initLang();
        initSkillConfigs();
        initPageConfigs();

        initStorage();
        this.accessoryService = new AccessoryService(this);

        registerCommands();
        registerListeners();

        startAutoSaveTask();
        checkAndScheduleMythicHook();
        initAuraHookIfPresent();
    }

    public AccessoryService service() {
        return accessoryService;
    }

    public void reloadPluginSettings() {
        if (inventoryStore != null) {
            inventoryStore.flushAllAsync(totalAccessoryStorageSize()).join();
        }
        if (sqlManager != null) {
            sqlManager.shutdown();
            sqlManager = null;
        }
        reloadConfig();
        if (pageManager == null) {
            initPageConfigs();
        } else {
            pageManager.reload();
        }
        lang.reload();
        initStorage();
    }

    @Override
    public void onDisable() {
        if (inventoryStore != null) {
            int totalSize = totalAccessoryStorageSize();
            inventoryStore.flushAllAsync(totalSize).join();
            inventoryStore.shutdown();
        }
        if (sqlManager != null) {
            sqlManager.shutdown();
        }
        getLogger().info("Bye");
    }

    private void initStorage() {
        String typeRaw = getConfig().getString("database.type", "yml");
        InvStore.StorageType storageType = InvStore.StorageType.fromConfig(typeRaw);

        if (storageType == InvStore.StorageType.MYSQL) {
            this.sqlManager = new SqlManager(this);
            this.sqlManager.init(
                    getConfig().getString("database.mysql.host", "127.0.0.1"),
                    getConfig().getInt("database.mysql.port", 3306),
                    getConfig().getString("database.mysql.database", "minecraft"),
                    getConfig().getString("database.mysql.username", "root"),
                    getConfig().getString("database.mysql.password", "password"),
                    getConfig().getInt("database.mysql.pool-size", 10),
                    getConfig().getInt("database.mysql.min-idle", 2),
                    getConfig().getInt("database.mysql.max-lifetime", 1800000),
                    getConfig().getInt("database.mysql.connection-timeout", 10000),
                    getConfig().getInt("database.mysql.idle-timeout", 600000)
            );
            getLogger().info("Accessory storage mode: mysql");
        } else {
            this.sqlManager = null;
            getLogger().info("Accessory storage mode: yml");
        }

        this.inventoryStore = new InvStore(this, storageType, sqlManager);
    }

    private void startAutoSaveTask() {
        long period = 5L * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            int totalSize = totalAccessoryStorageSize();
            inventoryStore.flushAllAsync(totalSize);
        }, period, period);
    }

    private void initFiles() {
        getDataFolder().mkdirs();
        File statsFile = new File(getDataFolder(), "stats.yml");
        if (!statsFile.exists()) {
            saveResource("stats.yml", false);
        }
        saveDefaultConfig();
    }

    private void initPageConfigs() {
        File pageFolder = new File(getDataFolder(), "page");
        if (!pageFolder.exists()) {
            pageFolder.mkdirs();
        }
        savePageResourceIfMissing("page/page1.yml");
        savePageResourceIfMissing("page/page2.yml");

        this.pageManager = new AccessoryPageManager(this);
        this.pageManager.reload();
    }

    private void savePageResourceIfMissing(String path) {
        File file = new File(getDataFolder(), path);
        if (!file.exists()) {
            saveResource(path, false);
        }
    }

    private void initLang() {
        String LangFile = getConfig().getString("Lang","en_US");
        lang = new Lang(this,LangFile);
    }

    private void initSkillConfigs() {
        File skillFolder = new File(getDataFolder(), "skill");
        if (!skillFolder.exists()) {
            skillFolder.mkdirs();
        }
        File skillFile = new File(skillFolder, "skill.yml");
        if (!skillFile.exists()) {
            saveResource("skill/skill.yml", false);
        }
        File exampleFile = new File(skillFolder, "example.yml");
        if (!exampleFile.exists()) {
            saveResource("skill/example.yml", false);
        }
    }

    private void checkAndScheduleMythicHook() {
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            getLogger().warning("MythicMobs not found, Mythic skill bridge disabled.");
            this.skillEngine = null;
            return;
        }
        Bukkit.getScheduler().runTask(this, this::initMythicBridgeHook);
    }

    private void initMythicBridgeHook() {
        try {
            this.skillEngine = new AccessorySkills(this);
            this.skillEngine.loadConfig();
            this.skillEngine.startTimer();
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

    private void initAuraHookIfPresent() {
        if (Bukkit.getPluginManager().getPlugin("AuraSkills") != null) {
            Bukkit.getScheduler().runTask(this, () -> new AuraSkillsHook(this).load());
            return;
        }
        getLogger().warning("AuraSkills not found.");
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
        return accessorySize(1);
    }

    public int accessorySize(int page) {
        if (pageManager != null) {
            return pageManager.pageSize(page);
        }
        int size = getConfig().getInt("size", 9);
        size = Math.max(9, Math.min(54, size));
        return size - (size % 9);
    }

    public int accessoryPages() {
        int configuredPages = Math.max(1, getConfig().getInt("pages", 1));
        return pageManager == null ? configuredPages : pageManager.configuredPageCount(configuredPages);
    }

    public int accessoryPageStart(int page) {
        return pageManager == null ? (Math.max(1, page) - 1) * accessorySize() : pageManager.pageStart(page);
    }

    public int totalAccessoryStorageSize() {
        return pageManager == null ? accessorySize() * accessoryPages() : pageManager.totalStorageSize();
    }
}

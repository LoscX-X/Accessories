package com.blanoir.accessory;

import com.blanoir.accessory.api.AccessoryQuickEquipService;
import com.blanoir.accessory.api.AccessoryService;
import com.blanoir.accessory.command.AccessoryInventoryCommand;
import com.blanoir.accessory.command.TraitsCommand;
import com.blanoir.accessory.hook.aura.AuraSkillsHook;
import com.blanoir.accessory.hook.myhic.MythicBridgeListener;
import com.blanoir.accessory.hook.myhic.skills.AccessorySkillListener;
import com.blanoir.accessory.hook.myhic.skills.AccessorySkills;
import com.blanoir.accessory.hook.placeholderapi.SkillCooldownPlaceholder;
import com.blanoir.accessory.config.AccessorySettings;
import com.blanoir.accessory.config.ConfigFiles;
import com.blanoir.accessory.config.PageSettings;
import com.blanoir.accessory.module.attribute.loader.AccessoryLoad;
import com.blanoir.accessory.module.inventory.AccessoryStorage;
import com.blanoir.accessory.module.inventory.ui.AccessoryMenuController;
import com.blanoir.accessory.module.inventory.ui.AccessoryInventoryHolder;
import org.bukkit.inventory.ItemStack;
import com.blanoir.accessory.module.inventory.AccessoryPageManager;
import com.blanoir.accessory.module.inventory.AccessoryInventoryLifecycleListener;
import com.blanoir.accessory.module.inventory.AccessoryStore;
import com.blanoir.accessory.module.inventory.ItemLimitManager;
import com.blanoir.accessory.module.inventory.listener.AccessoryListener;
import com.blanoir.accessory.module.inventory.listener.AccessoryQuickEquipListener;
import com.blanoir.accessory.utils.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.util.List;

public final class Accessory extends JavaPlugin {

    private Lang lang;
    private AccessoryService accessoryService;
    private AccessorySkills skillEngine;
    private AccessoryStorage storage;
    private AccessorySettings settings;
    private AccessoryLoad effects;
    private AccessoryQuickEquipService quickEquipService;
    private AccessoryPageManager pageManager;
    private ItemLimitManager limitManager;
    private AccessoryMenuController menus;
    private final TraitsCommand shieldCommand = new TraitsCommand("accessory.shield", "shield");
    private final TraitsCommand magicShieldCommand = new TraitsCommand("accessory.magicshield", "magicshield");

    public Accessory() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = event.registrar();
            commands.register("accessory", "打开饰品背包或执行管理命令", java.util.List.of("acc", "acre"),
                    new AccessoryInventoryCommand(this));
            commands.register("shield", "调整当前物理护盾", shieldCommand);
            commands.register("magicshield", "调整当前魔法护盾", magicShieldCommand);
        });
    }

    public Lang lang() { return lang; }
    public AccessorySkills skillEngine() { return skillEngine; }
    public AccessoryStore inventoryStore() { return storage.store(); }
    public AccessorySettings settings() { return settings; }
    public AccessoryPageManager pageManager() { return pageManager; }
    public AccessoryQuickEquipService quickEquipService() { return quickEquipService; }
    public ItemLimitManager limitManager() { return limitManager; }
    public AccessoryMenuController menus() { return menus; }

    public List<String> antiUnequipLoreTags() {
        return settings.antiUnequipLore();
    }

    @Override
    public void onEnable() {
        ConfigFiles.initialize(this);
        limitManager = new ItemLimitManager(this);
        applySettings(readSettings());

        storage = new AccessoryStorage(this, settings.storage());
        effects = new AccessoryLoad(this);
        menus = new AccessoryMenuController(this);
        accessoryService = new AccessoryService(this);
        quickEquipService = new AccessoryQuickEquipService(this);

        registerListeners();
        startAutoSaveTask();
        checkAndScheduleMythicHook();
        initAuraHookIfPresent();
    }

    public AccessoryService service() { return accessoryService; }

    private LoadedSettings readSettings() {
        AccessorySettings values = AccessorySettings.read(getConfig());
        AccessoryPageManager pages = new AccessoryPageManager(getDataFolder(), getLogger());
        pages.reload(PageSettings.read(getConfig(), getLogger()), values.gui());
        return new LoadedSettings(values, pages, new Lang(this, values.language()));
    }

    private void applySettings(LoadedSettings loaded) {
        settings = loaded.values();
        pageManager = loaded.pages();
        lang = loaded.language();
        limitManager.reload();
    }

    public void reloadPluginSettings() {
        reloadConfig();
        LoadedSettings loaded = readSettings();
        AccessoryStorage replacement = storage.uses(loaded.values().storage())
                ? null : new AccessoryStorage(this, loaded.values().storage());
        // Close using the old layout so InventoryCloseEvent saves to the correct page offsets.
        closeAccessoryInventories();
        int oldSize = totalAccessoryStorageSize();
        if (replacement != null) {
            storage.close(oldSize);
            storage = replacement;
        }
        applySettings(loaded);
        if (skillEngine != null) skillEngine.loadConfig();
        for (Player online : Bukkit.getOnlinePlayers()) {
            refreshPlayerEffects(online, inventoryStore().getOrLoad(online.getUniqueId(), totalAccessoryStorageSize()));
        }
    }

    private record LoadedSettings(AccessorySettings values, AccessoryPageManager pages, Lang language) { }

    /** One pipeline for effects after equip, GUI edits, clear and reload. */
    public void refreshPlayerEffects(Player player, ItemStack[] contents) {
        effects.rebuildFromContents(player, contents);
        if (skillEngine != null) skillEngine.refreshPlayer(player, contents);
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            closeAccessoryInventories();
            storage.close(totalAccessoryStorageSize());
        }
    }

    private void closeAccessoryInventories() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AccessoryInventoryHolder) {
                player.closeInventory();
            }
        }
    }

    private void startAutoSaveTask() {
        long period = 5L * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this,
                () -> inventoryStore().flushAllAsync(totalAccessoryStorageSize()), period, period);
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
            registerSkillCooldownPlaceholder();
            getLogger().info("MythicMobs hook enabled (delayed init).");
        } catch (Throwable t) {
            this.skillEngine = null;
            getLogger().warning("MythicMobs hook failed, Mythic skill bridge disabled.");
        }
    }

    private void registerSkillCooldownPlaceholder() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new SkillCooldownPlaceholder(this).register();
            getLogger().info("PlaceholderAPI skill cooldown placeholders registered: %blacc_cd_1% ~ %blacc_cd_10%");
        } catch (Throwable t) {
            getLogger().warning("Failed to register skill cooldown placeholders: " + t.getMessage());
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
        pm.registerEvents(new AccessoryInventoryLifecycleListener(this), this);
        pm.registerEvents(new AccessoryListener(this), this);
        pm.registerEvents(new AccessoryQuickEquipListener(this, quickEquipService), this);
    }

    public void configureShieldCommands(com.blanoir.accessory.module.attribute.aura.traits.Absorb absorb,
                                        com.blanoir.accessory.module.attribute.aura.traits.MagicAbsorb magicAbsorb) {
        shieldCommand.configure(absorb::addShield, absorb::addShieldPercent);
        magicShieldCommand.configure(magicAbsorb::addShield, magicAbsorb::addShieldPercent);
    }

    public int accessorySize() { return accessorySize(1); }
    public int accessorySize(int page) { return pageManager.pageSize(page); }
    public int accessoryPages() { return pageManager.pageCount(); }
    public int accessoryPageStart(int page) { return pageManager.pageStart(page); }
    public int totalAccessoryStorageSize() { return pageManager.totalStorageSize(); }
}

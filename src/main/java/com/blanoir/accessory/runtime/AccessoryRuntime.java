package com.blanoir.accessory.runtime;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.api.AccessoryQuickEquipService;
import com.blanoir.accessory.api.AccessoryService;
import com.blanoir.accessory.config.AccessoryConfiguration;
import com.blanoir.accessory.config.ConfigFiles;
import com.blanoir.accessory.events.AccessoryChangeCause;
import com.blanoir.accessory.hook.AccessoryIntegrations;
import com.blanoir.accessory.module.attribute.AccessoryAttributes;
import com.blanoir.accessory.module.inventory.*;
import com.blanoir.accessory.module.inventory.listener.AccessoryListener;
import com.blanoir.accessory.module.inventory.listener.AccessoryQuickEquipListener;
import com.blanoir.accessory.module.inventory.ui.AccessoryMenuController;
import com.blanoir.accessory.module.lifecycle.AccessoryLifecycle;
import com.blanoir.accessory.module.lifecycle.AccessoryPlayerListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Composition root: startup, validated reconfiguration, resource shutdown. No item or attribute rules. */
public final class AccessoryRuntime {
    private final Accessory plugin;
    private AccessoryConfiguration configuration;
    private AccessoryStorage storage;
    private AccessoryLifecycle lifecycle;
    private ItemLimitManager limits;
    private AccessoryMenuController menus;
    private AccessoryService service;
    private AccessoryQuickEquipService quickEquip;
    private AccessoryIntegrations integrations;
    private com.blanoir.accessory.module.skill.SkillSystem skills;
    private final DebugLog debug;
    public DebugLog debug() { return debug; }
    public com.blanoir.accessory.module.skill.SkillSystem skills() { return skills; }
    private BukkitTask autoSave;
    private BukkitTask profilePoll;
    private com.blanoir.accessory.module.inventory.profile.AccessoryProfileManager profiles;
    public com.blanoir.accessory.module.inventory.profile.AccessoryProfileManager profiles() { return profiles; }
    private com.blanoir.accessory.module.inventory.profile.ProfileDefinitions readProfiles(AccessoryConfiguration config) {
        var definitions = com.blanoir.accessory.module.inventory.profile.ProfileDefinitions.load(new java.io.File(plugin.getDataFolder(), "profiles"),
                plugin.getLogger(), config.pages().pageCount(), config.pages()::pageSize);
        for (var profile : definitions.profiles()) {
            var pages = config.pages().forProfile(profile.inventory().layouts(), profile.inventory().title());
            profile.disabledSlots().forEach((page, slots) -> {
                if (page > pages.pageCount() || slots.stream().anyMatch(slot -> slot >= pages.pageSize(page)))
                    throw new IllegalArgumentException("Profile " + profile.id() + ": disabled slot outside its layout");
            });
        }
        return definitions;
    }

    public AccessoryRuntime(Accessory plugin) { this.plugin = plugin; debug = new DebugLog(plugin); }

    public void enable() {
        ConfigFiles.initialize(plugin);
        configuration = AccessoryConfiguration.read(plugin);
        AccessoryAttributes attributes = new AccessoryAttributes(plugin, configuration.settings().attributes());
        profiles = new com.blanoir.accessory.module.inventory.profile.AccessoryProfileManager(plugin, readProfiles(configuration));
        profiles.configureInventories(configuration.pages());
        limits = new ItemLimitManager(plugin);
        limits.reload();
        storage = new AccessoryStorage(plugin, configuration.settings().storage());
        menus = new AccessoryMenuController(plugin);
        service = new com.blanoir.accessory.module.inventory.service.DefaultAccessoryService(plugin);
        quickEquip = new com.blanoir.accessory.module.inventory.service.DefaultQuickEquipService(plugin);
        integrations = new AccessoryIntegrations(plugin);
        skills = new com.blanoir.accessory.module.skill.SkillSystem(plugin);
        var skillCatalog = skills.prepare();
        lifecycle = new AccessoryLifecycle(plugin, attributes);
        registerListeners();
        autoSave = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> storage.store().flushAllAsync(plugin.totalAccessoryStorageSize()), 6000L, 6000L);
        Bukkit.getScheduler().runTask(plugin, () -> {
            integrations.enable();
            skills.enable(skillCatalog);
            for (Player player : Bukkit.getOnlinePlayers()) lifecycle.load(player, AccessoryChangeCause.JOIN);
        });
        profilePoll = Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(profiles::poll), 20L, 20L);
        logAttributes();
    }

    public void reload() {
        plugin.reloadConfig();
        AccessoryConfiguration next = AccessoryConfiguration.read(plugin);
        var nextProfiles = readProfiles(next);
        var nextSkills = skills.prepare();
        AccessoryAttributes attributes = new AccessoryAttributes(plugin, next.settings().attributes());
        AccessoryStorage replacement = storage.uses(next.settings().storage()) ? null : new AccessoryStorage(plugin, next.settings().storage());
        menus.closeAll(); // Old page offsets must still be in effect while saving old windows.
        if (replacement != null) {
            try { storage.flush(plugin.totalAccessoryStorageSize()); }
            catch (RuntimeException ex) { replacement.shutdown(); throw ex; }
            storage.shutdown();
            storage = replacement;
        }
        lifecycle.replaceAttributes(attributes); // Remove sources using the previous provider.
        configuration = next;
        profiles.replace(nextProfiles);
        profiles.configureInventories(next.pages());
        limits.reload();
        skills.reload(nextSkills);
        for (Player player : Bukkit.getOnlinePlayers()) lifecycle.load(player, AccessoryChangeCause.RELOAD);
        logAttributes();
    }

    public void close() {
        if (autoSave != null) autoSave.cancel();
        if (profilePoll != null) profilePoll.cancel();
        try {
            if (menus != null) menus.closeAll();
            if (lifecycle != null) for (Player player : Bukkit.getOnlinePlayers()) {
                try { lifecycle.unload(player, AccessoryChangeCause.PLUGIN_DISABLE); }
                catch (RuntimeException | LinkageError ex) { debug.failure("lifecycle", "Could not remove player effects", ex); }
            }
        } finally {
            if (skills != null) skills.close();
            if (storage != null) storage.close(plugin.totalAccessoryStorageSize());
        }
    }

    private void registerListeners() {
        var manager = plugin.getServer().getPluginManager();
        manager.registerEvents(new AccessoryPlayerListener(plugin), plugin);
        manager.registerEvents(new AccessoryInventoryLifecycleListener(plugin), plugin);
        manager.registerEvents(new AccessoryListener(plugin), plugin);
        manager.registerEvents(new AccessoryQuickEquipListener(plugin, quickEquip), plugin);
    }

    private void logAttributes() {
        var selected = configuration.settings().attributes();
        plugin.getLogger().info("Accessory attributes: provider=" + selected.provider().pluginName() + ", vanilla=" + selected.vanilla());
    }

    public AccessoryConfiguration configuration() { return configuration; }
    public AccessoryStore store() { return storage.store(); }
    public AccessoryLifecycle lifecycle() { return lifecycle; }
    public ItemLimitManager limits() { return limits; }
    public AccessoryMenuController menus() { return menus; }
    public AccessoryService service() { return service; }
    public AccessoryQuickEquipService quickEquip() { return quickEquip; }
    public AccessoryIntegrations integrations() { return integrations; }
}

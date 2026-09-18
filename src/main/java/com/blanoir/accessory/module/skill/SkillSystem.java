package com.blanoir.accessory.module.skill;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.api.*;
import com.blanoir.accessory.events.AccessoryChangeCause;
import com.blanoir.accessory.hook.myhic.MythicBridgeListener;
import com.blanoir.accessory.hook.myhic.skills.*;
import com.blanoir.accessory.hook.placeholderapi.SkillCooldownPlaceholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.server.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.util.*;

/** Owns dependency availability, event registration, timer and cleanup as a single independent system. */
public final class SkillSystem implements AccessorySkills, Listener, AutoCloseable {
    private final Accessory plugin;
    private SkillCatalog catalog = new SkillCatalog(Map.of(), Map.of());
    private SkillEngine engine;
    private BukkitTask timer;
    private Runnable removePlaceholder;
    private final List<Listener> listeners = new ArrayList<>();
    public SkillSystem(Accessory plugin) { this.plugin = plugin; }
    public SkillCatalog prepare() { return SkillCatalog.load(new File(plugin.getDataFolder(), "skills"), plugin.getLogger()); }
    public void enable(SkillCatalog catalog) {
        this.catalog = catalog;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        start();
    }
    public void reload(SkillCatalog catalog) {
        this.catalog = catalog;
        if (!plugin.settings().skills().enabled()) { stop(); return; }
        if (engine == null) start(); else engine.catalog(catalog);
    }
    private void start() {
        if (engine != null || !plugin.settings().skills().enabled()) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            plugin.getLogger().info("Accessory skills inactive: MythicMobs is not enabled."); return;
        }
        try {
            engine = new SkillEngine(plugin, new MythicSkillBackend(), catalog);
            register(new SkillTriggerListener(plugin, engine));
            register(new MythicSkillListener(engine));
            register(new MythicBridgeListener(plugin));
            timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> engine.tick(), 1L, 1L);
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                var expansion = new SkillCooldownPlaceholder(plugin);
                expansion.register(); removePlaceholder = expansion::unregister;
            }
            plugin.getLogger().info("Accessory skills enabled: MythicMobs.");
        } catch (RuntimeException | LinkageError ex) {
            stop(); plugin.debug().failure("hooks", "Could not start accessory skills", ex);
        }
    }
    private void register(Listener listener) { listeners.add(listener); Bukkit.getPluginManager().registerEvents(listener, plugin); }
    private void stop() {
        if (timer != null) { timer.cancel(); timer = null; }
        listeners.forEach(HandlerList::unregisterAll); listeners.clear();
        if (removePlaceholder != null) { removePlaceholder.run(); removePlaceholder = null; }
        if (engine != null) { engine.close(); engine = null; }
    }
    @EventHandler public void dependencyDisabled(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("MythicMobs")) stop();
        if (event.getPlugin().getName().equals("PlaceholderAPI") && removePlaceholder != null) { removePlaceholder.run(); removePlaceholder = null; }
    }
    @EventHandler public void dependencyEnabled(PluginEnableEvent event) {
        if (!event.getPlugin().getName().equals("MythicMobs")) return;
        start();
        if (engine != null) for (Player player : Bukkit.getOnlinePlayers()) plugin.lifecycle().load(player, AccessoryChangeCause.RELOAD);
    }
    public void equip(Player owner, ItemStack[] active) { if (engine != null) engine.equip(owner, active); }
    public void detach(Player owner, AccessoryChangeCause cause) {
        if (engine != null) engine.detach(owner, cause == AccessoryChangeCause.QUIT || cause == AccessoryChangeCause.PLUGIN_DISABLE);
    }
    public boolean stamp(ItemStack item) { return engine != null && engine.stamp(item); }
    @Override public boolean enabled() { return engine != null; }
    @Override public List<AccessorySkillInfo> getSkills(Player owner) { return engine == null ? List.of() : engine.skills(owner); }
    @Override public Optional<AccessorySkillInfo> getSkill(Player owner, String skill) {
        return skill == null ? Optional.empty() : getSkills(owner).stream().filter(info -> info.skill().equalsIgnoreCase(skill)).findFirst();
    }
    @Override public String formatCooldown(Player owner, int slot) { return engine == null ? "" : engine.format(owner, slot); }
    @Override public void resetCooldowns(Player owner) { if (engine != null) engine.reset(owner); }
    @Override public void resetCooldowns() { if (engine != null) engine.reset(); }
    @Override public void close() { stop(); HandlerList.unregisterAll(this); }
}

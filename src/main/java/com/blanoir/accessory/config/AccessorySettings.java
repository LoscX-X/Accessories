package com.blanoir.accessory.config;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.module.inventory.AccessoryStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Main config values, read once at startup/reload instead of throughout the event pipeline. */
public record AccessorySettings(
        String language, Gui gui, boolean startupDebug, boolean skillDebug,
        boolean quickEquip, List<String> antiUnequipLore, boolean demonstrateLifeSteal,
        Shield physicalShield, Shield magicShield, Storage storage) {

    public AccessorySettings {
        antiUnequipLore = List.copyOf(antiUnequipLore);
    }

    public static AccessorySettings read(ConfigurationSection config) {
        List<String> tags = config.getStringList("anti-unequip.lore");
        // Ignore bundled defaults when deciding whether the old quick-equip alias is in use.
        String quickEquipPath = config.contains("quick-equip.shift-right-click", true)
                ? "quick-equip.shift-right-click" : "quick-equip.sneak-right-click";
        return new AccessorySettings(
                config.getString("Lang", "en_US"),
                new Gui(config.getString("title", "<green>Accessory"),
                        normalizeSize(config.getInt("size", 9))),
                config.getBoolean("debug-mode", false), config.getBoolean("skill-debug", false),
                config.getBoolean(quickEquipPath, true), tags.isEmpty() ? List.of("[Anti-unequip]") : tags,
                config.getBoolean("Life_Steal_demonstrate", false),
                new Shield(config.getInt("OUT_OF_COMBAT_SECONDS", 12), config.getDouble("SHIELD_REGEN_PERCENT", 0.1)),
                new Shield(config.getInt("MAGIC_SHIELD_OUT_OF_COMBAT_SECONDS", 12), config.getDouble("MAGIC_SHIELD_REGEN_PERCENT", 0.1)),
                new Storage(AccessoryStore.StorageType.fromConfig(config.getString("database.type", "yml")),
                        new Mysql(config.getString("database.mysql.host", "127.0.0.1"),
                                config.getInt("database.mysql.port", 3306),
                                config.getString("database.mysql.database", "minecraft"),
                                config.getString("database.mysql.username", "root"),
                                config.getString("database.mysql.password", "password"),
                                config.getInt("database.mysql.pool-size", 10),
                                config.getInt("database.mysql.min-idle", 2),
                                config.getInt("database.mysql.max-lifetime", 1800000),
                                config.getInt("database.mysql.connection-timeout", 10000),
                                config.getInt("database.mysql.idle-timeout", 600000))));
    }

    /** Retains support for trait handlers constructed with a plain JavaPlugin by API callers. */
    public static AccessorySettings current(JavaPlugin plugin) {
        return plugin instanceof Accessory accessory ? accessory.settings() : read(plugin.getConfig());
    }

    public static int normalizeSize(int size) {
        int bounded = Math.max(9, Math.min(54, size));
        return bounded - bounded % 9;
    }

    public record Gui(String title, int defaultSize) { }
    public record Shield(int outOfCombatSeconds, double regenPercent) { }
    public record Storage(AccessoryStore.StorageType type, Mysql mysql) { }
    public record Mysql(String host, int port, String database, String username, String password,
                        int poolSize, int minIdle, int maxLifetime, int connectionTimeout, int idleTimeout) {
        @Override
        public String toString() {
            return "Mysql[host=" + host + ", port=" + port + ", database=" + database + "]";
        }
    }
}

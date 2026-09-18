package com.blanoir.accessory.config;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.module.inventory.AccessoryStore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Main config values, read once at startup/reload instead of throughout the event pipeline. */
public record AccessorySettings(
        String language, Gui gui, DebugSettings debug, SkillSettings skills,
        boolean quickEquip, List<String> antiUnequipLore, boolean demonstrateLifeSteal,
        Shield physicalShield, Shield magicShield, Storage storage, Attributes attributes, AccessoryDeathPolicy deathPolicy) {

    public AccessorySettings {
        antiUnequipLore = List.copyOf(antiUnequipLore);
    }

    public static AccessorySettings read(ConfigurationSection config) {
        for (String obsolete : List.of("Lang", "debug-mode", "title", "size", "pages", "layout", "quick-equip", "anti-unequip",
                "item-limits", "skill-debug", "vanilla-lore-attributes", "database", "inventory.pages", "inventory.layout", "general.debug", "skills.debug")) {
            if (config.contains(obsolete, true)) throw new IllegalArgumentException("Unsupported config key " + obsolete + "; use the current config.yml and profiles/ format");
        }
        if (config.contains("attribute.provider") && !config.isString("attribute.provider")) {
            throw new IllegalArgumentException("attribute.provider must be a single provider name, not a list");
        }
        List<String> tags = config.getStringList("inventory.anti-unequip.lore");
        return new AccessorySettings(
                config.getString("general.language", "en_US"),
                new Gui(config.getString("inventory.title", "<green>Accessory"),
                        normalizeSize(config.getInt("inventory.size", 9))),
                DebugSettings.read(config), SkillSettings.read(config),
                config.getBoolean("inventory.quick-equip.shift-right-click", true), tags,
                config.getBoolean("aura.life-steal.show-heal-message", false),
                new Shield(config.getInt("aura.physical-shield.out-of-combat-seconds", 12), config.getDouble("aura.physical-shield.regen-percent", 0.1)),
                new Shield(config.getInt("aura.magic-shield.out-of-combat-seconds", 12), config.getDouble("aura.magic-shield.regen-percent", 0.1)),
                new Storage(AccessoryStore.StorageType.fromConfig(config.getString("storage.type", "yml")),
                        new Mysql(config.getString("storage.mysql.host", "127.0.0.1"),
                                config.getInt("storage.mysql.port", 3306),
                                config.getString("storage.mysql.database", "minecraft"),
                                config.getString("storage.mysql.username", "root"),
                                config.getString("storage.mysql.password", "password"),
                                config.getInt("storage.mysql.pool-size", 10),
                                config.getInt("storage.mysql.min-idle", 2),
                                config.getInt("storage.mysql.max-lifetime", 1800000),
                                config.getInt("storage.mysql.connection-timeout", 10000),
                                config.getInt("storage.mysql.idle-timeout", 600000))),
                new Attributes(AttributeProvider.parse(config.getString("attribute.provider", "none")),
                        config.getBoolean("attribute.vanilla", true), VanillaLoreSettings.read(config)),
                AccessoryDeathPolicy.parse(config.getString("lifecycle.death", "keep")));
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
    public record Attributes(AttributeProvider provider, boolean vanilla, VanillaLoreSettings lore) { }
    public record Storage(AccessoryStore.StorageType type, Mysql mysql) { }
    public record Mysql(String host, int port, String database, String username, String password,
                        int poolSize, int minIdle, int maxLifetime, int connectionTimeout, int idleTimeout) {
        @Override
        public String toString() {
            return "Mysql[host=" + host + ", port=" + port + ", database=" + database + "]";
        }
    }
}

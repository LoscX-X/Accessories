package com.blanoir.accessory.config;
import org.bukkit.configuration.ConfigurationSection;
public record SkillSettings(boolean enabled, boolean cancelledDamage, boolean resetCooldownsOnQuit) {
    public static SkillSettings read(ConfigurationSection config) {
        return new SkillSettings(config.getBoolean("skills.enabled", true), config.getBoolean("skills.triggers.cancelled-damage", false),
                config.getBoolean("skills.cooldowns.reset-on-quit", true));
    }
}

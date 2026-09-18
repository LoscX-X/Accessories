package com.blanoir.accessory.hook;
import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.hook.aura.AuraSkillsHook;
import org.bukkit.Bukkit;
/** Optional Aura trait extension. Skill lifecycle is owned by module.skill.SkillSystem. */
public final class AccessoryIntegrations {
    private final Accessory plugin;
    public AccessoryIntegrations(Accessory plugin) { this.plugin = plugin; }
    public void enable() {
        if (Bukkit.getPluginManager().isPluginEnabled("AuraSkills")) new AuraSkillsHook(plugin).load();
    }
}

package com.blanoir.accessory.traits;

import com.blanoir.accessory.attributeload.CustomTraits;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.bukkit.BukkitTraitHandler;
import dev.aurelium.auraskills.api.trait.Trait;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Health implements BukkitTraitHandler, Listener {

    private final AuraSkillsApi auraSkills;

    public Health(JavaPlugin plugin, AuraSkillsApi auraSkills) {
        this.auraSkills = auraSkills;
    }

    @Override
    public Trait[] getTraits() {
        return new Trait[]{CustomTraits.HEALTH};
    }

    @Override
    public double getBaseLevel(Player player, Trait trait) {
        return 0;
    }

    @Override
    public void onReload(Player player, SkillsUser user, Trait trait) {
        applyHealth(player, user);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SkillsUser user = auraSkills.getUser(player.getUniqueId());
        if (user == null || !user.isLoaded()) return;
        applyHealth(player, user);
    }

    private void applyHealth(Player player, SkillsUser user) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        double extra = user.getEffectiveTraitLevel(CustomTraits.HEALTH);
        double target = Math.max(1.0, 20.0 + extra);

        maxHealth.setBaseValue(target);
        if (player.getHealth() > target) {
            player.setHealth(target);
        }
    }
}

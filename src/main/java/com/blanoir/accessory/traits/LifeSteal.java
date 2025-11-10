package com.blanoir.accessory.traits;

import com.blanoir.accessory.attributeload.CustomTraits;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.bukkit.BukkitTraitHandler;
import dev.aurelium.auraskills.api.trait.Trait;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.blanoir.accessory.Accessory;

import java.util.List;

public class LifeSteal implements BukkitTraitHandler, Listener {
    private final AuraSkillsApi auraSkills;
    private final JavaPlugin plugin;
    private final Accessory plugins;

    public LifeSteal(JavaPlugin plugin, AuraSkillsApi auraSkills,Accessory plugins) {
        this.auraSkills = auraSkills;
        this.plugin = plugin;
        this.plugins = plugins;
    }

    @Override
    public Trait[] getTraits() {
        return new Trait[] { CustomTraits.LIFE_STEAL };
    }

    @Override
    public double getBaseLevel(Player player, Trait trait) {
        // Default: no life steal if stat is zero
        return 0.0;
    }

    @Override
    public void onReload(Player player, SkillsUser user, Trait trait) {
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        // Only player damage events
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        Entity victim = event.getEntity();
        if (victim instanceof Player) {
            // You can choose whether to allow life steal on players, or restrict to mobs
        }

        double damage = event.getFinalDamage();
        if (damage <= 0) return;

        // Get the player's trait level (the % life steal)
        SkillsUser user = auraSkills.getUser(attacker.getUniqueId());
        double lifeStealPercent = user.getEffectiveTraitLevel(CustomTraits.LIFE_STEAL);

        if (lifeStealPercent > 0) {
            double healAmount = damage * (lifeStealPercent / 100.0);
            double newHealth = Math.min(attacker.getHealth() + healAmount, attacker.getMaxHealth());

            attacker.setHealth(newHealth);
            if(plugin.getConfig().getBoolean("Life_Steal_demonstrate")){
            attacker.sendMessage(plugins.lang().lang("Life_steal_success")+String.format("%.1f", healAmount));}
        }
    }
}

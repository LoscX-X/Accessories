package com.blanoir.accessory.traits;

import com.blanoir.accessory.attributeload.CustomTraits;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.bukkit.BukkitTraitHandler;
import dev.aurelium.auraskills.api.trait.Trait;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class HealRegeneration implements BukkitTraitHandler {
    private final AuraSkillsApi auraSkills;
    private final JavaPlugin plugin;

    // Inject API dependency in constructor
    public HealRegeneration(JavaPlugin plugin, AuraSkillsApi auraSkills) {
        this.auraSkills = auraSkills;
        this.plugin = plugin;
    }

    @Override
    public Trait[] getTraits() {
        // An array containing your CustomTrait instance
        return new Trait[]{CustomTraits.HEAL_REGENERATION};
    }

    @Override
    public double getBaseLevel(Player player, Trait trait) {
        // The base value of your trait when its stat is at level 0, could be a
        // Minecraft default value or values from other plugins
        return 0;
    }

    @Override
    public void onReload(Player player, SkillsUser user, Trait trait) {
        // Method called when the value of the trait's parent stat changes
    }

    // Example implementation of the trait's functionality (not complete)
    public void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.isDead()) continue;

                SkillsUser user = auraSkills.getUser(p.getUniqueId());
                double hps = user.getEffectiveTraitLevel(CustomTraits.HEAL_REGENERATION);
                if (hps <= 0) continue;

                double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
                double cur = p.getHealth();
                if (cur >= max) continue;

                double healAmount = Math.min(hps, max - cur);

                // 🔥 关键：改为事件，让 debuff 能抵消
                EntityRegainHealthEvent event =
                        new EntityRegainHealthEvent(p, healAmount, EntityRegainHealthEvent.RegainReason.CUSTOM);
                Bukkit.getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    p.setHealth(Math.min(cur + event.getAmount(), max));
                }
            }
        }, 20L, 20L);
    }
}
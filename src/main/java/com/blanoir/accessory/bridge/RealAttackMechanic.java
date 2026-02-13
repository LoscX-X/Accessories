package com.blanoir.accessory.bridge;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

@MythicMechanic(name = "realattack")
public class RealAttackMechanic implements ITargetedEntitySkill {

    private final Plugin plugin;

    public RealAttackMechanic(Plugin plugin, MythicMechanicLoadEvent loader) {
        this.plugin = plugin;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata meta, AbstractEntity target) {
        if (meta == null || target == null) return SkillResult.CONDITION_FAILED;
        if (meta.getCaster() == null) return SkillResult.CONDITION_FAILED;

        Entity caster = BukkitAdapter.adapt(meta.getCaster().getEntity());
        Entity victim = BukkitAdapter.adapt(target);

        if (!(caster instanceof LivingEntity mob)) return SkillResult.CONDITION_FAILED;
        if (victim == null) return SkillResult.CONDITION_FAILED;
        if (!mob.getWorld().equals(victim.getWorld())) return SkillResult.CONDITION_FAILED;
        if (mob.isDead() || !mob.isValid()) return SkillResult.CONDITION_FAILED;

        Runnable attack = () -> {
            if (mob.isDead() || !mob.isValid()) return;
            if (!mob.getWorld().equals(victim.getWorld())) return;
            mob.attack(victim);
        };

        if (Bukkit.isPrimaryThread()) {
            attack.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, attack);
        }

        return SkillResult.SUCCESS;
    }
}

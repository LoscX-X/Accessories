package com.blanoir.accessory.module.skill;
import org.bukkit.entity.*;
@FunctionalInterface
public interface SkillBackend {
    boolean cast(Player caster, SkillDefinition skill, Entity target, Entity trigger);
}

package com.blanoir.accessory.module.skill;

import com.blanoir.accessory.module.skill.trigger.SkillTrigger;

/** Parsed ability definition; id is stable across item moves and config list reordering. */
public record SkillDefinition(String id, String skill, SkillTrigger trigger, Target target, int period,
                              int cooldown, boolean forceSync, boolean cancelEvent, int displaySlot, String displayFormat) {
    public enum Target {
        SELF, TARGETED, ATTACKER, PROJECTILE, NONE;
        public static Target defaultFor(SkillTrigger trigger) {
            return switch (trigger) {
                case ON_ATTACK, ON_CRITICAL_HIT, ON_KILL, ON_SKILL_CAST -> TARGETED;
                case ON_DAMAGED -> ATTACKER;
                case ON_SHOOT -> PROJECTILE;
                default -> SELF;
            };
        }
        public boolean supports(SkillTrigger trigger) {
            return switch (this) {
                case SELF, NONE -> true;
                case TARGETED -> trigger == SkillTrigger.ON_ATTACK || trigger == SkillTrigger.ON_CRITICAL_HIT
                        || trigger == SkillTrigger.ON_KILL || trigger == SkillTrigger.ON_SKILL_CAST;
                case ATTACKER -> trigger == SkillTrigger.ON_DAMAGED;
                case PROJECTILE -> trigger == SkillTrigger.ON_SHOOT;
            };
        }
    }
}

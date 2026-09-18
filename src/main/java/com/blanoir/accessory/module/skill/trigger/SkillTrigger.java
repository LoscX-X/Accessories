package com.blanoir.accessory.module.skill.trigger;
public enum SkillTrigger {
    ON_ATTACK("onAttack"), ON_CRITICAL_HIT("onCriticalHit"), ON_DAMAGED("onDamaged"),
    ON_SHOOT("onShoot"), ON_KILL("onKill"), ON_DEATH("onDeath"), ON_TIMER("onTimer"),
    ON_SKILL_CAST("onSkillCast"), ON_INTERACT("onInteract");
    private final String config;
    SkillTrigger(String config) { this.config = config; }
    public String configName() { return config; }
    public static SkillTrigger parse(String raw) {
        for (var value : values()) if (value.config.equals(raw)) return value;
        throw new IllegalArgumentException("Unknown trigger: " + raw);
    }
}

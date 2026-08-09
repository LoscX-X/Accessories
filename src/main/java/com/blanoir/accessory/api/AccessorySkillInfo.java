package com.blanoir.accessory.api;

/**
 * 外部 API 使用的技能冷却信息快照。
 *
 * @param skill     MythicMobs 技能名
 * @param trigger   触发方式（onAttack / onCriticalHit / onDamaged / onShoot / onKill / onDeath / onTimer / onSkillCast）
 * @param cooldown  总冷却秒数（0 表示无冷却）
 * @param remaining 剩余冷却秒数（向上取整，0 表示已就绪或未开始冷却）
 * @param ready     当前是否可施放
 */
public record AccessorySkillInfo(
        String skill,
        String trigger,
        int cooldown,
        int remaining,
        boolean ready
) {

    public AccessorySkillInfo {
        skill = skill == null ? "" : skill;
        trigger = trigger == null ? "" : trigger;
        cooldown = Math.max(0, cooldown);
        remaining = Math.max(0, remaining);
    }
}

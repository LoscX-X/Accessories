package com.blanoir.accessory.traits;

import com.blanoir.accessory.attributeload.CustomTraits;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.bukkit.BukkitTraitHandler;
import dev.aurelium.auraskills.api.trait.Trait;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Defence implements BukkitTraitHandler, Listener {
    private final AuraSkillsApi auraSkills;

    // 每 1 点 DEFENCE 对应的 MAGIC 减免比例（自己调）
    private static final double PER_LEVEL_REDUCE = 0.01; // 1点=1%
    private static final double REDUCE_CAP = 0.60;       // 最多60%

    public Defence(JavaPlugin plugin, AuraSkillsApi auraSkills) {
        this.auraSkills = auraSkills;
    }

    @Override
    public Trait[] getTraits() {
        return new Trait[]{ CustomTraits.DEFENCE };
    }

    @Override
    public double getBaseLevel(Player player, Trait trait) {
        return 0;
    }

    @Override
    public void onReload(Player player, SkillsUser user, Trait trait) { }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;

        // 只接管 MAGIC
        if (e.getCause() != EntityDamageEvent.DamageCause.MAGIC) return;

        SkillsUser user = auraSkills.getUser(p.getUniqueId());
        if (user == null || !user.isLoaded()) return;

        // AuraSkills 计算后的 trait level（double）
        double level = user.getEffectiveTraitLevel(CustomTraits.DEFENCE);
        if (level <= 0) return;

        double reduce = Math.min(REDUCE_CAP, level * PER_LEVEL_REDUCE); // 0~cap
        if (reduce <= 0) return;

        e.setDamage(e.getDamage() * (1.0 - reduce));
    }
}

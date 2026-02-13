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
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class HealRegDecrease implements BukkitTraitHandler, Listener {
    private final AuraSkillsApi auraSkills;
    private final JavaPlugin plugin;
    private static final String META_ACCESSORY_REGEN = "accessory_regen";
    private static final String META_ACCESSORY_LIFESTEAL = "accessory_lifesteal";
    public HealRegDecrease(JavaPlugin plugin, AuraSkillsApi auraSkills) {
        this.auraSkills = auraSkills;
        this.plugin = plugin;
    }

    @Override
    public Trait[] getTraits() {
        return new Trait[]{CustomTraits.HEAL_DECREASE};
    }

    @Override
    public double getBaseLevel(Player player, Trait trait) {
        return 0;
    }

    public void onReload(Player player, SkillsUser user, Trait trait) {
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        var r = event.getRegainReason();

        // 保护：CUSTOM 只处理我们自己的回血（避免削弱其他插件的 CUSTOM）
        if (r == EntityRegainHealthEvent.RegainReason.CUSTOM
                && !player.hasMetadata(META_ACCESSORY_REGEN)
                && !player.hasMetadata(META_ACCESSORY_LIFESTEAL)) {
            return;
        }

        SkillsUser user = auraSkills.getUser(player.getUniqueId());
        if (user == null || !user.isLoaded()) return;

        double dec = user.getEffectiveTraitLevel(CustomTraits.HEAL_DECREASE);
        if (dec <= 0) return;

        // ✅ 对所有回血生效：原版/药水/食物/金苹果/信标/自定义等，统一减去 dec
        event.setAmount(Math.max(0.0, event.getAmount() - dec));
    }

}

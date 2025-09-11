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

    public HealRegDecrease(JavaPlugin plugin, AuraSkillsApi auraSkills) {
        this.auraSkills = auraSkills;
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public Trait[] getTraits() {
        return new Trait[]{CustomTraits.HEAL_DECREASE};

    }

    @Override
    public double getBaseLevel(Player player, Trait trait) {
        // The base value of your trait when its stat is at level 0, could be a
        // Minecraft default value or values from other plugins
        return 0;
    }

    public void onReload(Player player, SkillsUser user, Trait trait) {
        // Method called when the value of the trait's parent stat changes
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // 只拦你想处理的回血原因（可按需删减）
        var r = event.getRegainReason(); // SATIATED, MAGIC, MAGIC_REGEN, EATING 等
        switch (r) { // 例：自然/饱食/再生/进食/药水类
            case SATIATED, REGEN, MAGIC_REGEN, MAGIC, EATING -> {
            }
            default -> {
                return;
            } // 其他原因不处理
        }

        SkillsUser user = auraSkills.getUser(player.getUniqueId());
        if (user == null || !user.isLoaded()) return;

        double dec = user.getEffectiveTraitLevel(CustomTraits.HEAL_DECREASE);
        if (dec <= 0) return;

        double amt = event.getAmount();                    // 本次将要回复的生命值
        double newAmt = Math.max(0.0, amt - dec);          // 下限 0
        event.setAmount(newAmt);                           // 写回事件
        // 如果要“完全抵消”，也可以：if (newAmt == 0) event.setCancelled(true);
    }
}

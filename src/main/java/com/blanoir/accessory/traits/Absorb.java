package com.blanoir.accessory.traits;

import com.blanoir.accessory.attributeload.CustomTraits;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.bukkit.BukkitTraitHandler;
import dev.aurelium.auraskills.api.trait.Trait;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Absorb implements BukkitTraitHandler, Listener {

    private final AuraSkillsApi auraSkills;
    private final JavaPlugin plugin;

    // 储存玩家护盾与上次受伤时间
    private final Map<UUID, Double> shieldMap = new HashMap<>();
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();

    // 配置参数
      // 脱战时间（秒）


    public Absorb(JavaPlugin plugin, AuraSkillsApi auraSkills) {
        this.plugin = plugin;
        this.auraSkills = auraSkills;
        int OUT_OF_COMBAT_SECONDS = plugin.getConfig().getInt("OUT_OF_COMBAT_SECONDS",12);
        // 定时任务：处理脱战恢复护盾逻辑
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    double maxShield = getMaxShield(player);
                    double currentShield = shieldMap.getOrDefault(uuid, 0.0);

                    // 满护盾视为战斗状态，避免继续恢复
                    if (currentShield >= maxShield && maxShield > 0) {
                        lastDamageTime.put(uuid, System.currentTimeMillis());
                        continue;
                    }

                    // 脱战且未满护盾 → 恢复逻辑
                    long lastHit = lastDamageTime.getOrDefault(uuid, 0L);
                    if (System.currentTimeMillis() - lastHit > OUT_OF_COMBAT_SECONDS * 1000L) {
                        regenShield(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /* ---------------- BukkitTraitHandler ---------------- */

    @Override
    public Trait[] getTraits() {
        return new Trait[]{CustomTraits.ABSORB};
    }

    @Override
    public double getBaseLevel(Player player, Trait trait) {
        return 0.0; // Trait 基础值为 0
    }

    @Override
    public void onReload(Player player, SkillsUser user, Trait trait) {
    }

    /* ---------------- Listeners ---------------- */

    // 玩家加入时初始化护盾
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        initShield(event.getPlayer());
    }

    // 玩家被攻击时：先标记战斗状态再扣除护盾
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        lastDamageTime.put(uuid, System.currentTimeMillis()); // 标记战斗状态

        double shield = shieldMap.getOrDefault(uuid, 0.0);
        if (shield <= 0) return; // 没有护盾则不干预伤害

        double damage = event.getDamage();
        if (shield >= damage) {
            shieldMap.put(uuid, shield - damage);
            event.setDamage(0); // 完全吸收
        } else {
            shieldMap.put(uuid, 0.0);
            event.setDamage(damage - shield); // 部分吸收
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        shieldMap.remove(uuid);
        lastDamageTime.remove(uuid);
    }

    /* ---------------- Logic ---------------- */

    private void initShield(Player player) {
        double max = getMaxShield(player);
        if (max > 0) {
            shieldMap.put(player.getUniqueId(), max);
        } else {
            shieldMap.remove(player.getUniqueId());
        }
    }

    // 获取护盾上限（来自 AuraSkills）
    public double getMaxShield(Player player) {
        SkillsUser user = auraSkills.getUser(player.getUniqueId());
        if (user == null) return 0.0;
        return user.getEffectiveTraitLevel(CustomTraits.ABSORB);
    }

    // 护盾恢复逻辑：按百分比补给
    private void regenShield(Player player) {
        UUID uuid = player.getUniqueId();
        double maxShield = getMaxShield(player);
        double SHIELD_REGEN_PERCENT = plugin.getConfig().getDouble("SHIELD_REGEN_PERCENT",0.1);
        if (maxShield <= 0) return;

        double current = shieldMap.getOrDefault(uuid, 0.0);

        if (current < maxShield) {
            current += maxShield * SHIELD_REGEN_PERCENT;
            if (current > maxShield) current = maxShield;
            shieldMap.put(uuid, current);
        }
    }

    // PAPI 支持：读取当前护盾
    public double getCurrentShield(Player player) {
        UUID uuid = player.getUniqueId();
        double current = shieldMap.getOrDefault(uuid, 0.0);
        double max = getMaxShield(player);
        if (current > max) {
            shieldMap.put(uuid, max);
            return max;
        }
        return current;
    }
}

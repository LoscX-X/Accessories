package com.blanoir.accessory.traits;

import com.blanoir.accessory.attributeload.CustomTraits;
import com.blanoir.accessory.utils.ShieldUtil;
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
import org.bukkit.event.entity.EntityDamageEvent;
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
                        if (!player.getScoreboardTags().contains("acc_noabsorb")){
                            regenShield(player);
                        }
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
        if (event.getCause() == EntityDamageEvent.DamageCause.MAGIC) return;
        UUID uuid = player.getUniqueId();
        lastDamageTime.put(uuid, System.currentTimeMillis()); // 标记战斗状态

        double shield = shieldMap.getOrDefault(uuid, 0.0);
        if (shield <= 0) return; // 没有护盾则不干预伤害

        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0) return;

        double base = event.getDamage();

        if (shield >= finalDamage) {
            // —— 完全吸收 ——
            shieldMap.put(uuid, shield - finalDamage);

            // 关键：把 base 缩放到 0，而不是 setDamage(finalDamage)
            event.setDamage(0.0);

        } else {
            // —— 部分吸收 ——
            double newFinal = finalDamage - shield;
            shieldMap.put(uuid, 0.0);

            // 关键：比例缩放 base，保证最终伤害 = newFinal
            double scale = newFinal / finalDamage;
            event.setDamage(base * scale);
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

        // ✅ 如果有 Mythic Aura：Anti_absorb -> 不恢复
        if (hasMythicAura(player, "Anti_absorb")) {
            return;
        }

        double maxShield = getMaxShield(player);
        double SHIELD_REGEN_PERCENT = plugin.getConfig()
                .getDouble("SHIELD_REGEN_PERCENT", 0.1);

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
        return ShieldUtil.getCurrentShield(player, shieldMap, this::getMaxShield);
    }
    public void addShield(org.bukkit.entity.Player player, double delta) {
        java.util.UUID uuid = player.getUniqueId();
        double max = getMaxShield(player);
        if (max <= 0) return;

        double cur = shieldMap.getOrDefault(uuid, 0.0);
        double v = cur + delta;
        v = Math.max(0.0, Math.min(v, max));
        shieldMap.put(uuid, v);
    }

    // ratio: 0.4 = +40% max; -0.2 = -20% max
    public void addShieldPercent(org.bukkit.entity.Player player, double ratio) {
        double max = getMaxShield(player);
        if (max <= 0) return;
        addShield(player, max * ratio);
    }
    private boolean hasMythicAura(Player player, String auraName) {
        try {
            var am = io.lumine.mythic.bukkit.MythicBukkit.inst()
                    .getSkillManager()
                    .getAuraManager();

            var ae = io.lumine.mythic.bukkit.BukkitAdapter.adapt(player);

            return am.getHasAura(ae, auraName);

        } catch (Throwable t) {
            // Mythic 没装 / 出错时保险
            return false;
        }
    }

}

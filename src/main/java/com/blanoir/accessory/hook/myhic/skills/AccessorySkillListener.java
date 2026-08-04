package com.blanoir.accessory.hook.myhic.skills;

import com.blanoir.accessory.Accessory;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.events.MythicSkillEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class AccessorySkillListener implements Listener {

    private final Accessory plugin;

    public AccessorySkillListener(Accessory plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.skillEngine().refreshFromStored(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.skillEngine().onQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onAttack(EntityDamageByEntityEvent event) {

        var cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }

        // damager 本体为玩家
        if (!(event.getDamager() instanceof Player attacker)) return;

        plugin.skillEngine().triggerAttack(attacker, event.getEntity());

        // Paper 已按原版条件标记该次伤害是否为暴击（跳劈/满蓄力等）。
        if (event.isCritical()) {
            plugin.skillEngine().triggerCriticalHit(attacker, event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Entity attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            attacker = byEntity.getDamager();
            if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooterEntity) {
                attacker = shooterEntity;
            }
        }
        plugin.skillEngine().triggerDamaged(player, attacker);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        plugin.skillEngine().triggerKill(killer, event.getEntity());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // 先取消死亡再释放技能：死亡类 aura/效果挂在“存活”玩家身上，不会被死亡状态清掉
        boolean cancelled = plugin.skillEngine().shouldCancelDeath(player);
        if (cancelled) {
            event.setCancelled(true);
        }

        boolean anyCancelCast = plugin.skillEngine().triggerDeath(player);

        // 取消后技能实际没放出来（释放失败）时恢复死亡流程，避免凭空免死
        if (cancelled && !anyCancelCast) {
            event.setCancelled(false);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        plugin.skillEngine().triggerShoot(player, event.getEntity());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        plugin.skillEngine().triggerShoot(player, event.getProjectile());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSkillCast(MythicSkillEvent event) {
        SkillMetadata meta = event.getSkillMetadata();
        // 饰品自己通过 API 施放的技能不再触发 onSkillCast，避免无限循环。
        if (meta.getMetadata(AccessorySkills.ACCESSORY_CAST_META).isPresent()) return;

        if (meta.getCaster() == null || meta.getCaster().getEntity() == null) return;
        if (!(BukkitAdapter.adapt(meta.getCaster().getEntity()) instanceof Player player)) return;

        Entity target = null;
        if (meta.hasEntityTargets()) {
            for (AbstractEntity entityTarget : meta.getEntityTargets()) {
                target = BukkitAdapter.adapt(entityTarget);
                if (target != null) break;
            }
        }
        if (target == null && meta.getTrigger() != null) {
            target = BukkitAdapter.adapt(meta.getTrigger());
        }
        // target 与 trigger 都取被施放技能的目标实体，<target.xxx> / <trigger.xxx> 占位符可正常解析。
        plugin.skillEngine().triggerSkillCast(player, target, target);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        plugin.skillEngine().clearShootFlag(event.getEntity());
    }

    private Player playerFromDamager(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player p) return p;
        }
        return null;
    }
}

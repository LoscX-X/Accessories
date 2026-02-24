package com.blanoir.accessory.bridge;

import com.blanoir.accessory.Accessory;
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
        Player attacker = playerFromDamager(event.getDamager());
        if (attacker == null) return;
        plugin.skillEngine().triggerAttack(attacker, event.getEntity());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
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

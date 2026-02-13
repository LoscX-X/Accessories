package com.blanoir.accessory.traits;

import com.blanoir.accessory.attributeload.CustomTraits;
import com.blanoir.accessory.events.MagicShieldRegenEvent;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MagicAbsorb implements BukkitTraitHandler, Listener {

    private final AuraSkillsApi auraSkills;
    private final JavaPlugin plugin;

    // 当前魔法护盾值
    private final Map<UUID, Double> shieldMap = new HashMap<>();
    // 脱战判定（建议用“任何受伤都算战斗中”，避免挨打时回盾）
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();

    private final int OUT_OF_COMBAT_SECONDS;
    private final double SHIELD_REGEN_PERCENT;

    public MagicAbsorb(JavaPlugin plugin, AuraSkillsApi auraSkills) {
        this.plugin = plugin;
        this.auraSkills = auraSkills;

        this.OUT_OF_COMBAT_SECONDS = plugin.getConfig().getInt("MAGIC_SHIELD_OUT_OF_COMBAT_SECONDS", 12);
        this.SHIELD_REGEN_PERCENT = plugin.getConfig().getDouble("MAGIC_SHIELD_REGEN_PERCENT", 0.1);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();

                    double maxShield = getMaxShield(player);
                    double currentShield = shieldMap.getOrDefault(uuid, 0.0);

                    // 没有上限就清掉
                    if (maxShield <= 0) {
                        shieldMap.remove(uuid);
                        continue;
                    }

                    // 满盾视为“仍在战斗中”，避免一直 regen 触发
                    if (currentShield >= maxShield) {
                        lastDamageTime.put(uuid, System.currentTimeMillis());
                        continue;
                    }

                    long lastHit = lastDamageTime.getOrDefault(uuid, 0L);
                    if (System.currentTimeMillis() - lastHit > OUT_OF_COMBAT_SECONDS * 1000L) {
                        if (!player.getScoreboardTags().contains("acc_nomabsorb")) {
                        regenShield(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @Override
    public Trait[] getTraits() {
        return new Trait[]{ CustomTraits.MAGICABSORB }; // 你需要在 CustomTraits 里新增这个 trait
    }

    @Override
    public double getBaseLevel(Player player, Trait trait) {
        return 0.0;
    }

    @Override
    public void onReload(Player player, SkillsUser user, Trait trait) {}

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        initShield(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        shieldMap.remove(uuid);
        lastDamageTime.remove(uuid);
    }

    // 任何受伤都算进“战斗中”，防止挨刀时回盾（你想只按魔法受伤判定也行）
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onAnyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        lastDamageTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    // 只接管 MAGIC：扣魔法盾并抵消魔法伤害
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMagicDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.MAGIC) return;

        UUID uuid = player.getUniqueId();

        double shield = shieldMap.getOrDefault(uuid, 0.0);
        if (shield <= 0) return;

        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0) return;

        // 吸收量（按最终伤害来吸，保证“真抵消”）
        double absorbed = Math.min(shield, finalDamage);
        double newFinal = finalDamage - absorbed;

        // 扣盾
        shieldMap.put(uuid, shield - absorbed);

        // 把基础伤害按比例缩放，让最终伤害变成 newFinal
        double base = event.getDamage();
        double scale = (finalDamage == 0) ? 0 : (newFinal / finalDamage);
        event.setDamage(base * scale);
    }

    private void initShield(Player player) {
        double max = getMaxShield(player);
        if (max > 0) shieldMap.put(player.getUniqueId(), max);
        else shieldMap.remove(player.getUniqueId());
    }

    // 上限：来自 AuraSkills trait level
    public double getMaxShield(Player player) {
        SkillsUser user = auraSkills.getUser(player.getUniqueId());
        if (user == null || !user.isLoaded()) return 0.0;
        return user.getEffectiveTraitLevel(CustomTraits.MAGICABSORB);
    }

    private void regenShield(Player player) {
        UUID uuid = player.getUniqueId();
        double max = getMaxShield(player);
        if (max <= 0) return;

        double cur = shieldMap.getOrDefault(uuid, 0.0);
        if (cur >= max) return;

        double regenAmount = max * SHIELD_REGEN_PERCENT;
        MagicShieldRegenEvent regenEvent = new MagicShieldRegenEvent(player, cur, max, regenAmount);
        Bukkit.getPluginManager().callEvent(regenEvent);
        if (regenEvent.isCancelled()) return;

        double finalRegenAmount = Math.max(0.0, regenEvent.getAmount());
        if (finalRegenAmount <= 0.0) return;

        shieldMap.put(uuid, Math.min(max, cur + finalRegenAmount));
    }

    public double getCurrentShield(Player player) {
        return ShieldUtil.getCurrentShield(player, shieldMap, this::getMaxShield);
    }
}

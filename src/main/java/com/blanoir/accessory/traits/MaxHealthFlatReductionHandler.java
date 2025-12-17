package com.blanoir.accessory.traits;

import com.blanoir.accessory.attributeload.CustomTraits;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.bukkit.BukkitTraitHandler;
import dev.aurelium.auraskills.api.trait.Trait;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MaxHealthFlatReductionHandler implements BukkitTraitHandler {

    private final JavaPlugin plugin;
    private final AuraSkillsApi api;

    // ⭐ 关键：保存玩家未被修改前的原始 MaxHealth
    private final Map<UUID, Double> originalBase = new HashMap<>();

    public MaxHealthFlatReductionHandler(JavaPlugin plugin, AuraSkillsApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    @Override
    public Trait[] getTraits() {
        return new Trait[]{ CustomTraits.MAX_HEALTH_FLAT_REDUCTION };
    }

    @Override
    public double getBaseLevel(Player player, Trait trait) {
        return 0;
    }

    @Override
    public void onReload(Player player, SkillsUser user, Trait trait) {

        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;

        double flatReduce = user.getEffectiveTraitLevel(CustomTraits.MAX_HEALTH_FLAT_REDUCTION);

        // ⭐ Trait 已取消 → 还原 MaxHealth
        if (flatReduce == 0) {
            Double orig = originalBase.remove(player.getUniqueId());
            if (orig != null) inst.setBaseValue(orig);
            return;
        }

        // ⭐ 第一次出现 Trait → 保存原始 MaxHealth
        originalBase.putIfAbsent(player.getUniqueId(), inst.getBaseValue());

        double base = originalBase.get(player.getUniqueId());

        // ⭐ 设置减少后的 MaxHealth
        inst.setBaseValue(Math.max(1, base - flatReduce));
    }
}

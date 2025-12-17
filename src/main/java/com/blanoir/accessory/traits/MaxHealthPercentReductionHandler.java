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

public class MaxHealthPercentReductionHandler implements BukkitTraitHandler {

    private final JavaPlugin plugin;
    private final AuraSkillsApi api;

    public MaxHealthPercentReductionHandler(JavaPlugin plugin, AuraSkillsApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    @Override
    public Trait[] getTraits() {
        return new Trait[]{ CustomTraits.MAX_HEALTH_PERCENT_REDUCTION };
    }

    @Override
    public double getBaseLevel(Player player, Trait trait) {
        return 0;
    }

    @Override
    public void onReload(Player player, SkillsUser user, Trait trait) {
        double percent = user.getEffectiveTraitLevel(CustomTraits.MAX_HEALTH_PERCENT_REDUCTION);
        if (percent == 0) return;

        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;

        double base = inst.getBaseValue();
        double reduce = base * (percent / 100.0);

        inst.setBaseValue(Math.max(1, base - reduce));
    }
}

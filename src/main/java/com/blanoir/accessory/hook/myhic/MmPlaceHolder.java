package com.blanoir.accessory.hook.myhic;
import com.blanoir.accessory.module.attribute.aura.traits.Absorb;
import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.skills.placeholders.PlaceholderManager;
import io.lumine.mythic.core.skills.placeholders.all.FunctionalEntityPlaceholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("deprecation")
public final class MmPlaceHolder {

    // <caster.shield>
    public static void registerShieldPlaceholder(JavaPlugin plugin, Absorb absorb) {
        PlaceholderManager pm = MythicProvider.get().getPlaceholderManager();

        pm.register("caster.shield", new FunctionalEntityPlaceholder((entity, arg) -> {
            if (entity == null || !entity.isPlayer()) return "0";

            Player p = (Player) entity.getBukkitEntity();
            double shield = absorb.getCurrentShield(p);

            // 可选：格式化
            return String.valueOf(shield);
        }));

        plugin.getLogger().info("[Accessory] registered placeholder: <caster.shield>");
    }
}

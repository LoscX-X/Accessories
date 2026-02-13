package com.blanoir.accessory.bridge;
import com.blanoir.accessory.traits.Absorb;
import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.skills.placeholders.PlaceholderManager;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.skills.placeholders.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MmPlaceHolder {

    // <caster.shield>
    public static void registerShieldPlaceholder(JavaPlugin plugin, Absorb absorb) {
        PlaceholderManager pm = MythicProvider.get().getPlaceholderManager();

        pm.register("caster.shield", Placeholder.meta((meta, arg) -> {
            if (meta.getCaster() == null) return "0";
            var ae = meta.getCaster().getEntity();
            if (ae == null || !ae.isPlayer()) return "0";

            Player p = (Player) ae.getBukkitEntity();
            double shield = absorb.getCurrentShield(p);

            // 可选：格式化
            return String.valueOf(shield);
        }));

        plugin.getLogger().info("[Accessory] registered placeholder: <caster.shield>");
    }
}

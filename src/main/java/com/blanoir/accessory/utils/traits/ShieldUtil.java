package com.blanoir.accessory.utils.traits;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

public final class ShieldUtil {
    private ShieldUtil() {}

    public static double getCurrentShield(Player player,
                                          Map<UUID, Double> shieldMap,
                                          ToDoubleFunction<Player> maxSupplier) {
        UUID uuid = player.getUniqueId();
        double cur = shieldMap.getOrDefault(uuid, 0.0);
        double max = maxSupplier.applyAsDouble(player);

        if (cur > max) {
            shieldMap.put(uuid, max);
            return max;
        }
        return cur;
    }
}

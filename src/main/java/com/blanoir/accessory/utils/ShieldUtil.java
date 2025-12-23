package com.blanoir.accessory.utils;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

public final class ShieldUtil {
    private ShieldUtil() {}

    /**
     * 读取并修正当前护盾：如果当前护盾 > 最大护盾，就自动钳制到最大护盾并写回 map
     */
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

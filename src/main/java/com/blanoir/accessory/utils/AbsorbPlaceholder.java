package com.blanoir.accessory.utils;

import com.blanoir.accessory.traits.Absorb;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AbsorbPlaceholder extends PlaceholderExpansion {

    private final Absorb absorbTrait;

    public AbsorbPlaceholder(Absorb absorbTrait) {
        this.absorbTrait = absorbTrait;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "absorb"; // 占位符前缀 %absorb_*
    }

    @Override
    public @NotNull String getAuthor() {
        return "Blanoir"; // 你可以改成自己的名字
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0"; // 扩展版本
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public boolean persist() {
        return true; // 确保在重载时不取消注册
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";

        double currentShield = absorbTrait.getCurrentShield(player);
        double maxShield = absorbTrait.getMaxShield(player);

        switch (identifier.toLowerCase()) {
            case "current_shield":
                return String.format("%.1f", currentShield);
            case "max_shield":
                return String.format("%.1f", maxShield);
            case "shield_percent":
                if (maxShield <= 0) return "0%";
                double percent = (currentShield / maxShield) * 100;
                return String.format("%.1f%%", percent);
        }

        return null; // 未识别的占位符
    }
}

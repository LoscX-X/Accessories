package com.blanoir.accessory.hooks;

import com.blanoir.accessory.traits.MagicAbsorb;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MagicAbsorbPlaceholder extends PlaceholderExpansion {

    private final MagicAbsorb magicAbsorb;

    public MagicAbsorbPlaceholder(MagicAbsorb magicAbsorb) {
        this.magicAbsorb = magicAbsorb;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "magicabsorb"; // %magicabsorb_*
    }

    @Override
    public @NotNull String getAuthor() {
        return "Blanoir";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) return "";

        double currentShield = magicAbsorb.getCurrentShield(player);
        double maxShield = magicAbsorb.getMaxShield(player);

        switch (identifier.toLowerCase()) {
            case "current_shield":
                return String.format("%.1f", currentShield);
            case "max_shield":
                return String.format("%.1f", maxShield);
            case "shield_percent":
                if (maxShield <= 0) return "0%";
                double percent = (currentShield / maxShield) * 100.0;
                return String.format("%.1f%%", percent);
        }
        return null;
    }
}

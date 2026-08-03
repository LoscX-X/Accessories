package com.blanoir.accessory.hook.placeholderapi;

import com.blanoir.accessory.Accessory;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 饰品技能冷却占位符：%blacc_cd_1% ~ %blacc_cd_10%。
 *
 * <p>槽位与格式在 skill/*.yml 的每个技能里配置：cd 指定 1-10 槽位，
 * cd-format 自定义显示格式（支持 {cd} 剩余秒 / {max} 总冷却 / {skill} 技能名）。
 * 未指定 cd 的技能按冷却时长升序自动填入剩余槽位（冷却短的在上）。</p>
 */
public final class SkillCooldownPlaceholder extends PlaceholderExpansion {

    private final Accessory plugin;

    public SkillCooldownPlaceholder(Accessory plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "blacc";
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
        if (player == null) {
            return "";
        }

        String lower = identifier.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("cd_")) {
            return null;
        }

        try {
            int slot = Integer.parseInt(lower.substring(3));
            if (plugin.skillEngine() == null) {
                return "";
            }
            return plugin.skillEngine().formatCooldown(player, slot);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

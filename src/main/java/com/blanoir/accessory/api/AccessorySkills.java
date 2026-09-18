package com.blanoir.accessory.api;
import org.bukkit.entity.Player;
import java.util.*;
/** Server-thread skill query/control contract; no MythicMobs types leak into the API. */
public interface AccessorySkills {
    boolean enabled();
    List<AccessorySkillInfo> getSkills(Player player);
    Optional<AccessorySkillInfo> getSkill(Player player, String skill);
    String formatCooldown(Player player, int displaySlot);
    void resetCooldowns(Player player);
    void resetCooldowns();
}

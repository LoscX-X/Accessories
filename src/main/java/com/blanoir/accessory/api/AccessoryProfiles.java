package com.blanoir.accessory.api;

import org.bukkit.entity.Player;
import java.util.UUID;

/** Server-thread API. Assignments and bans last for the login session, including ONLY_RAM games. */
public interface AccessoryProfiles {
    AccessoryProfile resolve(Player owner);
    void assign(UUID owner, String profileId);
    void clearAssignment(UUID owner);
    void setBlocked(UUID owner, AccessoryAction action, boolean blocked);
    boolean isAllowed(Player owner, AccessoryAction action);
    boolean isSlotAvailable(Player owner, int page, int slot);
    void refresh(Player owner);
}

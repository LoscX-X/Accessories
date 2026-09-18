package com.blanoir.accessory.api;

import com.blanoir.accessory.events.AccessoryChangeCause;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.concurrent.CompletionStage;

/** Inventory control contract. Mutations/open require the server thread. Reads return independent clones. */
public interface AccessoryService {
    CompletionStage<ItemStack[]> read(UUID owner);
    void open(Player viewer, Player owner, AccessoryViewMode mode);
    void setBlocked(UUID owner, AccessoryAction action, boolean blocked);
    boolean isAllowed(Player owner, AccessoryAction action);
    void setSlotEnabled(int slot, boolean enabled);
    void setDisabledSlots(Collection<Integer> slots);
    List<Integer> getDisabledSlots();
    boolean isSlotDisabled(int slot);
    boolean clear(UUID owner);
    boolean clear(UUID owner, Player actor, AccessoryChangeCause cause);
}

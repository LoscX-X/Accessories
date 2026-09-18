package com.blanoir.accessory.module.lifecycle;

import com.blanoir.accessory.events.AccessoryChangeCause;
import com.blanoir.accessory.events.AccessoryEquipEvent;
import com.blanoir.accessory.events.AccessoryUnequipEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Pure snapshot comparison; changes never edit inventories or apply attributes. */
final class EquipmentChanges {
    record Slot(int page, int slot) { }
    private EquipmentChanges() { }

    static List<Event> between(Player owner, Player actor, Map<Slot, ItemStack> previous,
                                Map<Slot, ItemStack> next, AccessoryChangeCause cause) {
        Set<Slot> slots = new LinkedHashSet<>(previous.keySet());
        slots.addAll(next.keySet());
        List<Event> events = new ArrayList<>();
        for (Slot slot : slots) {
            ItemStack old = previous.get(slot), item = next.get(slot);
            if (Objects.equals(old, item)) continue;
            if (old != null) events.add(new AccessoryUnequipEvent(owner, actor, slot.page(), slot.slot(), old, item, cause));
            if (item != null) events.add(new AccessoryEquipEvent(owner, actor, slot.page(), slot.slot(), item, old, cause));
        }
        return List.copyOf(events);
    }
}

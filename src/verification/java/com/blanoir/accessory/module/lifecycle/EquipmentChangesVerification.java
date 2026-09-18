package com.blanoir.accessory.module.lifecycle;

import com.blanoir.accessory.config.AccessoryDeathPolicy;
import com.blanoir.accessory.events.*;
import com.blanoir.accessory.verification.Checks;
import com.blanoir.accessory.verification.TestItem;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class EquipmentChangesVerification {
    public static void run() {
        var slot = new EquipmentChanges.Slot(2, 4);
        ItemStack ring = new TestItem("ring"), replacement = new TestItem("other");
        var added = EquipmentChanges.between(null, null, Map.of(), Map.of(slot, ring), AccessoryChangeCause.GUI);
        Checks.that(added.size() == 1 && added.getFirst() instanceof AccessoryEquipEvent, "equip emitted once");
        var same = EquipmentChanges.between(null, null, Map.of(slot, ring), Map.of(slot, ring.clone()), AccessoryChangeCause.GUI);
        Checks.that(same.isEmpty(), "same snapshot close/refresh is silent");
        var swapped = EquipmentChanges.between(null, null, Map.of(slot, ring), Map.of(slot, replacement), AccessoryChangeCause.QUICK_EQUIP);
        Checks.that(swapped.size() == 2 && swapped.get(0) instanceof AccessoryUnequipEvent && swapped.get(1) instanceof AccessoryEquipEvent, "replace emits unequip then equip");
        var event = (AccessoryEquipEvent) swapped.get(1);
        Checks.that(event.getPage() == 2 && event.getSlot() == 4 && event.getCause() == AccessoryChangeCause.QUICK_EQUIP, "event context preserved");
        replacement.setAmount(5); event.getItem().setAmount(10);
        Checks.that(event.getItem().getAmount() == 1 && event.getCounterpart().equals(ring), "event payload is an isolated snapshot");
        var removed = EquipmentChanges.between(null, null, Map.of(slot, ring), Map.of(), AccessoryChangeCause.QUIT);
        Checks.that(removed.size() == 1 && removed.getFirst() instanceof AccessoryUnequipEvent, "unload emits unequip");
        var death = new AccessoryDeathEvent(null, new ItemStack[]{ring}, AccessoryDeathPolicy.KEEP);
        death.setPolicy(AccessoryDeathPolicy.DROP); death.getContents()[0].setAmount(20);
        Checks.that(death.getPolicy() == AccessoryDeathPolicy.DROP && death.getContents()[0].getAmount() == 1, "death policy hook cannot mutate stored item snapshot");
        var state = new AccessorySlotStateChangeEvent(3, true);state.setCancelled(true);
        Checks.that(state.isCancelled() && state.getSlot() == 3 && state.isEnabled(), "slot change veto contract");
        Checks.that(AccessoryEquipEvent.getHandlerList() != AccessoryUnequipEvent.getHandlerList(), "event registrations are separate");
    }
}

package com.blanoir.accessory.api;

import java.util.*;

/** Immutable effective rules. Empty pages means all pages; slot numbers start at zero. */
public record AccessoryProfile(String id, Set<AccessoryAction> allowedActions, Set<Integer> pages,
                               Map<Integer, Set<Integer>> disabledSlots, InventoryDefinition inventory) {
    public AccessoryProfile {
        allowedActions = Set.copyOf(allowedActions);
        pages = Set.copyOf(pages);
        Map<Integer, Set<Integer>> copy = new LinkedHashMap<>();
        disabledSlots.forEach((page, slots) -> copy.put(page, Set.copyOf(slots)));
        disabledSlots = Map.copyOf(copy);
    }
    public record InventoryDefinition(String title, List<String> layouts) {
        public InventoryDefinition { layouts = List.copyOf(layouts); }
    }
    public boolean allows(AccessoryAction action) { return allowedActions.contains(action); }
    public boolean allowsPage(int page) { return pages.isEmpty() || pages.contains(page); }
    public boolean allowsSlot(int page, int slot) {
        return allowsPage(page) && !disabledSlots.getOrDefault(page, Set.of()).contains(slot);
    }
}

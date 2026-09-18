package com.blanoir.accessory.module.inventory;

import org.bukkit.inventory.ItemStack;
import java.util.List;

/** Stable page/slot addresses: changing a profile never shifts the next page's items. */
public final class InventorySlotLayout {
    public static final int PAGE_CAPACITY = 54;
    private InventorySlotLayout() { }
    public static ItemStack[] migrateLegacy(ItemStack[] old, List<Integer> sizes, int minimum) {
        if (sizes.stream().anyMatch(size -> size < 1 || size > PAGE_CAPACITY)
                || sizes.stream().mapToInt(Integer::intValue).sum() < old.length)
            throw new IllegalArgumentException("Legacy inventory shape is unknown; configure storage.legacy-page-sizes before loading");
        ItemStack[] result = new ItemStack[Math.max(minimum, sizes.size() * PAGE_CAPACITY)];
        int start = 0;
        for (int page = 0; page < sizes.size(); page++) {
            for (int slot = 0; slot < sizes.get(page) && start + slot < old.length; slot++)
                result[page * PAGE_CAPACITY + slot] = old[start + slot] == null ? null : old[start + slot].clone();
            start += sizes.get(page);
        }
        return result;
    }
}

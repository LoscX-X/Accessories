package com.blanoir.accessory.module.inventory.ui;

import com.blanoir.accessory.api.AccessoryViewMode;
import com.blanoir.accessory.module.inventory.AccessoryPageManager;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.NotNull;
import java.util.*;

/** One window's immutable owner, layout and edit mode. */
public final class AccessoryInventoryHolder implements InventoryHolder {
    private final UUID ownerId;
    private final int currentPage, totalPages;
    private final AccessoryViewMode mode;
    private final AccessoryPageManager pages;
    private Inventory inventory;
    public AccessoryInventoryHolder(UUID owner, int page, int count, AccessoryViewMode mode, AccessoryPageManager pages) {
        ownerId = Objects.requireNonNull(owner); currentPage = Math.clamp(page, 1, Math.max(1, count));
        totalPages = Math.max(1, count); this.mode = Objects.requireNonNull(mode); this.pages = pages;
    }
    public UUID getOwnerId() { return ownerId; }
    public int currentPage() { return currentPage; }
    public int totalPages() { return totalPages; }
    public AccessoryViewMode mode() { return mode; }
    public AccessoryPageManager pages() { return pages; }
    public void bindInventory(Inventory value) { inventory = value; }
    @Override public @NotNull Inventory getInventory() { return Objects.requireNonNull(inventory); }
}

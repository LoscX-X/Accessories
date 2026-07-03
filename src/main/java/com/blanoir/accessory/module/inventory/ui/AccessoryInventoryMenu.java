package com.blanoir.accessory.module.inventory.ui;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.module.inventory.AccessoryPageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AccessoryInventoryMenu {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Accessory plugin;
    private final AccessoryInventoryItem itemFactory;

    public AccessoryInventoryMenu(Accessory plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.itemFactory = new AccessoryInventoryItem(plugin);
    }

    public Inventory create(UUID ownerId,
                            int page,
                            int totalPages,
                            ItemStack[] contents,
                            Collection<Integer> disabledSlots) {
        AccessoryInventoryHolder holder = new AccessoryInventoryHolder(ownerId, page, totalPages);

        int size = plugin.accessorySize(holder.currentPage());
        Component title = title(holder.currentPage(), holder.totalPages());

        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.bindInventory(inventory);

        inventory.setContents(normalizeContents(contents, size));
        decorate(inventory, holder, disabledSlots);

        return inventory;
    }

    public Inventory createEmpty(UUID ownerId,
                                 int page,
                                 int totalPages,
                                 Collection<Integer> disabledSlots) {
        int safeTotalPages = Math.max(1, totalPages);
        int safePage = Math.max(1, Math.min(safeTotalPages, page));

        return create(
                ownerId,
                safePage,
                safeTotalPages,
                new ItemStack[plugin.accessorySize(safePage)],
                disabledSlots
        );
    }

    public void decorate(Inventory inventory,
                         AccessoryInventoryHolder holder,
                         Collection<Integer> disabledSlots) {
        applyFrames(inventory, holder);
        applyDisabledSlots(inventory, holder, disabledSlots);
    }

    private void applyFrames(Inventory inventory, AccessoryInventoryHolder holder) {
        List<AccessoryPageManager.FrameItem> frameItems =
                plugin.pageManager().frameItems(holder.currentPage(), inventory.getSize());

        for (AccessoryPageManager.FrameItem frameItem : frameItems) {
            ItemStack frame = itemFactory.frameItem(
                    frameItem.section(),
                    holder.currentPage(),
                    holder.totalPages()
            );

            for (int slot : frameItem.slots()) {
                if (isValidSlot(inventory, slot)) {
                    inventory.setItem(slot, frame.clone());
                }
            }
        }
    }

    private void applyDisabledSlots(Inventory inventory,
                                    AccessoryInventoryHolder holder,
                                    Collection<Integer> disabledSlots) {
        if (disabledSlots == null || disabledSlots.isEmpty()) {
            return;
        }

        ItemStack disabled = itemFactory.disabledItem(
                plugin.pageManager().disabledSlotItemSection(holder.currentPage()),
                holder.currentPage(),
                holder.totalPages()
        );

        for (Integer slot : disabledSlots) {
            if (slot != null && isValidSlot(inventory, slot)) {
                inventory.setItem(slot, disabled.clone());
            }
        }
    }

    private Component title(int currentPage, int totalPages) {
        String raw = plugin.getConfig().getString("title", "<green>Accessory");

        return MINI_MESSAGE.deserialize(
                raw.replace("{page}", String.valueOf(currentPage))
                        .replace("{max_page}", String.valueOf(totalPages))
        );
    }

    private ItemStack[] normalizeContents(ItemStack[] contents, int size) {
        ItemStack[] out = new ItemStack[size];

        if (contents == null || contents.length == 0) {
            return out;
        }

        int limit = Math.min(size, contents.length);
        for (int i = 0; i < limit; i++) {
            out[i] = contents[i] == null ? null : contents[i].clone();
        }

        return out;
    }

    private boolean isValidSlot(Inventory inventory, int slot) {
        return slot >= 0 && slot < inventory.getSize();
    }
}
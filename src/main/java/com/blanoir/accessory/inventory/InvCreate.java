package com.blanoir.accessory.inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InvCreate implements InventoryHolder {
    private final JavaPlugin plugin;
    private final Inventory inventory;
    private final UUID ownerId;
    private int currentPage;
    private final int totalPages;

    public InvCreate(JavaPlugin plugin, UUID ownerId, int page, int totalPages) {
        this.plugin = plugin;
        this.ownerId = ownerId;
        this.totalPages = Math.max(1, totalPages);
        this.currentPage = Math.max(1, Math.min(this.totalPages, page));

        FileConfiguration cfg = plugin.getConfig();

        int size = pageSize();

        String mmTitle = cfg.getString("title", "<green>Accessory");
        Component title = MiniMessage.miniMessage().deserialize(mmTitle.replace("{page}", String.valueOf(this.currentPage)).replace("{max_page}", String.valueOf(this.totalPages)));

        this.inventory = Bukkit.createInventory(this, size, title);
    }


    public int currentPage() {
        return currentPage;
    }

    public int totalPages() {
        return totalPages;
    }

    public void setCurrentPage(int page) {
        this.currentPage = Math.max(1, Math.min(totalPages, page));
    }

    public UUID ownerId() {
        return ownerId;
    }

    private int pageSize() {
        if (plugin instanceof com.blanoir.accessory.Accessory accessory && accessory.pageManager() != null) {
            return accessory.accessorySize(currentPage);
        }
        FileConfiguration cfg = plugin.getConfig();
        int size = cfg.getInt("size", 9);
        size = Math.max(9, Math.min(54, size));
        return size - (size % 9);
    }

    @NotNull
    @Override
    public Inventory getInventory() { return this.inventory; }

    public void decorate(List<Integer> disabledSlots) {
        applyFrames();
        applyDisabledSlots(disabledSlots);
        applyPageButtons();
    }

    public boolean hasPreviousPage() {
        return currentPage > 1;
    }

    public boolean hasNextPage() {
        return currentPage < totalPages;
    }

    public int previousPageSlot() {
        return plugin.getConfig().getInt("pre_page.slot", 0);
    }

    public int nextPageSlot() {
        return plugin.getConfig().getInt("next_page.slot", Math.max(0, inventory.getSize() - 1));
    }

    public void applyFrames() {
        if (plugin instanceof com.blanoir.accessory.Accessory accessory && accessory.pageManager() != null) {
            for (AccessoryPageManager.FrameItem frameItem : accessory.pageManager().frameItems(currentPage, inventory.getSize())) {
                ItemStack pane = makeMarkedItem(frameItem.section(), "locked");
                for (int slot : frameItem.slots()) {
                    inventory.setItem(slot, pane.clone());
                }
            }
            return;
        }

        ItemStack pane = makeMarkedItemFromConfig("frame.item", "locked");
        int invSize = inventory.getSize();
        for (int s : List.of(0, 2, 4, 6, 8)) {
            if (s >= 0 && s < invSize) {
                inventory.setItem(s, pane.clone());
            }
        }
    }

    public void applyDisabledSlots(List<Integer> slots) {
        if (slots == null || slots.isEmpty()) return;

        ItemStack pane = makeMarkedItemFromConfig("disabled-slot.item", "disabled", "locked");
        int invSize = inventory.getSize();
        for (int s : slots) {
            if (s >= 0 && s < invSize) {
                inventory.setItem(s, pane.clone());
            }
        }
    }

    public void applyPageButtons() {
        int size = inventory.getSize();
        if (hasPreviousPage()) {
            int slot = previousPageSlot();
            if (slot >= 0 && slot < size) {
                inventory.setItem(slot, makeMarkedItemFromConfig("pre_page.item", "pre_page"));
            }
        }
        if (hasNextPage()) {
            int slot = nextPageSlot();
            if (slot >= 0 && slot < size) {
                inventory.setItem(slot, makeMarkedItemFromConfig("next_page.item", "next_page"));
            }
        }
    }

    private ItemStack makeMarkedItemFromConfig(String basePath, String... markers) {
        return makeMarkedItem(plugin.getConfig().getConfigurationSection(basePath), markers);
    }

    private ItemStack makeMarkedItem(ConfigurationSection section, String... markers) {
        String typeStr = section == null ? "BLACK_STAINED_GLASS_PANE" : section.getString("type", "BLACK_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(typeStr, false);
        if (mat == null || !mat.isItem()) {
            mat = Material.BLACK_STAINED_GLASS_PANE;
        }

        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();

        boolean hide = section == null || section.getBoolean("hide-tooltip", true);
        try { meta.setHideTooltip(hide); } catch (NoSuchMethodError ignored) {}

        int cmd = section == null ? -1 : section.getInt("custom-model-data", -1);
        if (cmd > 0) {
            try { meta.setCustomModelData(cmd); } catch (Throwable ignored) {}
        }

        String mmName = section == null ? null : section.getString("name", null);
        if (mmName != null && !mmName.isEmpty()) {
            meta.displayName(MiniMessage.miniMessage().deserialize(mmName
                    .replace("{page}", String.valueOf(currentPage))
                    .replace("{max_page}", String.valueOf(totalPages))));
        }

        List<String> mmLore = section == null ? List.of() : section.getStringList("lore");
        if (!mmLore.isEmpty()) {
            List<Component> lore = new ArrayList<>(mmLore.size());
            for (String line : mmLore) {
                lore.add(MiniMessage.miniMessage().deserialize(line
                        .replace("{page}", String.valueOf(currentPage))
                        .replace("{max_page}", String.valueOf(totalPages))));
            }
            try { meta.lore(lore); } catch (NoSuchMethodError ignored) {}
        }

        for (String marker : markers) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, marker),
                    PersistentDataType.BYTE, (byte) 1
            );
        }

        it.setItemMeta(meta);
        return it;
    }
}

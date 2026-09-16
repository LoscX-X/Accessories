package com.blanoir.accessory.module.inventory;

import com.blanoir.accessory.config.AccessoryLayout;
import com.blanoir.accessory.config.AccessoryLayout.FrameItem;
import com.blanoir.accessory.config.AccessoryLayout.SlotRule;
import com.blanoir.accessory.config.AccessorySettings;
import com.blanoir.accessory.config.ConfigFiles;
import com.blanoir.accessory.config.PageSettings;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Named layout registry and resolved page table; menu actions belong to the menu controller. */
public final class AccessoryPageManager {
    private final File directory;
    private final Logger logger;
    private Map<String, AccessoryLayout> layouts = Map.of();
    private List<AccessoryLayout> pages = List.of();
    private int[] starts = new int[0];

    public AccessoryPageManager(File dataFolder, Logger logger) {
        directory = new File(dataFolder, "layouts");
        this.logger = logger;
    }

    public void reload(PageSettings settings, AccessorySettings.Gui defaults) {
        Map<String, AccessoryLayout> loaded = new LinkedHashMap<>();
        for (File file : ConfigFiles.yamlFiles(directory)) {
            var config = ConfigFiles.load(file, logger);
            if (config == null) continue;
            String name = file.getName();
            String id = name.substring(0, name.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (loaded.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate layout id '" + id + "': " + file.getName());
            }
            loaded.put(id, AccessoryLayout.read(id, config, defaults, logger));
        }

        // Resolve before publishing so a missing layout never replaces a working page table.
        AccessoryLayout fallback = loaded.get(settings.defaultLayout());
        if (fallback == null) {
            throw new IllegalArgumentException("Default layout '" + settings.defaultLayout() + "' not found in layouts/");
        }
        var resolved = new java.util.ArrayList<AccessoryLayout>();
        int[] offsets = new int[settings.count() + 1];
        for (int page = 1; page <= settings.count(); page++) {
            String id = settings.layoutFor(page);
            AccessoryLayout layout = loaded.get(id);
            if (layout == null) {
                logger.warning("Page " + page + ": layout '" + id + "' not found; using '" + fallback.id() + "'.");
                layout = fallback;
            }
            resolved.add(layout);
            offsets[page] = offsets[page - 1] + layout.size();
        }
        layouts = Map.copyOf(loaded);
        pages = List.copyOf(resolved);
        starts = offsets;
    }

    public int pageCount() { return pages.size(); }
    public int normalizePage(int page) { return Math.clamp(page, 1, pageCount()); }
    public AccessoryLayout layout(int page) { return pages.get(normalizePage(page) - 1); }
    public Map<String, AccessoryLayout> layouts() { return layouts; }
    public int pageSize(int page) { return layout(page).size(); }
    public String pageTitle(int page) { return layout(page).title(); }
    public int maxPageSize() { return pages.stream().mapToInt(AccessoryLayout::size).max().orElse(9); }
    public int pageStart(int page) { return starts[normalizePage(page) - 1]; }
    public int totalStorageSize() { return starts[pageCount()]; }

    public int pageByAbsoluteSlot(int absoluteSlot) {
        if (absoluteSlot < 0 || absoluteSlot >= totalStorageSize()) return -1;
        for (int page = 1; page <= pageCount(); page++) {
            if (absoluteSlot < starts[page]) return page;
        }
        return -1;
    }

    public int localSlot(int absoluteSlot) {
        int page = pageByAbsoluteSlot(absoluteSlot);
        return page == -1 ? -1 : absoluteSlot - pageStart(page);
    }

    public List<Integer> configuredSlots(int page) { return List.copyOf(layout(page).slots().keySet()); }
    public boolean isSlotConfigured(int page, int slot) { return slotRule(page, slot) != null; }

    public List<String> requiredLore(int page, int slot) {
        SlotRule rule = slotRule(page, slot);
        return rule == null ? List.of() : rule.lore();
    }

    public String requiredPermission(int page, int slot) {
        SlotRule rule = slotRule(page, slot);
        return rule == null ? null : rule.permission();
    }

    public List<Integer> frameSlots(int page, int size) {
        return frameItems(page, size).stream().flatMap(item -> item.slots().stream()).filter(slot -> slot < size).distinct().toList();
    }

    public List<FrameItem> frameItems(int page, int size) { return layout(page).frames(); }

    public FrameItem frameItemAt(int page, int slot, int size) {
        if (slot < 0 || slot >= size) return null;
        return frameItems(page, size).stream().filter(item -> item.slots().contains(slot)).findFirst().orElse(null);
    }

    public ConfigurationSection disabledSlotItemSection(int page) { return layout(page).disabledItem(); }

    private SlotRule slotRule(int page, int slot) {
        return page < 1 || page > pageCount() ? null : layout(page).slots().get(slot);
    }
}

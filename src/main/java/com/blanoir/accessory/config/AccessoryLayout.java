package com.blanoir.accessory.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** A reusable layout definition. It contains no player inventory or current-page state. */
public record AccessoryLayout(String id, String title, int size, Map<Integer, SlotRule> slots,
                              List<FrameItem> frames, ConfigurationSection disabledItem) {
    public AccessoryLayout {
        slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        frames = List.copyOf(frames);
    }

    public static AccessoryLayout read(String id, ConfigurationSection config,
                                       AccessorySettings.Gui defaults, Logger logger) {
        if (config.contains("Accessory", true)) throw new IllegalArgumentException("Layout " + id + ": replace the old Accessory section with slots");
        int requestedSize = config.getInt("size", defaults.defaultSize());
        int size = AccessorySettings.normalizeSize(requestedSize);
        if (size != requestedSize) logger.warning("Layout " + id + ": size " + requestedSize + " normalized to " + size);

        Map<Integer, SlotRule> slots = new LinkedHashMap<>();
        ConfigurationSection accessories = config.getConfigurationSection("slots");
        if (accessories != null) {
            for (String key : accessories.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ConfigurationSection rule = accessories.getConfigurationSection(key);
                    if (rule == null || !validSlot(slot, size, id, "slots." + key, logger)) continue;
                    String permission = rule.getString("permission", "").trim();
                    slots.put(slot, new SlotRule(List.copyOf(rule.getStringList("lore")), permission.isEmpty() ? null : permission));
                } catch (NumberFormatException ex) {
                    logger.warning("Layout " + id + ": invalid slots entry " + key + "; skipped.");
                }
            }
        }

        List<FrameItem> frames = new ArrayList<>();
        ConfigurationSection frame = config.getConfigurationSection("frame");
        if (frame != null) {
            for (String key : frame.getKeys(false)) {
                ConfigurationSection item = frame.getConfigurationSection(key);
                if (item == null || !item.isList("slots")) continue;
                LinkedHashSet<Integer> frameSlots = new LinkedHashSet<>();
                for (int slot : item.getIntegerList("slots")) {
                    if (validSlot(slot, size, id, "frame." + key, logger)) frameSlots.add(slot);
                }
                String actionName = item.getString("action", "none").trim().toUpperCase(Locale.ROOT);
                Action action;
                try {
                    action = Action.valueOf(actionName);
                } catch (IllegalArgumentException ex) {
                    logger.warning("Layout " + id + ": unknown frame action " + actionName + " on " + key);
                    action = Action.NONE;
                }
                frames.add(new FrameItem(key, item, List.copyOf(frameSlots), action,
                        commands(item, "command.console"), commands(item, "command.player")));
            }
        }
        return new AccessoryLayout(id, config.getString("title", defaults.title()), size, slots, frames,
                config.getConfigurationSection("disabled-slot.item"));
    }

    private static boolean validSlot(int slot, int size, String id, String path, Logger logger) {
        if (slot >= 0 && slot < size) return true;
        logger.warning("Layout " + id + ": " + path + " slot " + slot + " outside 0.." + (size - 1) + "; skipped.");
        return false;
    }

    private static List<String> commands(ConfigurationSection section, String path) {
        if (section.isList(path)) return List.copyOf(section.getStringList(path));
        return section.isString(path) ? List.of(section.getString(path, "")) : List.of();
    }

    public enum Action { NONE, PREVIOUS_PAGE, NEXT_PAGE, COMMAND }
    public record SlotRule(List<String> lore, String permission) { }
    public record FrameItem(String key, ConfigurationSection section, List<Integer> slots, Action action,
                            List<String> consoleCommands, List<String> playerCommands) { }
}

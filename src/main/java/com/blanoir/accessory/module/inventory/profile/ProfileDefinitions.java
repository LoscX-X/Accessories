package com.blanoir.accessory.module.inventory.profile;

import com.blanoir.accessory.api.*;
import com.blanoir.accessory.config.ConfigFiles;
import org.bukkit.configuration.ConfigurationSection;
import java.io.File;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;

/** Pure selection and parsing. Exactly one matching profile wins, with default rules inherited. */
public final class ProfileDefinitions {
    public record Entry(AccessoryProfile rules, int priority, Set<String> worlds, Set<String> permissions) {
        boolean matches(String world, Predicate<String> permission) {
            return (worlds.isEmpty() || worlds.contains(world)) && permissions.stream().allMatch(permission);
        }
    }
    private final Map<String, Entry> entries;
    private final List<Entry> ordered;
    public ProfileDefinitions(Map<String, Entry> entries) {
        if (!entries.containsKey("default")) throw new IllegalArgumentException("profiles/default.yml is required");
        this.entries = Map.copyOf(entries);
        this.ordered = entries.values().stream().filter(e -> !e.rules().id().equals("default"))
                .sorted(Comparator.comparingInt(Entry::priority).reversed().thenComparing(e -> e.rules().id())).toList();
    }
    public AccessoryProfile select(String world, Predicate<String> permission, String override) {
        if (override != null && entries.containsKey(override)) return entries.get(override).rules();
        return ordered.stream().filter(e -> e.matches(world, permission)).findFirst()
                .orElse(entries.get("default")).rules();
    }
    public Collection<AccessoryProfile> profiles() { return entries.values().stream().map(Entry::rules).toList(); }
    public boolean contains(String id) { return entries.containsKey(id); }
    public static ProfileDefinitions load(File directory, Logger logger, int pageCount, java.util.function.IntUnaryOperator size) {
        Map<String, ConfigurationSection> configs = new LinkedHashMap<>();
        for (File file : ConfigFiles.yamlFiles(directory)) {
            var config = ConfigFiles.load(file, logger);
            if (config == null) throw new IllegalArgumentException("Invalid profile: " + file);
            String id = file.getName().replaceFirst("(?i)\\.ya?ml$", "");
            if (configs.putIfAbsent(id, config) != null) throw new IllegalArgumentException("Duplicate profile: " + id);
        }
        if (!configs.containsKey("default")) throw new IllegalArgumentException("profiles/default.yml is required");
        var base = parse("default", configs.get("default"), null, pageCount, size);
        Map<String, Entry> entries = new LinkedHashMap<>();
        entries.put("default", base);
        configs.forEach((id, config) -> {
            if (!id.equals("default") && config.getBoolean("enabled", true))
                entries.put(id, parse(id, config, base.rules(), pageCount, size));
        });
        return new ProfileDefinitions(entries);
    }
    public static Entry parse(String id, ConfigurationSection config, AccessoryProfile defaults,
                              int pageCount, java.util.function.IntUnaryOperator size) {
        var inheritedInventory = defaults == null ? new AccessoryProfile.InventoryDefinition("", List.of()) : defaults.inventory();
        var inventory = new AccessoryProfile.InventoryDefinition(config.getString("inventory.title", inheritedInventory.title()),
                config.contains("inventory.pages") ? config.getStringList("inventory.pages") : inheritedInventory.layouts());
        if (config.contains("inventory.pages") && inventory.layouts().isEmpty()) throw new IllegalArgumentException(id + ": inventory.pages must contain layout names");
        if (inventory.layouts().size() > 100) throw new IllegalArgumentException(id + ": at most 100 pages");
        if (id.equals("default") && inventory.layouts().isEmpty()) throw new IllegalArgumentException("profiles/default.yml must specify inventory.pages");
        int visiblePages = inventory.layouts().isEmpty() ? pageCount : inventory.layouts().size();
        Set<AccessoryAction> actions = defaults == null ? EnumSet.allOf(AccessoryAction.class)
                : new HashSet<>(defaults.allowedActions());
        for (AccessoryAction action : AccessoryAction.values()) {
            String key = "rules." + action.name().toLowerCase(Locale.ROOT);
            if (config.contains(key)) {
                if (!config.isBoolean(key)) throw new IllegalArgumentException(id + ": " + key + " must be true/false");
                if (config.getBoolean(key)) actions.add(action); else actions.remove(action);
            }
        }
        Set<Integer> pages = config.contains("rules.pages") ? new LinkedHashSet<>(config.getIntegerList("rules.pages"))
                : defaults == null ? Set.of() : defaults.pages();
        for (int page : pages) if (page < 1 || page > visiblePages) throw new IllegalArgumentException(id + ": invalid page " + page);
        Map<Integer, Set<Integer>> slots = new LinkedHashMap<>();
        if (defaults != null) slots.putAll(defaults.disabledSlots());
        var disabled = config.getConfigurationSection("rules.disabled-slots");
        if (disabled != null) for (String key : disabled.getKeys(false)) {
            int page;
            try { page = Integer.parseInt(key); } catch (NumberFormatException ex) { throw new IllegalArgumentException(id + ": invalid page " + key); }
            if (page < 1 || page > visiblePages) throw new IllegalArgumentException(id + ": invalid page " + page);
            Set<Integer> values = new LinkedHashSet<>(disabled.getIntegerList(key));
            for (int slot : values) if (slot < 0 || slot >= (inventory.layouts().isEmpty() ? size.applyAsInt(page) : 54)) throw new IllegalArgumentException(id + ": invalid slot " + page + ":" + slot);
            slots.put(page, values); // A profile replaces the entire list for each specified page.
        }
        return new Entry(new AccessoryProfile(id, actions, pages, slots, inventory), config.getInt("priority", 0),
                Set.copyOf(config.getStringList("match.worlds")), Set.copyOf(config.getStringList("match.permissions")));
    }
}

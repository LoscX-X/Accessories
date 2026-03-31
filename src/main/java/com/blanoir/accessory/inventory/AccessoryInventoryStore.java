package com.blanoir.accessory.inventory;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class AccessoryInventoryStore {
    private static final String CONTAINS_DIR = "contains";
    private static final String CONTENTS_KEY = "contents";

    private final JavaPlugin plugin;
    private final Map<UUID, ItemStack[]> cache = new ConcurrentHashMap<>();

    public AccessoryInventoryStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void preload(UUID playerId, int size) {
        cache.computeIfAbsent(playerId, id -> loadFromDisk(id, size));
    }

    public ItemStack[] getOrLoad(UUID playerId, int size) {
        ItemStack[] contents = cache.computeIfAbsent(playerId, id -> loadFromDisk(id, size));
        return copyToSize(contents, size);
    }

    public void update(UUID playerId, ItemStack[] contents, int size) {
        cache.put(playerId, copyToSize(contents, size));
    }

    public void clear(UUID playerId, int size) {
        cache.put(playerId, new ItemStack[size]);
    }

    public void saveAndRemove(UUID playerId, int size) {
        ItemStack[] contents = getOrLoad(playerId, size);
        saveToDisk(playerId, contents);
        cache.remove(playerId);
    }

    public void flush(UUID playerId, int size) {
        saveToDisk(playerId, getOrLoad(playerId, size));
    }

    private ItemStack[] loadFromDisk(UUID playerId, int size) {
        File file = fileOf(playerId);
        if (!file.exists()) {
            return new ItemStack[size];
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ItemStack[] out = new ItemStack[size];
        var raw = cfg.getList(CONTENTS_KEY);
        if (raw == null || raw.isEmpty()) {
            return out;
        }

        int limit = Math.min(size, raw.size());
        for (int i = 0; i < limit; i++) {
            Object it = raw.get(i);
            if (it instanceof ItemStack stack) {
                out[i] = stack;
            }
        }
        return out;
    }

    private void saveToDisk(UUID playerId, ItemStack[] contents) {
        File dir = new File(plugin.getDataFolder(), CONTAINS_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Failed to create contains directory: " + dir.getAbsolutePath());
            return;
        }

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set(CONTENTS_KEY, Arrays.asList(contents));
        try {
            cfg.save(fileOf(playerId));
        } catch (IOException ex) {
            plugin.getLogger().severe("Save failed: " + playerId);
            ex.printStackTrace();
        }
    }

    private File fileOf(UUID playerId) {
        return new File(plugin.getDataFolder(), CONTAINS_DIR + "/" + playerId + ".yml");
    }

    private ItemStack[] copyToSize(ItemStack[] source, int size) {
        ItemStack[] out = new ItemStack[size];
        if (source == null || source.length == 0) {
            return out;
        }
        System.arraycopy(source, 0, out, 0, Math.min(size, source.length));
        return out;
    }
}

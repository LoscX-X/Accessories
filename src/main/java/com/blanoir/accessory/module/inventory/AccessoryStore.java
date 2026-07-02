package com.blanoir.accessory.module.inventory;

import com.blanoir.accessory.database.mysql.SqlManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class AccessoryStore {
    private static final String CONTAINS_DIR = "contains";
    private static final String CONTENTS_KEY = "contents";

    public enum StorageType {
        MYSQL,
        YML;

        public static StorageType fromConfig(String raw) {
            if (raw == null) {
                return YML;
            }
            if ("mysql".equalsIgnoreCase(raw)) {
                return MYSQL;
            }
            return YML;
        }
    }

    private final JavaPlugin plugin;
    private final StorageType storageType;
    private final SqlManager sqlManager;
    private final Map<UUID, ItemStack[]> cache = new ConcurrentHashMap<>();
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "accessory-io");
        thread.setDaemon(true);
        return thread;
    });

    public AccessoryStore(JavaPlugin plugin, StorageType storageType, SqlManager sqlManager) {
        this.plugin = plugin;
        this.storageType = storageType;
        this.sqlManager = sqlManager;
    }

    public void preload(UUID playerId, int totalSize) {
        CompletableFuture.runAsync(() -> cache.computeIfAbsent(playerId, id -> load(id, totalSize)), ioExecutor);
    }


    public void getSliceOrLoadAsync(UUID playerId, int start, int size, int totalSize, Consumer<ItemStack[]> callback) {
        CompletableFuture
                .supplyAsync(() -> getOrLoadInternal(playerId, totalSize), ioExecutor)
                .thenApply(full -> extractSlice(full, start, size))
                .thenAccept(pageContents -> Bukkit.getScheduler().runTask(plugin, () -> callback.accept(pageContents)));
    }

    public ItemStack[] getOrLoad(UUID playerId, int totalSize) {
        ItemStack[] contents = cache.computeIfAbsent(playerId, id -> load(id, totalSize));
        return copyToSize(contents, totalSize);
    }


    public ItemStack[] getSliceOrLoad(UUID playerId, int start, int size, int totalSize) {
        ItemStack[] full = getOrLoad(playerId, totalSize);
        return extractSlice(full, start, size);
    }

    public ItemStack[] getPageOrLoad(UUID playerId, int page, int pageSize, int totalPages) {
        ItemStack[] full = getOrLoad(playerId, totalSize(pageSize, totalPages));
        return extractPage(full, page, pageSize);
    }

    public void update(UUID playerId, ItemStack[] contents, int totalSize) {
        cache.put(playerId, copyToSize(contents, totalSize));
    }


    public void updateSlice(UUID playerId, int start, ItemStack[] pageContents, int pageSize, int totalSize) {
        ItemStack[] full = getOrLoad(playerId, totalSize);
        int safeStart = Math.max(0, Math.min(start, full.length));
        int safeSize = Math.max(0, Math.min(pageSize, full.length - safeStart));
        for (int i = 0; i < safeSize; i++) {
            full[safeStart + i] = i < pageContents.length ? pageContents[i] : null;
        }
        cache.put(playerId, full);
    }

    public void updatePage(UUID playerId, int page, ItemStack[] pageContents, int pageSize, int totalPages) {
        int totalSize = totalSize(pageSize, totalPages);
        ItemStack[] full = getOrLoad(playerId, totalSize);
        int pageIndex = normalizedPage(page, totalPages) - 1;
        int start = pageIndex * pageSize;
        for (int i = 0; i < pageSize; i++) {
            full[start + i] = i < pageContents.length ? pageContents[i] : null;
        }
        cache.put(playerId, full);
    }

    public void clear(UUID playerId, int totalSize) {
        cache.put(playerId, new ItemStack[totalSize]);
    }

    public void saveAndRemove(UUID playerId, int totalSize) {
        ItemStack[] removed = cache.remove(playerId);
        ItemStack[] snapshot = copyToSize(removed, totalSize);

        CompletableFuture.runAsync(() -> save(playerId, snapshot), ioExecutor)
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to save inventory for " + playerId + ": " + ex.getMessage());
                    return null;
                });
    }

    public void flush(UUID playerId, int totalSize) {
        ItemStack[] snapshot = getOrLoad(playerId, totalSize);
        CompletableFuture.runAsync(() -> save(playerId, snapshot), ioExecutor);
    }

    public CompletableFuture<Void> flushAllAsync(int totalSize) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Map<UUID, ItemStack[]> snapshotByPlayer = new HashMap<>(cache);
        for (Map.Entry<UUID, ItemStack[]> entry : snapshotByPlayer.entrySet()) {
            UUID playerId = entry.getKey();
            ItemStack[] snapshot = copyToSize(entry.getValue(), totalSize);
            futures.add(CompletableFuture.runAsync(() -> save(playerId, snapshot), ioExecutor));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public void shutdown() {
        ioExecutor.shutdown();
    }


    private ItemStack[] extractSlice(ItemStack[] full, int start, int size) {
        ItemStack[] out = new ItemStack[Math.max(0, size)];
        int safeStart = Math.max(0, Math.min(start, full.length));
        int copyLength = Math.min(out.length, full.length - safeStart);
        if (copyLength > 0) {
            System.arraycopy(full, safeStart, out, 0, copyLength);
        }
        return out;
    }

    private ItemStack[] extractPage(ItemStack[] full, int page, int pageSize) {
        int safePageSize = Math.max(1, pageSize);
        int maxPages = Math.max(1, (full.length + safePageSize - 1) / safePageSize);
        int pageIndex = normalizedPage(page, maxPages) - 1;
        ItemStack[] out = new ItemStack[safePageSize];
        int start = pageIndex * safePageSize;
        System.arraycopy(full, start, out, 0, Math.min(safePageSize, Math.max(0, full.length - start)));
        return out;
    }

    private int totalSize(int pageSize, int totalPages) {
        return Math.max(pageSize, pageSize * Math.max(1, totalPages));
    }

    private int normalizedPage(int page, int totalPages) {
        int max = Math.max(1, totalPages);
        return Math.max(1, Math.min(max, page));
    }

    private ItemStack[] getOrLoadInternal(UUID playerId, int totalSize) {
        ItemStack[] contents = cache.computeIfAbsent(playerId, id -> load(id, totalSize));
        return copyToSize(contents, totalSize);
    }

    private ItemStack[] load(UUID playerId, int size) {
        return storageType == StorageType.MYSQL ? loadFromMysql(playerId, size) : loadFromDisk(playerId, size);
    }

    private void save(UUID playerId, ItemStack[] contents) {
        if (storageType == StorageType.MYSQL) {
            saveToMysql(playerId, contents);
            return;
        }
        saveToDisk(playerId, contents);
    }

    private ItemStack[] loadFromMysql(UUID playerId, int size) {
        if (sqlManager == null) {
            plugin.getLogger().warning("MySQL storage selected but SqlManager is unavailable, fallback to empty data.");
            return new ItemStack[size];
        }
        try {
            String raw = sqlManager.loadInventory(playerId);
            if (raw == null || raw.isEmpty()) {
                return new ItemStack[size];
            }
            return decode(raw, size);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load inventory from MySQL for " + playerId + ": " + ex.getMessage());
            return new ItemStack[size];
        }
    }

    private void saveToMysql(UUID playerId, ItemStack[] contents) {
        if (sqlManager == null) {
            plugin.getLogger().warning("MySQL storage selected but SqlManager is unavailable, skip save.");
            return;
        }
        try {
            sqlManager.saveInventory(playerId, encode(contents));
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to save inventory to MySQL for " + playerId + ": " + ex.getMessage());
        }
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

    private String encode(ItemStack[] contents) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set(CONTENTS_KEY, Arrays.asList(contents));
        return Base64.getEncoder().encodeToString(cfg.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private ItemStack[] decode(String encoded, int size) {
        String yaml = new String(Base64.getDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid serialized inventory", ex);
        }

        ItemStack[] out = new ItemStack[size];
        var raw = cfg.getList(CONTENTS_KEY);
        if (raw == null || raw.isEmpty()) {
            return out;
        }

        int limit = Math.min(size, raw.size());
        for (int i = 0; i < limit; i++) {
            Object value = raw.get(i);
            if (value instanceof ItemStack stack) {
                out[i] = stack;
            }
        }
        return out;
    }

    private File fileOf(UUID playerId) {
        return new File(plugin.getDataFolder(), CONTAINS_DIR + "/" + playerId + ".yml");
    }

    private ItemStack[] copyToSize(ItemStack[] source, int size) {
        ItemStack[] coped = new ItemStack[size];

        if (source == null || source.length == 0) {
            return coped;
        }

        int limit = Math.min(size, source.length);
        for (int i = 0; i < limit; i++) {
            coped[i] = source[i] == null ? null : source[i].clone();
        }

        return coped;
    }
}

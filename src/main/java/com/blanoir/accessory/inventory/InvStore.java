package com.blanoir.accessory.inventory;

import com.blanoir.accessory.database.mysql.SqlManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class InvStore {
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

    public InvStore(JavaPlugin plugin, StorageType storageType, SqlManager sqlManager) {
        this.plugin = plugin;
        this.storageType = storageType;
        this.sqlManager = sqlManager;
    }

    public void preload(UUID playerId, int totalSize) {
        CompletableFuture.runAsync(() -> cache.computeIfAbsent(playerId, id -> load(id, totalSize)), ioExecutor);
    }

    public void getPageOrLoadAsync(UUID playerId, int page, int pageSize, int totalPages, Consumer<ItemStack[]> callback) {
        CompletableFuture
                .supplyAsync(() -> getOrLoadInternal(playerId, totalSize(pageSize, totalPages)), ioExecutor)
                .thenApply(full -> extractPage(full, page, pageSize))
                .thenAccept(pageContents -> Bukkit.getScheduler().runTask(plugin, () -> callback.accept(pageContents)));
    }

    public ItemStack[] getOrLoad(UUID playerId, int totalSize) {
        ItemStack[] contents = cache.get(playerId);
        if (contents == null) {
            contents = new ItemStack[totalSize];
            cache.put(playerId, contents);
            preload(playerId, totalSize);
        }
        return copyToSize(contents, totalSize);
    }

    public ItemStack[] getPageOrLoad(UUID playerId, int page, int pageSize, int totalPages) {
        ItemStack[] full = getOrLoad(playerId, totalSize(pageSize, totalPages));
        return extractPage(full, page, pageSize);
    }

    public void update(UUID playerId, ItemStack[] contents, int totalSize) {
        cache.put(playerId, copyToSize(contents, totalSize));
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
        ItemStack[] contents = getOrLoad(playerId, totalSize);
        CompletableFuture.runAsync(() -> save(playerId, contents), ioExecutor)
                .thenRun(() -> cache.remove(playerId));
    }

    public void flush(UUID playerId, int totalSize) {
        ItemStack[] snapshot = getOrLoad(playerId, totalSize);
        CompletableFuture.runAsync(() -> save(playerId, snapshot), ioExecutor);
    }

    public CompletableFuture<Void> flushAllAsync(int totalSize) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (UUID playerId : cache.keySet()) {
            ItemStack[] snapshot = getOrLoad(playerId, totalSize);
            futures.add(CompletableFuture.runAsync(() -> save(playerId, snapshot), ioExecutor));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public void shutdown() {
        ioExecutor.shutdown();
    }

    private ItemStack[] extractPage(ItemStack[] full, int page, int pageSize) {
        int maxPages = Math.max(1, full.length / pageSize);
        int pageIndex = normalizedPage(page, maxPages) - 1;
        ItemStack[] out = new ItemStack[pageSize];
        int start = pageIndex * pageSize;
        System.arraycopy(full, start, out, 0, Math.min(pageSize, full.length - start));
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

    private String encode(ItemStack[] contents) throws IOException {
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(byteOut)) {
            out.writeInt(contents.length);
            for (ItemStack content : contents) {
                out.writeObject(content);
            }
            out.flush();
            return Base64.getEncoder().encodeToString(byteOut.toByteArray());
        }
    }

    private ItemStack[] decode(String encoded, int size) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(encoded);
        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(data);
             BukkitObjectInputStream in = new BukkitObjectInputStream(byteIn)) {
            int length = in.readInt();
            ItemStack[] contents = new ItemStack[Math.max(size, length)];
            for (int i = 0; i < length; i++) {
                Object value = in.readObject();
                if (value instanceof ItemStack stack) {
                    contents[i] = stack;
                }
            }
            return copyToSize(contents, size);
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

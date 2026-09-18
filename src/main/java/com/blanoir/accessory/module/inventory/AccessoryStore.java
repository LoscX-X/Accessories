package com.blanoir.accessory.module.inventory;

import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.database.mysql.SqlManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Serial persistence, session cache, failed-write retention, and defensive snapshots. */
public final class AccessoryStore {
    public enum StorageType {
        MYSQL, YML, ONLY_RAM;
        public static StorageType fromConfig(String raw) {
            if (raw == null) return YML;
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "mysql" -> MYSQL;
                case "yml" -> YML;
                case "only-ram" -> ONLY_RAM;
                default -> throw new IllegalArgumentException("Unknown storage.type: " + raw);
            };
        }
    }
    private record Write(ItemStack[] contents, boolean delete) { }
    private final JavaPlugin plugin;
    private final StorageType storageType;
    private final SqlManager sqlManager;
    private final Object lock = new Object();
    private final Map<UUID, ItemStack[]> cache = new HashMap<>();
    private final Map<UUID, Long> generations = new HashMap<>();
    private final Map<UUID, Write> pendingWrites = new HashMap<>();
    private final List<Integer> legacySizes;
    private final boolean stableSlots;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "accessory-io"); thread.setDaemon(true); return thread;
    });

    public AccessoryStore(JavaPlugin plugin, StorageType type, SqlManager sql) {
        this.plugin = plugin; storageType = type; sqlManager = sql;
        stableSlots = plugin instanceof Accessory;
        if (plugin instanceof Accessory accessory) {
            List<Integer> explicit = plugin.getConfig().getIntegerList("storage.legacy-page-sizes");
            legacySizes = explicit.isEmpty() ? java.util.stream.IntStream.rangeClosed(1, accessory.pageManager().pageCount())
                    .mapToObj(accessory.pageManager()::pageSize).toList() : List.copyOf(explicit);
        } else legacySizes = List.of();
        if (type == StorageType.MYSQL && sql == null) throw new IllegalArgumentException("MySQL selected without a connection manager");
    }

    public void preload(UUID player, int size) { readAsync(player, size).exceptionally(ex -> { log("load", player, ex); return null; }); }

    public CompletableFuture<ItemStack[]> readAsync(UUID player, int size) {
        final long generation;
        synchronized (lock) {
            var cached = cache.get(player);
            if (cached != null) return CompletableFuture.completedFuture(copy(cached, size));
            generation = generations.getOrDefault(player, 0L);
        }
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
                ItemStack[] current = cache.get(player);
                if (current != null) return copy(current, size);
            }
            ItemStack[] loaded;
            synchronized (lock) {
                Write pending = pendingWrites.get(player);
                loaded = pending == null ? null : copy(pending.contents(), size);
            }
            if (loaded == null) loaded = load(player, size); // A failed read never becomes an empty inventory.
            synchronized (lock) {
                if (generations.getOrDefault(player, 0L) == generation) {
                    cache.putIfAbsent(player, loaded);
                    return copy(cache.get(player), size);
                }
                return copy(loaded, size); // Session consumer also validates its request token.
            }
        }, ioExecutor);
    }

    public void getSliceOrLoadAsync(UUID player, int start, int size, int total, Consumer<ItemStack[]> callback) {
        readAsync(player, total).thenAccept(full -> {
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> callback.accept(slice(full, start, size)));
        }).exceptionally(ex -> { log("load", player, ex); return null; });
    }
    public ItemStack[] getOrLoad(UUID player, int size) { return readAsync(player, size).join(); }
    public ItemStack[] getSliceOrLoad(UUID player, int start, int size, int total) { return slice(getOrLoad(player, total), start, size); }

    public void update(UUID player, ItemStack[] contents, int size) {
        synchronized (lock) { cache.put(player, copy(contents, size)); }
    }
    public void updateSlice(UUID player, int start, ItemStack[] contents, int size, int total) {
        ItemStack[] full = getOrLoad(player, total);
        int safeStart = Math.clamp(start, 0, full.length);
        int length = Math.min(size, full.length - safeStart);
        for (int i = 0; i < length; i++) full[safeStart + i] = i < contents.length && contents[i] != null ? contents[i].clone() : null;
        update(player, full, total);
    }
    public void clear(UUID player, int size) {
        ItemStack[] empty = new ItemStack[size];
        synchronized (lock) { generations.merge(player, 1L, Long::sum); cache.put(player, empty); }
        enqueue(player, new Write(empty, storageType == StorageType.MYSQL));
    }
    public void saveAndRemove(UUID player, int size) {
        ItemStack[] removed;
        synchronized (lock) { generations.merge(player, 1L, Long::sum); removed = cache.remove(player); }
        // Quitting before a load completed must never persist a fabricated empty snapshot.
        if (removed != null) enqueue(player, new Write(copy(removed, size), false));
    }
    public void flush(UUID player, int size) { enqueue(player, new Write(getOrLoad(player, size), false)); }
    public CompletableFuture<Void> flushAllAsync(int size) {
        Map<UUID, Write> writes;
        synchronized (lock) {
            writes = new HashMap<>(pendingWrites);
            cache.forEach((id, contents) -> writes.put(id, new Write(copy(contents, size), false)));
        }
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        writes.forEach((id, write) -> tasks.add(enqueue(id, write)));
        tasks.add(CompletableFuture.runAsync(() -> { }, ioExecutor)); // Includes departed players' queued writes.
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
    }
    public void shutdown() { ioExecutor.shutdown(); }

    private CompletableFuture<Void> enqueue(UUID player, Write write) {
        if (storageType == StorageType.ONLY_RAM) return CompletableFuture.completedFuture(null);
        synchronized (lock) { pendingWrites.put(player, write); }
        var task = CompletableFuture.runAsync(() -> {
            persist(player, write);
            if (plugin instanceof Accessory accessory) accessory.debug().trace("storage", player.toString(), () -> "saved owner=" + player + " slots=" + write.contents().length);
            synchronized (lock) { pendingWrites.remove(player, write); }
        }, ioExecutor);
        task.whenComplete((ignored, ex) -> { if (ex != null) log("save (retained for retry)", player, ex); });
        return task;
    }
    private ItemStack[] load(UUID player, int size) {
        if (storageType == StorageType.ONLY_RAM) return new ItemStack[size];
        try {
            YamlConfiguration config = new YamlConfiguration();
            if (storageType == StorageType.MYSQL) {
                String raw = sqlManager.loadInventory(player);
                if (raw == null || raw.isEmpty()) return new ItemStack[size];
                config.loadFromString(new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8));
            } else {
                File file = fileOf(player);
                if (!file.exists()) return new ItemStack[size];
                config.load(file); // Unlike loadConfiguration(), parsing errors propagate.
            }
            List<?> raw = config.getList("contents");
            if (raw == null) throw new IllegalArgumentException("Missing contents list");
            ItemStack[] contents = new ItemStack[raw.size()];
            for (int i = 0; i < contents.length; i++) {
                Object value = raw.get(i);
                if (value != null && !(value instanceof ItemStack)) throw new IllegalArgumentException("Invalid item at slot " + i);
                contents[i] = value == null ? null : ((ItemStack) value).clone();
            }
            int format = config.getInt("format", 1);
            if (format != 1 && format != 2) throw new IllegalArgumentException("Unsupported inventory format: " + format);
            return stableSlots && format == 1 ? InventorySlotLayout.migrateLegacy(contents, legacySizes, size) : copy(contents, size);
        } catch (Exception ex) { throw new CompletionException("Cannot load accessory inventory " + player, ex); }
    }
    private void persist(UUID player, Write write) {
        try {
            if (storageType == StorageType.MYSQL && write.delete()) { sqlManager.deleteInventory(player); return; }
            YamlConfiguration config = new YamlConfiguration();
            config.set("format", stableSlots ? 2 : 1);
            config.set("contents", Arrays.asList(write.contents()));
            if (storageType == StorageType.MYSQL) {
                sqlManager.saveInventory(player, Base64.getEncoder().encodeToString(config.saveToString().getBytes(StandardCharsets.UTF_8)));
            } else if (storageType == StorageType.YML) {
                Path destination = fileOf(player).toPath();
                Files.createDirectories(destination.getParent());
                Path temporary = Files.createTempFile(destination.getParent(), player + "-", ".tmp");
                try {
                    config.save(temporary.toFile());
                    try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(temporary); }
            }
        } catch (Exception ex) { throw new CompletionException("Cannot save accessory inventory " + player, ex); }
    }
    private File fileOf(UUID player) { return new File(plugin.getDataFolder(), "contains/" + player + ".yml"); }
    private static ItemStack[] slice(ItemStack[] full, int start, int size) {
        ItemStack[] result = new ItemStack[Math.max(0, size)];
        for (int i = 0; i < result.length && start + i < full.length; i++) if (start + i >= 0 && full[start + i] != null) result[i] = full[start + i].clone();
        return result;
    }
    private static ItemStack[] copy(ItemStack[] source, int minimum) {
        // Hidden pages survive profile/layout shrinkage.
        ItemStack[] result = new ItemStack[Math.max(minimum, source == null ? 0 : source.length)];
        if (source != null) for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].clone();
        return result;
    }
    private void log(String operation, UUID player, Throwable ex) {
        Throwable root = ex; while (root.getCause() != null) root = root.getCause();
        plugin.getLogger().warning("Accessory " + operation + " failed for " + player + ": " + root.getMessage());
    }
}

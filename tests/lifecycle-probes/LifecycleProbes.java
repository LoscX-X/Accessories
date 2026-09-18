import com.blanoir.accessory.Accessory;
import com.blanoir.accessory.config.AccessorySettings;
import com.blanoir.accessory.database.mysql.SqlManager;
import com.blanoir.accessory.module.inventory.*;
import org.bukkit.inventory.ItemStack;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Regression checks against actual Store/Storage code with injected SQL/codec boundaries. */
public class LifecycleProbes {
    static Path root;
    static int assertions;
    static final int SIZE = 108;
    static final List<AccessoryStore> stores = new ArrayList<>();
    public static void main(String[] args) throws Exception {
        root = Path.of(args[0]);
        try {
            for (var mode : AccessoryStore.StorageType.values()) normal(mode);
            copies(); quitBeforeLoad(); quickRejoin(); failedRead(); failedWrite(); failedDelete();
            failedDiskWrite(); corruptDisk(); emptyQuit(); shutdownBarrier(); hiddenPages(); legacyPages();
            System.out.println("Storage regression passed: " + assertions + " assertions");
        } finally { for (var store : stores) store.shutdown(); }
    }
    static Accessory plugin(String name) throws Exception {
        var plugin = new Accessory(); plugin.folder = root.resolve(name).toFile();
        Files.createDirectories(plugin.folder.toPath()); return plugin;
    }
    static AccessoryStore store(Accessory p, AccessoryStore.StorageType mode, SqlManager sql) {
        var result = new AccessoryStore(p, mode, sql); stores.add(result); return result;
    }
    static ItemStack[] item(String id) { var result = new ItemStack[SIZE]; result[0] = new ItemStack(id); return result; }
    static boolean has(ItemStack[] items, String id) { return Arrays.stream(items).anyMatch(i -> i != null && i.id.equals(id)); }
    static ExecutorService io(AccessoryStore store) throws Exception {
        var field = AccessoryStore.class.getDeclaredField("ioExecutor"); field.setAccessible(true); return (ExecutorService) field.get(store);
    }
    static void drain(AccessoryStore store) throws Exception { io(store).submit(() -> {}).get(5, TimeUnit.SECONDS); }
    static CountDownLatch block(AccessoryStore store) throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        io(store).submit(() -> { entered.countDown(); try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("gate timeout"); }
            catch (InterruptedException e) { throw new RuntimeException(e); } });
        if (!entered.await(5, TimeUnit.SECONDS)) throw new AssertionError("gate not entered"); return release;
    }
    static void check(String message, boolean condition) {
        if (!condition) throw new AssertionError(message);
        assertions++; System.out.println("OK " + message);
    }
    static void rejects(String message, Runnable action) {
        try { action.run(); } catch (CompletionException ex) { check(message, true); return; }
        throw new AssertionError("Expected failure: " + message);
    }
    static void normal(AccessoryStore.StorageType mode) throws Exception {
        var p = plugin("normal-" + mode); var sql = new SqlManager(p); var s = store(p, mode, sql); var id = UUID.randomUUID();
        s.update(id, item("ring"), SIZE); check(mode + " cached contents", has(s.getOrLoad(id, SIZE), "ring"));
        s.saveAndRemove(id, SIZE); drain(s);
        check(mode + " quit semantics", has(s.getOrLoad(id, SIZE), "ring") == (mode != AccessoryStore.StorageType.ONLY_RAM));
        s.clear(id, SIZE); s.flushAllAsync(SIZE).join(); s.saveAndRemove(id, SIZE); drain(s);
        check(mode + " clear remains empty", !has(s.getOrLoad(id, SIZE), "ring"));
    }
    static void copies() throws Exception {
        var p = plugin("copies"); var s = store(p, AccessoryStore.StorageType.ONLY_RAM, null); var id = UUID.randomUUID();
        var source = item("ring"); s.update(id, source, SIZE); source[0].id = "changed";
        s.getOrLoad(id, SIZE)[0].id = "changed-again";
        check("snapshots cannot mutate cache", has(s.getOrLoad(id, SIZE), "ring"));
        ItemStack[] page = new ItemStack[27]; page[26] = new ItemStack("tail");
        s.updateSlice(id, 54, page, 27, SIZE); page[26].id = "changed";
        check("different page sizes keep stable offsets and clones", has(s.getSliceOrLoad(id, 54, 27, SIZE), "tail"));
    }
    static void quitBeforeLoad() throws Exception {
        var p = plugin("early-quit"); var sql = new SqlManager(p); var seed = store(p, AccessoryStore.StorageType.MYSQL, sql); var id = UUID.randomUUID();
        seed.update(id, item("ring"), SIZE); seed.flushAllAsync(SIZE).join();
        var s = store(p, AccessoryStore.StorageType.MYSQL, sql); var gate = block(s);
        s.preload(id, SIZE); s.saveAndRemove(id, SIZE); gate.countDown(); drain(s);
        var field = AccessoryStore.class.getDeclaredField("cache"); field.setAccessible(true);
        check("late preload cannot resurrect departed cache", !((Map<?, ?>) field.get(s)).containsKey(id));
        check("quit before load preserves persisted row", has(store(p, AccessoryStore.StorageType.MYSQL, sql).getOrLoad(id, SIZE), "ring"));
    }
    static void quickRejoin() throws Exception {
        var p = plugin("quick-rejoin"); var sql = new SqlManager(p); var s = store(p, AccessoryStore.StorageType.MYSQL, sql); var id = UUID.randomUUID();
        s.update(id, item("old"), SIZE); s.flushAllAsync(SIZE).join(); s.update(id, item("new"), SIZE);
        var gate = block(s); s.saveAndRemove(id, SIZE);
        var read = s.readAsync(id, SIZE); check("rejoin waits for queued persistence", !read.isDone());
        gate.countDown(); check("rejoin observes latest snapshot", has(read.get(5, TimeUnit.SECONDS), "new"));
    }
    static void failedRead() throws Exception {
        var p = plugin("failed-read"); var sql = new SqlManager(p); var s = store(p, AccessoryStore.StorageType.MYSQL, sql); var id = UUID.randomUUID();
        s.update(id, item("ring"), SIZE); s.saveAndRemove(id, SIZE); drain(s); String saved = sql.rows.get(id);
        sql.failReads = true; rejects("load failure propagates", () -> s.getOrLoad(id, SIZE));
        s.saveAndRemove(id, SIZE); s.flushAllAsync(SIZE).join();
        check("load failure cannot overwrite a good row with empty data", saved.equals(sql.rows.get(id)));
        sql.failReads = false; check("load can recover", has(s.getOrLoad(id, SIZE), "ring"));
    }
    static void failedWrite() throws Exception {
        var p = plugin("failed-write"); var sql = new SqlManager(p); var s = store(p, AccessoryStore.StorageType.MYSQL, sql); var id = UUID.randomUUID();
        sql.failWrites = true; s.update(id, item("ring"), SIZE); s.saveAndRemove(id, SIZE); drain(s);
        rejects("flush future reports write failure", () -> s.flushAllAsync(SIZE).join());
        check("failed quit retains latest readable snapshot", has(s.getOrLoad(id, SIZE), "ring"));
        sql.failWrites = false; s.flushAllAsync(SIZE).join();
        check("failed write is retried successfully", has(store(p, AccessoryStore.StorageType.MYSQL, sql).getOrLoad(id, SIZE), "ring"));
    }
    static void failedDelete() throws Exception {
        var p = plugin("failed-delete"); var sql = new SqlManager(p); var s = store(p, AccessoryStore.StorageType.MYSQL, sql); var id = UUID.randomUUID();
        s.update(id, item("ring"), SIZE); s.flushAllAsync(SIZE).join(); sql.failDeletes = true; s.clear(id, SIZE); drain(s);
        sql.failDeletes = false; s.flushAllAsync(SIZE).join();
        check("failed clear cannot resurrect old items after retry", !has(store(p, AccessoryStore.StorageType.MYSQL, sql).getOrLoad(id, SIZE), "ring"));
    }
    static void failedDiskWrite() throws Exception {
        var p = plugin("failed-disk"); var s = store(p, AccessoryStore.StorageType.YML, null); var id = UUID.randomUUID();
        Path obstruction = p.folder.toPath().resolve("contains"); Files.writeString(obstruction, "obstruction");
        s.update(id, item("ring"), SIZE); s.saveAndRemove(id, SIZE); drain(s);
        Files.move(obstruction, p.folder.toPath().resolve("fixture.txt")); s.flushAllAsync(SIZE).join();
        check("failed YML quit retains data for retry", has(store(p, AccessoryStore.StorageType.YML, null).getOrLoad(id, SIZE), "ring"));
    }
    static void corruptDisk() throws Exception {
        var p = plugin("corrupt-disk"); var s = store(p, AccessoryStore.StorageType.YML, null); var id = UUID.randomUUID();
        Path folder = p.folder.toPath().resolve("contains"); Files.createDirectories(folder); Path file = folder.resolve(id + ".yml"); Files.writeString(file, "broken");
        rejects("corrupt file fails visibly", () -> s.getOrLoad(id, SIZE)); s.saveAndRemove(id, SIZE); s.flushAllAsync(SIZE).join();
        check("corrupt file preserved for repair", Files.readString(file).equals("broken"));
    }
    static void emptyQuit() throws Exception {
        var p = plugin("empty-quit"); var s = store(p, AccessoryStore.StorageType.YML, null); var id = UUID.randomUUID();
        s.update(id, item("ring"), SIZE); s.saveAndRemove(id, SIZE); drain(s); s.saveAndRemove(id, SIZE); drain(s);
        check("duplicate quit never writes fabricated emptiness", has(s.getOrLoad(id, SIZE), "ring"));
    }
    static void shutdownBarrier() throws Exception {
        var p = plugin("shutdown");
        var settings = new AccessorySettings.Storage(AccessoryStore.StorageType.MYSQL, new AccessorySettings.Mysql("fake", 1, "fake", "fake", "", 1, 1, 1, 1, 1));
        var storage = new AccessoryStorage(p, settings); var s = storage.store(); stores.add(s);
        var field = AccessoryStorage.class.getDeclaredField("sql"); field.setAccessible(true); var sql = (SqlManager) field.get(storage);
        var id = UUID.randomUUID(); s.update(id, item("ring"), SIZE); var gate = block(s); s.saveAndRemove(id, SIZE);
        var close = CompletableFuture.runAsync(() -> storage.close(SIZE));
        check("SQL is open while quit save is queued", !sql.closed);
        gate.countDown(); close.get(5, TimeUnit.SECONDS);
        check("shutdown drains quit save before closing pool", sql.closed && sql.rows.containsKey(id));
    }
    static void hiddenPages() throws Exception {
        var p = plugin("hidden"); var s = store(p, AccessoryStore.StorageType.YML, null); var id = UUID.randomUUID();
        var all = item("ring"); all[80] = new ItemStack("hidden"); s.update(id, all, SIZE); s.flush(id, 9); drain(s); s.saveAndRemove(id, 9); drain(s);
        check("profile shrink preserves hidden page items", has(store(p, AccessoryStore.StorageType.YML, null).getOrLoad(id, 9), "hidden"));
    }
    static void legacyPages() throws Exception {
        var p = plugin("legacy"); var s = store(p, AccessoryStore.StorageType.YML, null); var id = UUID.randomUUID();
        Path folder = p.folder.toPath().resolve("contains"); Files.createDirectories(folder);
        var config = new org.bukkit.configuration.file.YamlConfiguration(); var flat = new ItemStack[18]; flat[9] = new ItemStack("page-two");
        config.set("contents", Arrays.asList(flat)); config.save(folder.resolve(id + ".yml").toFile());
        var loaded = s.getOrLoad(id, SIZE); check("legacy page two migrates to fixed page address", loaded[54] != null && loaded[54].id.equals("page-two"));
        s.saveAndRemove(id, SIZE); drain(s);
        check("migration is not repeated after saving current format", store(p, AccessoryStore.StorageType.YML, null).getOrLoad(id, SIZE)[54].id.equals("page-two"));
    }
}

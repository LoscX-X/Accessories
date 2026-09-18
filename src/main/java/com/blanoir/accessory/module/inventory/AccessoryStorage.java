package com.blanoir.accessory.module.inventory;

import com.blanoir.accessory.config.AccessorySettings;
import com.blanoir.accessory.database.mysql.SqlManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns the inventory cache, I/O worker and optional database pool as one lifecycle. */
public final class AccessoryStorage {
    private final AccessorySettings.Storage settings;
    private final SqlManager sql;
    private final AccessoryStore store;

    public AccessoryStorage(JavaPlugin plugin, AccessorySettings.Storage settings) {
        this.settings = settings;
        sql = settings.type() == AccessoryStore.StorageType.MYSQL ? new SqlManager(plugin) : null;
        if (sql != null) {
            var mysql = settings.mysql();
            try {
                sql.init(mysql.host(), mysql.port(), mysql.database(), mysql.username(), mysql.password(),
                        mysql.poolSize(), mysql.minIdle(), mysql.maxLifetime(), mysql.connectionTimeout(), mysql.idleTimeout());
            } catch (RuntimeException ex) {
                sql.shutdown();
                throw ex;
            }
        }
        store = new AccessoryStore(plugin, settings.type(), sql);
        plugin.getLogger().info("Accessory storage mode: " + settings.type());
    }

    public AccessoryStore store() { return store; }

    public boolean uses(AccessorySettings.Storage other) {
        return settings.type() == other.type()
                && (sql == null || settings.mysql().equals(other.mysql()));
    }

    public void flush(int totalSize) { store.flushAllAsync(totalSize).join(); }
    public void shutdown() { store.shutdown(); if (sql != null) sql.shutdown(); }

    public void close(int totalSize) {
        try {
            flush(totalSize);
        } finally {
            shutdown();
        }
    }
}

package com.blanoir.accessory.database.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class SqlManager {
    private final Plugin plugin;
    private HikariDataSource dataSource;

    public SqlManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init(String host,
                     int port,
                     String database,
                     String username,
                     String password,
                     int poolSize,
                     int idle,
                     int maxLifetime,
                     int timeOut,
                     int idleTimeOut) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(idle);
        config.setIdleTimeout(idleTimeOut);
        config.setMaxLifetime(maxLifetime);
        config.setConnectionTimeout(timeOut);
        this.dataSource = new HikariDataSource(config);
        createTable();
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("数据库尚未初始化");
        }
        return dataSource.getConnection();
    }

    public void saveInventory(UUID playerId, String encodedInventory) throws SQLException {
        String sql = """
                INSERT INTO accessory_inventory (player_uuid, inventory_data, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE inventory_data = VALUES(inventory_data), updated_at = CURRENT_TIMESTAMP
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, encodedInventory);
            ps.executeUpdate();
        }
    }

    public String loadInventory(UUID playerId) throws SQLException {
        String sql = "SELECT inventory_data FROM accessory_inventory WHERE player_uuid = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("inventory_data");
                }
                return null;
            }
        }
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL datasource closed.");
        }
    }

    private void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS accessory_inventory (
                    player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                    inventory_data LONGTEXT NOT NULL,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """;
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to create accessory_inventory table", ex);
        }
    }
}

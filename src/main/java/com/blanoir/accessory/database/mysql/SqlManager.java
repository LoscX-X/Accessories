package com.blanoir.accessory.database.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import javax.xml.crypto.Data;
import java.sql.Connection;
import java.sql.SQLException;


public class SqlManager {
    private Plugin plugin;
    private HikariDataSource dataSource;
    public SqlManager (JavaPlugin plugin){
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
                     int idleTimeOut
                    ){
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        /*hikari*/
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(idle);
        config.setIdleTimeout(idleTimeOut);
        config.setMaxLifetime(maxLifetime);
        config.setConnectionTimeout(timeOut);
        this.dataSource = new HikariDataSource(config);

    }
    public DataSource dataSource(){
        return dataSource;
    }
    public Connection getConnection() throws SQLException{
        if(dataSource == null){
            throw new IllegalStateException("数据库尚未初始化");
        }
        return dataSource.getConnection();
    }
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
    private void createTable(){
        String sql = """
                CREATE TABLE IF NOT EXISTS accessory_inventory(
                    player_uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                )
                """;
    }
}

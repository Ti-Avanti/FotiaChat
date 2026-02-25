package gg.fotia.chat.storage;

import gg.fotia.chat.FotiaChat;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * 数据库管理器
 * 支持MySQL存储玩家数据
 */
public class DatabaseManager {

    private final FotiaChat plugin;
    private HikariDataSource dataSource;
    private boolean enabled = false;

    public DatabaseManager(FotiaChat plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化数据库连接
     */
    public void init() {
        ConfigurationSection config = plugin.getConfigManager().getConfig()
                .getConfigurationSection("storage");

        if (config == null) {
            plugin.getLogger().info("存储配置未找到，使用内存存储");
            return;
        }

        String type = config.getString("type", "memory");
        if (!type.equalsIgnoreCase("mysql")) {
            plugin.getLogger().info("存储类型: " + type + "，不使用MySQL");
            return;
        }

        String host = config.getString("mysql.host", "localhost");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "fotiachat");
        String username = config.getString("mysql.username", "root");
        String password = config.getString("mysql.password", "");
        int poolSize = config.getInt("mysql.pool-size", 10);

        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8");
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(poolSize);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setIdleTimeout(300000);
            hikariConfig.setConnectionTimeout(10000);
            hikariConfig.setMaxLifetime(600000);
            hikariConfig.setPoolName("FotiaChat-Pool");

            // MySQL优化参数
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(hikariConfig);
            enabled = true;

            // 创建表
            createTables();

            plugin.getLogger().info("MySQL数据库连接成功");
        } catch (Exception e) {
            plugin.getLogger().severe("MySQL数据库连接失败: " + e.getMessage());
            enabled = false;
        }
    }

    /**
     * 创建数据表
     */
    private void createTables() {
        String createPlayerDataTable = """
            CREATE TABLE IF NOT EXISTS fotiachat_players (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16) NOT NULL,
                channel_id VARCHAR(32) DEFAULT 'global',
                color_id VARCHAR(32) DEFAULT NULL,
                last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_username (username)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        String createMutesTable = """
            CREATE TABLE IF NOT EXISTS fotiachat_mutes (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16) NOT NULL,
                mute_time BIGINT NOT NULL,
                expire_time BIGINT NOT NULL,
                reason VARCHAR(255) DEFAULT '',
                muted_by VARCHAR(16) NOT NULL,
                INDEX idx_expire (expire_time)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

        try (Connection conn = getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(createPlayerDataTable)) {
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(createMutesTable)) {
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("创建数据表失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection() throws SQLException {
        if (!enabled || dataSource == null) {
            throw new SQLException("数据库未启用");
        }
        return dataSource.getConnection();
    }

    /**
     * 保存玩家数据
     */
    public void savePlayerData(UUID uuid, String username, String channelId, String colorId) {
        if (!enabled) return;

        String sql = """
            INSERT INTO fotiachat_players (uuid, username, channel_id, color_id)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                username = VALUES(username),
                channel_id = VALUES(channel_id),
                color_id = VALUES(color_id)
            """;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, username);
                stmt.setString(3, channelId);
                stmt.setString(4, colorId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("保存玩家数据失败: " + e.getMessage());
            }
        });
    }

    /**
     * 加载玩家数据
     */
    public PlayerData loadPlayerData(UUID uuid) {
        if (!enabled) return null;

        String sql = "SELECT channel_id, color_id FROM fotiachat_players WHERE uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerData(
                            uuid,
                            rs.getString("channel_id"),
                            rs.getString("color_id")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("加载玩家数据失败: " + e.getMessage());
        }

        return null;
    }

    /**
     * 更新玩家频道
     */
    public void updatePlayerChannel(UUID uuid, String channelId) {
        if (!enabled) return;

        String sql = "UPDATE fotiachat_players SET channel_id = ? WHERE uuid = ?";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, channelId);
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("更新玩家频道失败: " + e.getMessage());
            }
        });
    }

    /**
     * 更新玩家颜色
     */
    public void updatePlayerColor(UUID uuid, String colorId) {
        if (!enabled) return;

        String sql = "UPDATE fotiachat_players SET color_id = ? WHERE uuid = ?";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, colorId);
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("更新玩家颜色失败: " + e.getMessage());
            }
        });
    }

    /**
     * 关闭数据库连接
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL数据库连接已关闭");
        }
    }

    /**
     * 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    // ==================== 禁言相关方法 ====================

    /**
     * 保存禁言数据
     */
    public void saveMute(UUID uuid, String username, long muteTime, long expireTime, String reason, String mutedBy) {
        if (!enabled) return;

        String sql = """
            INSERT INTO fotiachat_mutes (uuid, username, mute_time, expire_time, reason, muted_by)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                username = VALUES(username),
                mute_time = VALUES(mute_time),
                expire_time = VALUES(expire_time),
                reason = VALUES(reason),
                muted_by = VALUES(muted_by)
            """;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, username);
                stmt.setLong(3, muteTime);
                stmt.setLong(4, expireTime);
                stmt.setString(5, reason);
                stmt.setString(6, mutedBy);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("保存禁言数据失败: " + e.getMessage());
            }
        });
    }

    /**
     * 删除禁言数据
     */
    public void deleteMute(UUID uuid) {
        if (!enabled) return;

        String sql = "DELETE FROM fotiachat_mutes WHERE uuid = ?";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("删除禁言数据失败: " + e.getMessage());
            }
        });
    }

    /**
     * 加载禁言数据
     */
    public MuteRecord loadMute(UUID uuid) {
        if (!enabled) return null;

        String sql = "SELECT username, mute_time, expire_time, reason, muted_by FROM fotiachat_mutes WHERE uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new MuteRecord(
                            uuid,
                            rs.getString("username"),
                            rs.getLong("mute_time"),
                            rs.getLong("expire_time"),
                            rs.getString("reason"),
                            rs.getString("muted_by")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("加载禁言数据失败: " + e.getMessage());
        }

        return null;
    }

    /**
     * 加载所有禁言数据
     */
    public java.util.List<MuteRecord> loadAllMutes() {
        java.util.List<MuteRecord> mutes = new java.util.ArrayList<>();
        if (!enabled) return mutes;

        String sql = "SELECT uuid, username, mute_time, expire_time, reason, muted_by FROM fotiachat_mutes WHERE expire_time = 0 OR expire_time > ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    mutes.add(new MuteRecord(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("username"),
                            rs.getLong("mute_time"),
                            rs.getLong("expire_time"),
                            rs.getString("reason"),
                            rs.getString("muted_by")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("加载所有禁言数据失败: " + e.getMessage());
        }

        return mutes;
    }

    /**
     * 清理过期禁言
     */
    public void cleanExpiredMutes() {
        if (!enabled) return;

        String sql = "DELETE FROM fotiachat_mutes WHERE expire_time > 0 AND expire_time < ?";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("清理过期禁言失败: " + e.getMessage());
            }
        });
    }

    /**
     * 玩家数据记录
     */
    public record PlayerData(UUID uuid, String channelId, String colorId) {}

    /**
     * 禁言数据记录
     */
    public record MuteRecord(UUID uuid, String username, long muteTime, long expireTime, String reason, String mutedBy) {}
}

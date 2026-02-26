package gg.fotia.chat.mute;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.storage.DatabaseManager;
import gg.fotia.chat.util.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 禁言管理器
 */
public class MuteManager {

    private final FotiaChat plugin;
    private final Map<UUID, MuteData> mutedPlayers = new ConcurrentHashMap<>();
    private File mutesFile;
    private FileConfiguration mutesConfig;

    public MuteManager(FotiaChat plugin) {
        this.plugin = plugin;
    }

    public void load() {
        mutedPlayers.clear();

        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager != null && dbManager.isEnabled()) {
            // 从数据库加载
            loadFromDatabase();
        } else {
            // 从文件加载
            loadFromFile();
        }

        plugin.getLogger().info("已加载 " + mutedPlayers.size() + " 个禁言记录");
    }

    /**
     * 从数据库加载禁言数据
     */
    private void loadFromDatabase() {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        for (DatabaseManager.MuteRecord record : dbManager.loadAllMutes()) {
            MuteData data = new MuteData(
                    record.uuid(),
                    record.username(),
                    record.muteTime(),
                    record.expireTime(),
                    record.reason(),
                    record.mutedBy()
            );
            if (!data.isExpired()) {
                mutedPlayers.put(record.uuid(), data);
            }
        }
        // 清理过期禁言
        dbManager.cleanExpiredMutes();
    }

    /**
     * 从文件加载禁言数据
     */
    private void loadFromFile() {
        mutesFile = new File(plugin.getDataFolder(), "mutes.yml");
        if (!mutesFile.exists()) {
            try {
                mutesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("无法创建禁言数据文件: " + e.getMessage());
            }
        }

        mutesConfig = YamlConfiguration.loadConfiguration(mutesFile);

        ConfigurationSection mutesSection = mutesConfig.getConfigurationSection("mutes");
        if (mutesSection != null) {
            for (String uuidStr : mutesSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    ConfigurationSection section = mutesSection.getConfigurationSection(uuidStr);
                    if (section != null) {
                        MuteData data = loadMuteData(uuid, section);
                        if (!data.isExpired()) {
                            mutedPlayers.put(uuid, data);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的UUID: " + uuidStr);
                }
            }
        }
    }

    private MuteData loadMuteData(UUID uuid, ConfigurationSection section) {
        String playerName = section.getString("name", "Unknown");
        long muteTime = section.getLong("mute-time", 0);
        long expireTime = section.getLong("expire-time", 0);
        String reason = section.getString("reason", "无");
        String mutedBy = section.getString("muted-by", "Console");

        return new MuteData(uuid, playerName, muteTime, expireTime, reason, mutedBy);
    }

    public void save() {
        // 清除过期的禁言
        mutedPlayers.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // 清空配置
        mutesConfig.set("mutes", null);

        // 保存所有禁言
        for (Map.Entry<UUID, MuteData> entry : mutedPlayers.entrySet()) {
            String path = "mutes." + entry.getKey().toString();
            MuteData data = entry.getValue();

            mutesConfig.set(path + ".name", data.getPlayerName());
            mutesConfig.set(path + ".mute-time", data.getMuteTime());
            mutesConfig.set(path + ".expire-time", data.getExpireTime());
            mutesConfig.set(path + ".reason", data.getReason());
            mutesConfig.set(path + ".muted-by", data.getMutedBy());
        }

        try {
            mutesConfig.save(mutesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存禁言数据: " + e.getMessage());
        }
    }

    /**
     * 禁言玩家
     * @param player 被禁言的玩家
     * @param duration 禁言时长(秒)，0表示永久
     * @param reason 禁言原因
     * @param mutedBy 执行禁言的人
     */
    public void mute(Player player, long duration, String reason, String mutedBy) {
        mute(player.getUniqueId(), player.getName(), duration, reason, mutedBy);
    }

    /**
     * 禁言玩家(通过UUID)
     */
    public void mute(UUID uuid, String playerName, long duration, String reason, String mutedBy) {
        long muteTime = System.currentTimeMillis();
        long expireTime = duration > 0 ? muteTime + (duration * 1000) : 0;

        MuteData data = new MuteData(uuid, playerName, muteTime, expireTime, reason, mutedBy);
        mutedPlayers.put(uuid, data);

        // 保存到数据库或文件
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager != null && dbManager.isEnabled()) {
            dbManager.saveMute(uuid, playerName, muteTime, expireTime, reason, mutedBy);
        } else {
            save();
        }
    }

    /**
     * 解除禁言
     */
    public boolean unmute(UUID uuid) {
        MuteData removed = mutedPlayers.remove(uuid);
        if (removed != null) {
            // 从数据库或文件删除
            DatabaseManager dbManager = plugin.getDatabaseManager();
            if (dbManager != null && dbManager.isEnabled()) {
                dbManager.deleteMute(uuid);
            } else {
                save();
            }
            return true;
        }
        return false;
    }

    /**
     * 检查玩家是否被禁言
     */
    public boolean isMuted(Player player) {
        // 先检查权限禁言
        String mutePermission = plugin.getConfigManager().getMutePermission();
        if (mutePermission != null && !mutePermission.isEmpty()) {
            if (player.hasPermission(mutePermission)) {
                return true;
            }
        }
        // 再检查普通禁言（支持实时同步）
        return isMutedWithSync(player.getUniqueId());
    }

    /**
     * 检查玩家是否被禁言(通过UUID)，支持数据库实时同步
     */
    private boolean isMutedWithSync(UUID uuid) {
        DatabaseManager dbManager = plugin.getDatabaseManager();

        // 如果启用了数据库，优先从数据库实时查询（支持跨服同步）
        if (dbManager != null && dbManager.isEnabled()) {
            DatabaseManager.MuteRecord record = dbManager.loadMute(uuid);
            if (record != null) {
                MuteData data = new MuteData(
                        record.uuid(),
                        record.username(),
                        record.muteTime(),
                        record.expireTime(),
                        record.reason(),
                        record.mutedBy()
                );
                if (!data.isExpired()) {
                    // 更新本地缓存
                    mutedPlayers.put(uuid, data);
                    return true;
                } else {
                    // 删除过期记录
                    mutedPlayers.remove(uuid);
                    dbManager.deleteMute(uuid);
                    return false;
                }
            } else {
                // 数据库中没有记录，清除本地缓存
                mutedPlayers.remove(uuid);
                return false;
            }
        }

        // 未启用数据库，使用本地缓存
        MuteData localData = mutedPlayers.get(uuid);
        if (localData != null) {
            if (localData.isExpired()) {
                mutedPlayers.remove(uuid);
                save();
                return false;
            }
            return true;
        }

        return false;
    }

    /**
     * 检查玩家是否被禁言(通过UUID)
     */
    public boolean isMuted(UUID uuid) {
        return isMutedWithSync(uuid);
    }

    /**
     * 获取禁言数据
     */
    public MuteData getMuteData(UUID uuid) {
        MuteData data = mutedPlayers.get(uuid);
        if (data != null && data.isExpired()) {
            mutedPlayers.remove(uuid);
            save();
            return null;
        }
        return data;
    }

    /**
     * 获取禁言数据
     */
    public MuteData getMuteData(Player player) {
        return getMuteData(player.getUniqueId());
    }

    /**
     * 获取所有禁言数据
     */
    public Map<UUID, MuteData> getAllMutes() {
        return mutedPlayers;
    }
}

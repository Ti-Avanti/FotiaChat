package gg.fotia.chat.ignore;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.storage.DatabaseManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家屏蔽管理器
 */
public class IgnoreManager {

    private final FotiaChat plugin;
    // 玩家UUID -> 被屏蔽玩家UUID集合
    private final Map<UUID, Set<UUID>> ignoreMap = new ConcurrentHashMap<>();
    private File ignoresFile;
    private FileConfiguration ignoresConfig;

    public IgnoreManager(FotiaChat plugin) {
        this.plugin = plugin;
    }

    public void load() {
        ignoreMap.clear();

        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager != null && dbManager.isEnabled()) {
            // 数据库模式下不预加载，按需查询
            plugin.getLogger().info("屏蔽数据使用数据库存储");
        } else {
            // 从文件加载
            loadFromFile();
            plugin.getLogger().info("已加载 " + ignoreMap.size() + " 个玩家的屏蔽列表");
        }
    }

    /**
     * 从文件加载屏蔽数据
     */
    private void loadFromFile() {
        ignoresFile = new File(plugin.getDataFolder(), "ignores.yml");
        if (!ignoresFile.exists()) {
            try {
                ignoresFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("无法创建屏蔽数据文件: " + e.getMessage());
            }
        }

        ignoresConfig = YamlConfiguration.loadConfiguration(ignoresFile);

        ConfigurationSection ignoresSection = ignoresConfig.getConfigurationSection("ignores");
        if (ignoresSection != null) {
            for (String playerUuidStr : ignoresSection.getKeys(false)) {
                try {
                    UUID playerUuid = UUID.fromString(playerUuidStr);
                    List<String> ignoredList = ignoresSection.getStringList(playerUuidStr);
                    Set<UUID> ignoredSet = ConcurrentHashMap.newKeySet();
                    for (String ignoredUuidStr : ignoredList) {
                        try {
                            ignoredSet.add(UUID.fromString(ignoredUuidStr));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    if (!ignoredSet.isEmpty()) {
                        ignoreMap.put(playerUuid, ignoredSet);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("无效的UUID: " + playerUuidStr);
                }
            }
        }
    }

    /**
     * 保存屏蔽数据到文件
     */
    public void save() {
        if (ignoresConfig == null) return;

        ignoresConfig.set("ignores", null);

        for (Map.Entry<UUID, Set<UUID>> entry : ignoreMap.entrySet()) {
            List<String> ignoredList = new ArrayList<>();
            for (UUID ignoredUuid : entry.getValue()) {
                ignoredList.add(ignoredUuid.toString());
            }
            if (!ignoredList.isEmpty()) {
                ignoresConfig.set("ignores." + entry.getKey().toString(), ignoredList);
            }
        }

        try {
            ignoresConfig.save(ignoresFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存屏蔽数据: " + e.getMessage());
        }
    }

    /**
     * 添加屏蔽
     */
    public void addIgnore(UUID playerUuid, UUID ignoredUuid, String ignoredName) {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager != null && dbManager.isEnabled()) {
            dbManager.addIgnore(playerUuid, ignoredUuid, ignoredName);
        } else {
            ignoreMap.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(ignoredUuid);
            save();
        }
    }

    /**
     * 移除屏蔽
     */
    public void removeIgnore(UUID playerUuid, UUID ignoredUuid) {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager != null && dbManager.isEnabled()) {
            dbManager.removeIgnore(playerUuid, ignoredUuid);
        } else {
            Set<UUID> ignoredSet = ignoreMap.get(playerUuid);
            if (ignoredSet != null) {
                ignoredSet.remove(ignoredUuid);
                if (ignoredSet.isEmpty()) {
                    ignoreMap.remove(playerUuid);
                }
                save();
            }
        }
    }

    /**
     * 检查是否屏蔽了某个玩家
     */
    public boolean isIgnoring(UUID playerUuid, UUID targetUuid) {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager != null && dbManager.isEnabled()) {
            return dbManager.isIgnoring(playerUuid, targetUuid);
        } else {
            Set<UUID> ignoredSet = ignoreMap.get(playerUuid);
            return ignoredSet != null && ignoredSet.contains(targetUuid);
        }
    }

    /**
     * 获取玩家的屏蔽列表
     */
    public List<String> getIgnoreList(UUID playerUuid) {
        DatabaseManager dbManager = plugin.getDatabaseManager();
        if (dbManager != null && dbManager.isEnabled()) {
            return dbManager.getIgnoreList(playerUuid);
        } else {
            Set<UUID> ignoredSet = ignoreMap.get(playerUuid);
            if (ignoredSet == null || ignoredSet.isEmpty()) {
                return Collections.emptyList();
            }
            // 文件模式下只能返回UUID，无法获取玩家名
            List<String> names = new ArrayList<>();
            for (UUID uuid : ignoredSet) {
                // 尝试获取在线玩家名
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null) {
                    names.add(player.getName());
                } else {
                    names.add(uuid.toString().substring(0, 8) + "...");
                }
            }
            return names;
        }
    }

    /**
     * 切换屏蔽状态
     * @return true 表示添加了屏蔽，false 表示移除了屏蔽
     */
    public boolean toggleIgnore(UUID playerUuid, UUID ignoredUuid, String ignoredName) {
        if (isIgnoring(playerUuid, ignoredUuid)) {
            removeIgnore(playerUuid, ignoredUuid);
            return false;
        } else {
            addIgnore(playerUuid, ignoredUuid, ignoredName);
            return true;
        }
    }
}

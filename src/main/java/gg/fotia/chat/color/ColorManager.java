package gg.fotia.chat.color;

import gg.fotia.chat.FotiaChat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 聊天颜色管理器
 */
public class ColorManager {

    private final FotiaChat plugin;
    private FileConfiguration colorConfig;
    // 不可变快照 + volatile 整体替换：异步聊天线程读取时不与 reload 竞争
    private volatile Map<String, ChatColor> colors = Map.of();
    private final Map<UUID, String> playerColors = new java.util.concurrent.ConcurrentHashMap<>();

    private volatile String defaultColorId;

    public ColorManager(FotiaChat plugin) {
        this.plugin = plugin;
    }

    public void load() {
        saveDefaultConfig();

        File colorFile = new File(plugin.getDataFolder(), "colors.yml");
        colorConfig = YamlConfiguration.loadConfiguration(colorFile);

        // 合并默认配置
        InputStream defaultStream = plugin.getResource("colors.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            colorConfig.setDefaults(defaultConfig);
        }

        // 读取默认颜色
        this.defaultColorId = colorConfig.getString("default-color", "white");

        // 加载颜色配置
        Map<String, ChatColor> loaded = new LinkedHashMap<>();
        ConfigurationSection colorsSection = colorConfig.getConfigurationSection("colors");
        if (colorsSection != null) {
            for (String id : colorsSection.getKeys(false)) {
                ConfigurationSection colorSection = colorsSection.getConfigurationSection(id);
                if (colorSection != null) {
                    ChatColor color = loadColor(id, colorSection);
                    loaded.put(id, color);
                }
            }
        }
        this.colors = Collections.unmodifiableMap(loaded);

        plugin.getLogger().info("已加载 " + loaded.size() + " 种聊天颜色");
    }

    private ChatColor loadColor(String id, ConfigurationSection section) {
        String nameKey = section.getString("name-key", "");
        String name = nameKey == null || nameKey.isBlank()
                ? section.getString("name", id)
                : "lang:" + nameKey;

        ColorType type;
        try {
            type = ColorType.valueOf(section.getString("type", "SINGLE").toUpperCase());
        } catch (IllegalArgumentException e) {
            type = ColorType.SINGLE;
        }

        String format = section.getString("format", "<white>");
        String permission = section.getString("permission", "");

        return new ChatColor(id, name, type, format, permission);
    }

    private void saveDefaultConfig() {
        File colorFile = new File(plugin.getDataFolder(), "colors.yml");
        if (!colorFile.exists()) {
            plugin.saveResource("colors.yml", false);
        }
    }

    /**
     * 获取玩家当前选择的颜色
     */
    public ChatColor getPlayerColor(Player player) {
        String colorId = playerColors.getOrDefault(player.getUniqueId(), defaultColorId);
        colorId = gg.fotia.chat.api.PlayerPreferences.value(player, "chat.color", colorId);
        ChatColor color = colors.get(colorId);
        if (color == null || !color.hasPermission(player)) {
            return colors.get(defaultColorId);
        }
        return color;
    }

    /**
     * 设置玩家的聊天颜色
     */
    public boolean setPlayerColor(Player player, String colorId) {
        ChatColor color = colors.get(colorId);
        if (color == null) {
            return false;
        }
        if (!color.hasPermission(player)) {
            return false;
        }
        // 保存到数据库
        if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isEnabled()) {
            plugin.getDatabaseManager().updatePlayerColor(player.getUniqueId(), player.getName(), colorId);
        }
        playerColors.put(player.getUniqueId(), colorId);
        gg.fotia.chat.api.PlayerPreferences.save(player, "chat.color", colorId);

        return true;
    }

    /**
     * 重置玩家的聊天颜色为默认
     */
    public void resetPlayerColor(Player player) {
        // 保存到数据库
        if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isEnabled()) {
            plugin.getDatabaseManager().updatePlayerColor(player.getUniqueId(), player.getName(), null);
        }
        // 保留本次会话的显式重置，迟到的登录读取不能覆盖它。
        playerColors.put(player.getUniqueId(), "");
        gg.fotia.chat.api.PlayerPreferences.save(player, "chat.color", "");
    }

    /**
     * 应用玩家颜色到消息
     */
    public String applyPlayerColor(Player player, String message) {
        ChatColor color = getPlayerColor(player);
        if (color == null) {
            return message;
        }
        return color.apply(message);
    }

    /**
     * 获取玩家可用的颜色列表
     */
    public List<ChatColor> getAvailableColors(Player player) {
        List<ChatColor> available = new ArrayList<>();
        for (ChatColor color : colors.values()) {
            if (color.hasPermission(player)) {
                available.add(color);
            }
        }
        return available;
    }

    /**
     * 获取所有颜色
     */
    public Map<String, ChatColor> getColors() {
        return colors;
    }

    /**
     * 获取指定颜色
     */
    public ChatColor getColor(String id) {
        return colors.get(id);
    }

    public String getDisplayName(Player player, ChatColor color) {
        if (color == null) {
            return "";
        }
        return plugin.getMessageManager().resolveConfigured(player, color.getName());
    }

    /**
     * 获取默认颜色ID
     */
    public String getDefaultColorId() {
        return defaultColorId;
    }

    /**
     * 加载玩家颜色（从数据库）
     */
    public void loadPlayerColor(Player player, String colorId) {
        if (colorId != null && colors.containsKey(colorId)) {
            playerColors.putIfAbsent(player.getUniqueId(), colorId);
        }
    }

    public void removePlayer(UUID playerUuid) {
        playerColors.remove(playerUuid);
    }

    /**
     * 获取颜色ID列表（用于Tab补全）
     */
    public List<String> getColorIds() {
        return new ArrayList<>(colors.keySet());
    }
}

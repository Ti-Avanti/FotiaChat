package gg.fotia.chat.channel;

import gg.fotia.chat.FotiaChat;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 频道管理器
 */
public class ChannelManager {

    private final FotiaChat plugin;
    private FileConfiguration channelsConfig;
    private final Map<String, Channel> channels = new HashMap<>();
    private final Map<UUID, String> playerChannels = new ConcurrentHashMap<>();
    private String defaultChannelId;

    public ChannelManager(FotiaChat plugin) {
        this.plugin = plugin;
    }

    public void loadChannels() {
        channels.clear();
        saveDefaultChannelsConfig();

        File channelsFile = new File(plugin.getDataFolder(), "channels.yml");
        channelsConfig = YamlConfiguration.loadConfiguration(channelsFile);

        // 合并默认配置
        InputStream defaultStream = plugin.getResource("channels.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            channelsConfig.setDefaults(defaultConfig);
        }

        // 加载频道
        ConfigurationSection channelsSection = channelsConfig.getConfigurationSection("channels");
        if (channelsSection != null) {
            for (String channelId : channelsSection.getKeys(false)) {
                ConfigurationSection section = channelsSection.getConfigurationSection(channelId);
                if (section != null) {
                    Channel channel = loadChannel(channelId, section);
                    channels.put(channelId.toLowerCase(), channel);

                    if (channel.isDefault()) {
                        defaultChannelId = channelId.toLowerCase();
                    }
                }
            }
        }

        // 如果没有设置默认频道，使用第一个频道
        if (defaultChannelId == null && !channels.isEmpty()) {
            defaultChannelId = channels.keySet().iterator().next();
        }

        plugin.getLogger().info("已加载 " + channels.size() + " 个频道");
    }

    private void saveDefaultChannelsConfig() {
        File channelsFile = new File(plugin.getDataFolder(), "channels.yml");
        if (!channelsFile.exists()) {
            plugin.saveResource("channels.yml", false);
        }
    }

    private Channel loadChannel(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        String typeStr = section.getString("type", "PUBLIC");
        ChannelType type = ChannelType.fromId(typeStr);
        String format = section.getString("format", "<!i><gray>[<white>{channel}</white>]</gray> <white>{player}</white><gray>:</gray> {message}");
        String permission = section.getString("permission", type.getPermission());
        String shortcut = section.getString("shortcut", "");
        int radius = section.getInt("radius", 0);
        boolean isDefault = section.getBoolean("default", false);

        // 加载Hover配置
        boolean hoverEnabled = false;
        List<String> hoverText = new ArrayList<>();
        ConfigurationSection hoverSection = section.getConfigurationSection("hover");
        if (hoverSection != null) {
            hoverEnabled = hoverSection.getBoolean("enabled", false);
            hoverText = hoverSection.getStringList("text");
        }

        // 加载Click配置
        boolean clickEnabled = false;
        ClickEvent.Action clickAction = ClickEvent.Action.SUGGEST_COMMAND;
        String clickValue = "";
        ConfigurationSection clickSection = section.getConfigurationSection("click");
        if (clickSection != null) {
            clickEnabled = clickSection.getBoolean("enabled", false);
            String actionStr = clickSection.getString("action", "SUGGEST_COMMAND");
            clickAction = parseClickAction(actionStr);
            clickValue = clickSection.getString("value", "");
        }

        return new Channel(id, name, type, format, permission, shortcut, radius, isDefault,
                hoverEnabled, hoverText, clickEnabled, clickAction, clickValue);
    }

    /**
     * 解析点击动作类型
     */
    private ClickEvent.Action parseClickAction(String action) {
        return switch (action.toUpperCase()) {
            case "RUN_COMMAND" -> ClickEvent.Action.RUN_COMMAND;
            case "OPEN_URL" -> ClickEvent.Action.OPEN_URL;
            case "COPY_TO_CLIPBOARD" -> ClickEvent.Action.COPY_TO_CLIPBOARD;
            default -> ClickEvent.Action.SUGGEST_COMMAND;
        };
    }

    /**
     * 获取频道
     */
    public Channel getChannel(String id) {
        return channels.get(id.toLowerCase());
    }

    /**
     * 获取所有频道
     */
    public Collection<Channel> getAllChannels() {
        return channels.values();
    }

    /**
     * 获取默认频道
     */
    public Channel getDefaultChannel() {
        return channels.get(defaultChannelId);
    }

    /**
     * 获取玩家当前频道
     */
    public Channel getPlayerChannel(Player player) {
        String channelId = playerChannels.get(player.getUniqueId());
        if (channelId == null) {
            return getDefaultChannel();
        }
        Channel channel = channels.get(channelId);
        return channel != null ? channel : getDefaultChannel();
    }

    /**
     * 设置玩家频道
     */
    public void setPlayerChannel(Player player, String channelId) {
        playerChannels.put(player.getUniqueId(), channelId.toLowerCase());

        // 保存到数据库
        if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isEnabled()) {
            plugin.getDatabaseManager().updatePlayerChannel(player.getUniqueId(), channelId.toLowerCase());
        }
    }

    /**
     * 加载玩家频道（从数据库）
     */
    public void loadPlayerChannel(Player player, String channelId) {
        if (channelId != null && channels.containsKey(channelId.toLowerCase())) {
            playerChannels.put(player.getUniqueId(), channelId.toLowerCase());
        }
    }

    /**
     * 移除玩家频道记录
     */
    public void removePlayer(UUID uuid) {
        playerChannels.remove(uuid);
    }

    /**
     * 检查玩家是否有权限使用频道
     */
    public boolean hasChannelPermission(Player player, Channel channel) {
        if (channel.getPermission() == null || channel.getPermission().isEmpty()) {
            return true;
        }
        return player.hasPermission(channel.getPermission());
    }

    /**
     * 通过快捷方式获取频道
     */
    public Channel getChannelByShortcut(String shortcut) {
        for (Channel channel : channels.values()) {
            if (channel.getShortcut() != null && channel.getShortcut().equalsIgnoreCase(shortcut)) {
                return channel;
            }
        }
        return null;
    }

    /**
     * 检查频道是否存在
     */
    public boolean channelExists(String id) {
        return channels.containsKey(id.toLowerCase());
    }
}

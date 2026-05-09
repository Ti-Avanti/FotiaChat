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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 频道管理器
 */
public class ChannelManager {

    private static final List<String> SEGMENT_KEYS = List.of("channel", "player", "message");

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

        InputStream defaultStream = plugin.getResource("channels.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            channelsConfig.setDefaults(defaultConfig);
        }

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

        boolean hoverEnabled = false;
        List<String> hoverText = new ArrayList<>();
        Map<String, MutableSegmentConfig> segmentConfigs = new LinkedHashMap<>();
        ConfigurationSection hoverSection = section.getConfigurationSection("hover");
        if (hoverSection != null) {
            if (hoverSection.contains("enabled") || hoverSection.contains("text")) {
                hoverEnabled = hoverSection.getBoolean("enabled", false);
                hoverText = hoverSection.getStringList("text");
            }
            loadSegmentHoverConfigs(hoverSection, segmentConfigs);
        }

        boolean clickEnabled = false;
        ClickEvent.Action clickAction = ClickEvent.Action.SUGGEST_COMMAND;
        String clickValue = "";
        ConfigurationSection clickSection = section.getConfigurationSection("click");
        if (clickSection != null) {
            if (clickSection.contains("enabled") || clickSection.contains("action") || clickSection.contains("value")) {
                clickEnabled = clickSection.getBoolean("enabled", false);
                clickAction = parseClickAction(clickSection.getString("action", "SUGGEST_COMMAND"));
                clickValue = clickSection.getString("value", "");
            }
            loadSegmentClickConfigs(clickSection, segmentConfigs);
        }

        return new Channel(id, name, type, format, permission, shortcut, radius, isDefault,
                hoverEnabled, hoverText, clickEnabled, clickAction, clickValue,
                buildSegmentConfigs(segmentConfigs));
    }

    private ClickEvent.Action parseClickAction(String action) {
        String safeAction = action == null ? "SUGGEST_COMMAND" : action.toUpperCase();
        return switch (safeAction) {
            case "RUN_COMMAND" -> ClickEvent.Action.RUN_COMMAND;
            case "OPEN_URL" -> ClickEvent.Action.OPEN_URL;
            case "COPY_TO_CLIPBOARD" -> ClickEvent.Action.COPY_TO_CLIPBOARD;
            default -> ClickEvent.Action.SUGGEST_COMMAND;
        };
    }

    private void loadSegmentHoverConfigs(ConfigurationSection hoverSection, Map<String, MutableSegmentConfig> segmentConfigs) {
        for (String segmentKey : SEGMENT_KEYS) {
            ConfigurationSection segmentSection = hoverSection.getConfigurationSection(segmentKey);
            if (segmentSection == null) {
                continue;
            }

            MutableSegmentConfig config = segmentConfigs.computeIfAbsent(segmentKey, key -> new MutableSegmentConfig());
            config.display = segmentSection.getString("display", defaultSegmentDisplay(segmentKey));
            config.hoverEnabled = segmentSection.getBoolean("enabled", true);
            config.hoverText = new ArrayList<>(segmentSection.getStringList("text"));
        }
    }

    private void loadSegmentClickConfigs(ConfigurationSection clickSection, Map<String, MutableSegmentConfig> segmentConfigs) {
        for (String segmentKey : SEGMENT_KEYS) {
            ConfigurationSection segmentSection = clickSection.getConfigurationSection(segmentKey);
            if (segmentSection == null) {
                continue;
            }

            MutableSegmentConfig config = segmentConfigs.computeIfAbsent(segmentKey, key -> new MutableSegmentConfig());
            if (segmentSection.contains("display") && (config.display == null || config.display.isEmpty())) {
                config.display = segmentSection.getString("display", defaultSegmentDisplay(segmentKey));
            }
            config.clickEnabled = segmentSection.getBoolean("enabled", true);
            config.clickAction = parseClickAction(segmentSection.getString("action", "SUGGEST_COMMAND"));
            config.clickValue = segmentSection.getString("value", "");
        }
    }

    private Map<String, ChannelSegmentConfig> buildSegmentConfigs(Map<String, MutableSegmentConfig> source) {
        if (source.isEmpty()) {
            return Map.of();
        }

        Map<String, ChannelSegmentConfig> result = new LinkedHashMap<>();
        for (Map.Entry<String, MutableSegmentConfig> entry : source.entrySet()) {
            String segmentKey = entry.getKey();
            MutableSegmentConfig config = entry.getValue();
            result.put(segmentKey, new ChannelSegmentConfig(
                    segmentKey,
                    config.display == null || config.display.isEmpty() ? defaultSegmentDisplay(segmentKey) : config.display,
                    config.hoverEnabled,
                    config.hoverText,
                    config.clickEnabled,
                    config.clickAction,
                    config.clickValue
            ));
        }
        return result;
    }

    private String defaultSegmentDisplay(String segmentKey) {
        return switch (segmentKey) {
            case "channel" -> "{channel}";
            case "player" -> "{player}";
            case "message" -> "{message}";
            default -> "";
        };
    }

    public Channel getChannel(String id) {
        return channels.get(id.toLowerCase());
    }

    public Collection<Channel> getAllChannels() {
        return channels.values();
    }

    public Channel getDefaultChannel() {
        return channels.get(defaultChannelId);
    }

    public Channel getPlayerChannel(Player player) {
        String channelId = playerChannels.get(player.getUniqueId());
        if (channelId == null) {
            return getDefaultChannel();
        }
        Channel channel = channels.get(channelId);
        return channel != null ? channel : getDefaultChannel();
    }

    public void setPlayerChannel(Player player, String channelId) {
        playerChannels.put(player.getUniqueId(), channelId.toLowerCase());

        if (plugin.getDatabaseManager() != null && plugin.getDatabaseManager().isEnabled()) {
            plugin.getDatabaseManager().updatePlayerChannel(player.getUniqueId(), channelId.toLowerCase());
        }
    }

    public void loadPlayerChannel(Player player, String channelId) {
        if (channelId != null && channels.containsKey(channelId.toLowerCase())) {
            playerChannels.put(player.getUniqueId(), channelId.toLowerCase());
        }
    }

    public void removePlayer(UUID uuid) {
        playerChannels.remove(uuid);
    }

    public boolean hasChannelPermission(Player player, Channel channel) {
        if (channel.getPermission() == null || channel.getPermission().isEmpty()) {
            return true;
        }
        return player.hasPermission(channel.getPermission());
    }

    public Channel getChannelByShortcut(String shortcut) {
        for (Channel channel : channels.values()) {
            if (channel.getShortcut() != null && channel.getShortcut().equalsIgnoreCase(shortcut)) {
                return channel;
            }
        }
        return null;
    }

    public boolean channelExists(String id) {
        return channels.containsKey(id.toLowerCase());
    }

    private static final class MutableSegmentConfig {
        private String display = "";
        private boolean hoverEnabled = false;
        private List<String> hoverText = new ArrayList<>();
        private boolean clickEnabled = false;
        private ClickEvent.Action clickAction = ClickEvent.Action.SUGGEST_COMMAND;
        private String clickValue = "";
    }
}

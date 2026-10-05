package gg.fotia.chat.crossserver;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.channel.Channel;
import gg.fotia.chat.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 跨服通信管理器
 */
public class CrossServerManager {

    private static final String CROSS_SERVER_MESSAGE_MARKER = "\uE000FC_MESSAGE\uE001";

    private final FotiaChat plugin;
    private final RedisHandler redisHandler;
    private final BungeeHandler bungeeHandler;

    private volatile boolean enabled = false;
    private String type = "bungeecord";
    private String serverName;
    private final Map<String, List<String>> serverGroups = new HashMap<>();
    private final List<Consumer<String>> broadcastListeners = new CopyOnWriteArrayList<>();

    public CrossServerManager(FotiaChat plugin) {
        this.plugin = plugin;
        this.redisHandler = new RedisHandler(plugin, this);
        this.bungeeHandler = new BungeeHandler(plugin, this);
    }

    /**
     * 加载配置并启用跨服通信
     */
    public void load() {
        disable();

        ConfigurationSection config = plugin.getConfigManager().getConfig()
                .getConfigurationSection("cross-server");

        if (config == null) {
            plugin.getLogger().info("跨服通信未配置，功能已禁用");
            return;
        }

        if (!config.getBoolean("enabled", false)) {
            plugin.getLogger().info("跨服通信已禁用");
            return;
        }

        type = config.getString("type", "bungeecord").toLowerCase();
        serverName = config.getString("server-name", "server1").trim();
        if (serverName.isEmpty()) {
            serverName = "server1";
        }
        if ("server1".equalsIgnoreCase(serverName)) {
            plugin.getLogger().warning("跨服 server-name 仍为默认值 server1；每台服务器必须设置唯一名称，否则消息会被当作本服消息丢弃");
        }

        serverGroups.clear();
        ConfigurationSection groupsSection = config.getConfigurationSection("server-groups");
        if (groupsSection != null) {
            for (String groupName : groupsSection.getKeys(false)) {
                serverGroups.put(groupName, List.copyOf(groupsSection.getStringList(groupName)));
            }
        }

        setupMessageHandler();

        switch (type) {
            case "redis" -> {
                ConfigurationSection redis = config.getConfigurationSection("redis");
                String host = redis == null ? "localhost" : redis.getString("host", "localhost");
                int port = redis == null ? 6379 : redis.getInt("port", 6379);
                String password = redis == null ? "" : redis.getString("password", "");
                String channel = redis == null ? "fotiachat:messages" : redis.getString("channel", "fotiachat:messages");
                redisHandler.enable(host, port, password, channel);
                enabled = redisHandler.isEnabled();
            }
            case "bungeecord" -> {
                bungeeHandler.enable();
                enabled = bungeeHandler.isEnabled();
            }
            default -> {
                enabled = false;
                plugin.getLogger().severe("不支持的跨服通信类型: " + type + "，本次启动已禁用跨服通信");
            }
        }

        if (enabled) {
            plugin.getLogger().info("跨服通信已启用 (类型: " + type + ", 服务器: " + serverName + ")");
        }
    }

    private void setupMessageHandler() {
        redisHandler.setMessageHandler(this::handleIncomingMessage);
        bungeeHandler.setMessageHandler(this::handleIncomingMessage);
    }

    private void handleIncomingMessage(CrossServerMessage message) {
        switch (message.getType()) {
            case CrossServerMessage.TYPE_CHAT -> handleChatMessage(message);
            case CrossServerMessage.TYPE_PRIVATE -> handlePrivateMessage(message);
            case CrossServerMessage.TYPE_BROADCAST -> handleBroadcastMessage(message);
            default -> {
                if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("忽略未知跨服消息类型: " + message.getType());
                }
            }
        }
    }

    /**
     * 处理已格式化的聊天消息。未知频道必须丢弃，不能回退到公共频道，
     * 否则另一台服务器的受限频道内容会泄漏给全服。
     */
    private void handleChatMessage(CrossServerMessage message) {
        String channelId = message.getChannelId();
        Channel channel = channelId == null ? null : plugin.getChannelManager().getChannel(channelId);
        if (channel == null) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().warning("忽略来自 " + message.getServerName() + " 的未知频道消息: " + channelId);
            }
            return;
        }
        if (!channel.isCrossServerEnabled()) {
            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().warning("忽略不允许跨服的频道消息: " + channelId);
            }
            return;
        }

        String permission = channel.getPermission();
        UUID senderUuid = message.getSenderUuid();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if ((permission == null || permission.isEmpty() || player.hasPermission(permission))
                    && gg.fotia.chat.api.PlayerPreferences.allows(player, "fotiachat", "chat.public", senderUuid)
                    && (senderUuid == null || !plugin.getIgnoreManager().isIgnoring(player.getUniqueId(), senderUuid))) {
                if (message.getSenderName() == null || message.getSenderName().isEmpty()) {
                    player.sendMessage(gg.fotia.chat.api.PlayerPreferences.display(player, CrossServerChatCodec.decode(message.getMessage())));
                } else {
                    player.sendMessage(gg.fotia.chat.api.PlayerPreferences.display(player, buildLocalizedChatMessage(player, channel, message)));
                }
            }
        }
    }

    private Component buildLocalizedChatMessage(Player viewer, Channel channel, CrossServerMessage message) {
        Channel localizedChannel = channel.localized(text ->
                plugin.getMessageManager().resolveConfigured(viewer, text));
        String format = plugin.getMessageManager().getRaw(viewer, "crossserver.chat-format")
                .replace("{server}", escapeMiniMessage(message.getServerName()))
                .replace("{channel}", escapeMiniMessage(localizedChannel.getName()))
                .replace("{player}", escapeMiniMessage(message.getSenderName()))
                .replace("{message}", CROSS_SERVER_MESSAGE_MARKER);
        Component trustedFormat = MessageUtil.parse(format, viewer);
        Component untrustedMessage = CrossServerChatCodec.decode(message.getMessage());
        return trustedFormat.replaceText(builder -> builder
                .matchLiteral(CROSS_SERVER_MESSAGE_MARKER)
                .replacement(untrustedMessage));
    }

    private void handlePrivateMessage(CrossServerMessage message) {
        // 跨服私聊尚未暴露发送入口；保留类型以兼容旧版协议。
    }

    private void handleBroadcastMessage(CrossServerMessage message) {
        String msg = message.getMessage();

        for (Consumer<String> listener : broadcastListeners) {
            try {
                listener.accept(msg);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("广播监听器处理异常: " + throwable.getMessage());
            }
        }

        // Premium 的内部协议由对应监听器消费。
        if (msg.startsWith("SHOUT:") || msg.startsWith("SHOUT2:") || msg.startsWith("CHATGAME:")) {
            return;
        }

        Map<String, String> placeholders = Map.of(
                "server", message.getServerName(),
                "message", msg
        );
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!gg.fotia.chat.api.PlayerPreferences.allows(player, "fotiachat", "announcement.regular", null)) continue;
            player.sendMessage(plugin.getMessageManager().get(
                    "crossserver.broadcast-format", player, placeholders));
        }
    }

    public void registerBroadcastListener(Consumer<String> listener) {
        if (listener != null && !broadcastListeners.contains(listener)) {
            broadcastListeners.add(listener);
        }
    }

    public void unregisterBroadcastListener(Consumer<String> listener) {
        broadcastListeners.remove(listener);
    }

    public void sendFormattedChatMessage(Channel channel, String formattedMessage) {
        sendFormattedChatMessage(channel, formattedMessage, null);
    }

    public void sendFormattedChatMessage(Channel channel, String formattedMessage, UUID senderUuid) {
        if (!enabled || channel == null || !channel.isCrossServerEnabled()) return;

        sendMessage(new CrossServerMessage(
                CrossServerMessage.TYPE_CHAT,
                serverName,
                senderUuid,
                "",
                channel.getId(),
                CrossServerChatCodec.encode(MessageUtil.parse(formattedMessage))
        ));
    }

    /**
     * 旧 API 的参数是原始消息，因此必须先转义再走正常格式化管线，
     * 不能让远端把玩家输入直接当 MiniMessage 解析。
     */
    public void sendChatMessage(Player player, Channel channel, String message) {
        sendPreparedChatMessage(player, channel, Component.text(message == null ? "" : message));
    }

    /** 发送经过聊天管线处理的组件，保留颜色与字体并移除仅本服可用的快照链接。 */
    public void sendPreparedChatMessage(Player player, Channel channel, Component message) {
        if (!enabled || player == null || channel == null || !channel.isCrossServerEnabled()) return;

        String safeMessage = CrossServerChatCodec.encode(message);
        sendMessage(new CrossServerMessage(
                CrossServerMessage.TYPE_CHAT,
                serverName,
                player.getUniqueId(),
                gg.fotia.basictool.bridge.PaperNickBridge.name("FotiaChat", player),
                channel.getId(),
                safeMessage
        ));
    }

    private String escapeMiniMessage(String message) {
        return message.replace("\\", "\\\\").replace("<", "\\<");
    }

    public void sendBroadcast(String message) {
        if (!enabled) return;

        sendMessage(new CrossServerMessage(
                CrossServerMessage.TYPE_BROADCAST,
                serverName,
                null,
                "Server",
                null,
                message
        ));
    }

    private void sendMessage(CrossServerMessage message) {
        if (type.equals("redis")) {
            redisHandler.sendMessage(message);
        } else {
            bungeeHandler.sendMessage(message);
        }
    }

    public void disable() {
        redisHandler.disable();
        bungeeHandler.disable();
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getServerName() {
        return serverName;
    }

    public String getType() {
        return type;
    }

    public Map<String, List<String>> getServerGroups() {
        return Map.copyOf(serverGroups);
    }
}

package gg.fotia.chat.crossserver;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.channel.Channel;
import gg.fotia.chat.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

/**
 * 跨服通信管理器
 */
public class CrossServerManager {

    private final FotiaChat plugin;
    private RedisHandler redisHandler;
    private BungeeHandler bungeeHandler;

    private boolean enabled = false;
    private String type = "bungeecord";
    private String serverName;
    private Map<String, List<String>> serverGroups = new HashMap<>();
    private final List<Consumer<String>> broadcastListeners = new ArrayList<>();

    public CrossServerManager(FotiaChat plugin) {
        this.plugin = plugin;
        this.redisHandler = new RedisHandler(plugin, this);
        this.bungeeHandler = new BungeeHandler(plugin, this);
    }

    /**
     * 加载配置并启用跨服通信
     */
    public void load() {
        // 先禁用现有连接
        disable();

        ConfigurationSection config = plugin.getConfigManager().getConfig()
                .getConfigurationSection("cross-server");

        if (config == null) {
            plugin.getLogger().info("跨服通信未配置，功能已禁用");
            return;
        }

        enabled = config.getBoolean("enabled", false);
        if (!enabled) {
            plugin.getLogger().info("跨服通信已禁用");
            return;
        }

        type = config.getString("type", "bungeecord").toLowerCase();
        serverName = config.getString("server-name", "server1");

        // 加载服务器组
        serverGroups.clear();
        ConfigurationSection groupsSection = config.getConfigurationSection("server-groups");
        if (groupsSection != null) {
            for (String groupName : groupsSection.getKeys(false)) {
                List<String> servers = groupsSection.getStringList(groupName);
                serverGroups.put(groupName, servers);
            }
        }

        // 设置消息处理器
        setupMessageHandler();

        // 启用对应的处理器
        if (type.equals("redis")) {
            String host = config.getString("redis.host", "localhost");
            int port = config.getInt("redis.port", 6379);
            String password = config.getString("redis.password", "");
            redisHandler.enable(host, port, password);
        } else {
            bungeeHandler.enable();
        }

        plugin.getLogger().info("跨服通信已启用 (类型: " + type + ", 服务器: " + serverName + ")");
    }

    /**
     * 设置消息处理器
     */
    private void setupMessageHandler() {
        redisHandler.setMessageHandler(this::handleIncomingMessage);
        bungeeHandler.setMessageHandler(this::handleIncomingMessage);
    }

    /**
     * 处理接收到的跨服消息
     */
    private void handleIncomingMessage(CrossServerMessage message) {
        switch (message.getType()) {
            case CrossServerMessage.TYPE_CHAT -> handleChatMessage(message);
            case CrossServerMessage.TYPE_PRIVATE -> handlePrivateMessage(message);
            case CrossServerMessage.TYPE_BROADCAST -> handleBroadcastMessage(message);
        }
    }

    /**
     * 处理聊天消息（接收已格式化的消息）
     */
    private void handleChatMessage(CrossServerMessage message) {
        String channelId = message.getChannelId();
        Channel channel = plugin.getChannelManager().getChannel(channelId);

        if (channel == null) {
            channel = plugin.getChannelManager().getDefaultChannel();
        }

        // 直接解析已格式化的消息
        Component component = MessageUtil.parse(message.getMessage());

        // 发送给所有有权限的玩家
        String permission = channel.getPermission();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (permission == null || permission.isEmpty() || player.hasPermission(permission)) {
                player.sendMessage(component);
            }
        }
    }

    /**
     * 处理私聊消息
     */
    private void handlePrivateMessage(CrossServerMessage message) {
        // 跨服私聊需要额外的目标玩家信息
        // 当前简化实现
    }

    /**
     * 处理广播消息
     */
    private void handleBroadcastMessage(CrossServerMessage message) {
        String msg = message.getMessage();

        // 通知所有监听器
        for (Consumer<String> listener : broadcastListeners) {
            try {
                listener.accept(msg);
            } catch (Exception e) {
                plugin.getLogger().warning("广播监听器处理异常: " + e.getMessage());
            }
        }

        // 如果是特殊消息（如SHOUT:），由监听器处理，不再广播
        if (msg.startsWith("SHOUT:") || msg.startsWith("CHATGAME:")) {
            return;
        }

        String format = plugin.getMessageManager().getRaw("crossserver.broadcast-format");
        format = format.replace("{server}", message.getServerName());
        format = format.replace("{message}", msg);

        Component component = MessageUtil.parse(format);
        Bukkit.broadcast(component);
    }

    /**
     * 注册广播消息监听器
     */
    public void registerBroadcastListener(Consumer<String> listener) {
        broadcastListeners.add(listener);
    }

    /**
     * 移除广播消息监听器
     */
    public void unregisterBroadcastListener(Consumer<String> listener) {
        broadcastListeners.remove(listener);
    }

    /**
     * 发送已格式化的聊天消息到其他服务器
     */
    public void sendFormattedChatMessage(Channel channel, String formattedMessage) {
        if (!enabled) return;

        CrossServerMessage crossMessage = new CrossServerMessage(
                CrossServerMessage.TYPE_CHAT,
                serverName,
                null,
                "",
                channel.getId(),
                formattedMessage
        );

        sendMessage(crossMessage);
    }

    /**
     * 发送聊天消息到其他服务器（旧方法，保留兼容）
     */
    public void sendChatMessage(Player player, Channel channel, String message) {
        if (!enabled) return;

        CrossServerMessage crossMessage = new CrossServerMessage(
                CrossServerMessage.TYPE_CHAT,
                serverName,
                player.getUniqueId(),
                player.getName(),
                channel.getId(),
                message
        );

        sendMessage(crossMessage);
    }

    /**
     * 发送广播消息到其他服务器
     */
    public void sendBroadcast(String message) {
        if (!enabled) return;

        CrossServerMessage crossMessage = new CrossServerMessage(
                CrossServerMessage.TYPE_BROADCAST,
                serverName,
                null,
                "Server",
                null,
                message
        );

        sendMessage(crossMessage);
    }

    /**
     * 发送消息
     */
    private void sendMessage(CrossServerMessage message) {
        if (type.equals("redis")) {
            redisHandler.sendMessage(message);
        } else {
            bungeeHandler.sendMessage(message);
        }
    }

    /**
     * 禁用跨服通信
     */
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
        return serverGroups;
    }
}

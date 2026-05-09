package gg.fotia.chat.api;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.channel.Channel;
import gg.fotia.chat.channel.ChannelManager;
import gg.fotia.chat.color.ColorManager;
import gg.fotia.chat.crossserver.CrossServerManager;
import gg.fotia.chat.filter.FilterManager;
import gg.fotia.chat.format.ChatFormatter;
import gg.fotia.chat.manager.ConfigManager;
import gg.fotia.chat.manager.MessageManager;
import gg.fotia.chat.menu.MenuManager;
import gg.fotia.chat.mute.MuteManager;
import gg.fotia.chat.privatemsg.PrivateMessageManager;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;

import java.util.UUID;

/**
 * FotiaChat API
 * 提供给Addon使用的API接口
 */
public class FotiaChatAPI {

    private static FotiaChat plugin;

    /**
     * 初始化API（由主插件调用）
     */
    public static void init(FotiaChat instance) {
        plugin = instance;
    }

    /**
     * 获取主插件实例
     */
    public static FotiaChat getPlugin() {
        return plugin;
    }

    /**
     * 获取配置管理器
     */
    public static ConfigManager getConfigManager() {
        return plugin.getConfigManager();
    }

    /**
     * 获取消息管理器
     */
    public static MessageManager getMessageManager() {
        return plugin.getMessageManager();
    }

    /**
     * 获取频道管理器
     */
    public static ChannelManager getChannelManager() {
        return plugin.getChannelManager();
    }

    /**
     * 获取聊天格式化器
     */
    public static ChatFormatter getChatFormatter() {
        return plugin.getChatFormatter();
    }

    /**
     * 获取虚拟发言者聊天分发器。
     */
    public static VirtualChatDispatcher getVirtualChatDispatcher() {
        return plugin.getVirtualChatDispatcher();
    }

    /**
     * 获取颜色管理器
     */
    public static ColorManager getColorManager() {
        return plugin.getColorManager();
    }

    /**
     * 获取过滤器管理器
     */
    public static FilterManager getFilterManager() {
        return plugin.getFilterManager();
    }

    /**
     * 获取禁言管理器
     */
    public static MuteManager getMuteManager() {
        return plugin.getMuteManager();
    }

    /**
     * 获取私聊管理器
     */
    public static PrivateMessageManager getPrivateMessageManager() {
        return plugin.getPrivateMessageManager();
    }

    /**
     * 获取跨服管理器
     */
    public static CrossServerManager getCrossServerManager() {
        return plugin.getCrossServerManager();
    }

    /**
     * 获取菜单管理器
     */
    public static MenuManager getMenuManager() {
        return plugin.getMenuManager();
    }

    /**
     * 获取Addon管理器
     */
    public static AddonManager getAddonManager() {
        return plugin.getAddonManager();
    }

    /**
     * 注册公共聊天观察者。
     */
    public static void registerPublicChatObserver(PublicChatObserver observer) {
        plugin.registerPublicChatObserver(observer);
    }

    /**
     * 注销公共聊天观察者。
     */
    public static void unregisterPublicChatObserver(PublicChatObserver observer) {
        plugin.unregisterPublicChatObserver(observer);
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取玩家当前频道
     */
    public static Channel getPlayerChannel(Player player) {
        return plugin.getChannelManager().getPlayerChannel(player);
    }

    /**
     * 设置玩家频道
     */
    public static void setPlayerChannel(Player player, Channel channel) {
        plugin.getChannelManager().setPlayerChannel(player, channel.getId());
    }

    /**
     * 格式化聊天消息
     */
    public static Component formatMessage(Player player, Channel channel, String message) {
        return plugin.getChatFormatter().format(player, channel, message);
    }

    /**
     * 格式化虚拟发言者聊天消息。
     */
    public static Component formatVirtualMessage(VirtualChatSender sender, Channel channel, String message) {
        return plugin.getChatFormatter().format(sender, channel, message);
    }

    /**
     * 将虚拟发言者聊天消息按 FotiaChat 规则分发。
     */
    public static boolean dispatchVirtualChat(VirtualChatSender sender, String channelId, String message) {
        return plugin.getVirtualChatDispatcher().dispatchPublicChat(sender, channelId, message);
    }

    /**
     * 将虚拟发言者聊天消息发送到默认频道。
     */
    public static boolean dispatchVirtualChat(VirtualChatSender sender, String message) {
        return plugin.getVirtualChatDispatcher().dispatchPublicChat(sender, message);
    }

    /**
     * 发送跨服消息
     */
    public static void sendCrossServerMessage(Player player, Channel channel, String message) {
        if (plugin.getCrossServerManager().isEnabled()) {
            plugin.getCrossServerManager().sendChatMessage(player, channel, message);
        }
    }

    /**
     * 发送跨服广播
     */
    public static void sendCrossServerBroadcast(String message) {
        if (plugin.getCrossServerManager().isEnabled()) {
            plugin.getCrossServerManager().sendBroadcast(message);
        }
    }

    /**
     * 注册虚拟私聊目标提供者。
     */
    public static void registerVirtualPrivateMessageProvider(VirtualPrivateMessageProvider provider) {
        plugin.getPrivateMessageManager().registerVirtualProvider(provider);
    }

    /**
     * 注销虚拟私聊目标提供者。
     */
    public static void unregisterVirtualPrivateMessageProvider(VirtualPrivateMessageProvider provider) {
        plugin.getPrivateMessageManager().unregisterVirtualProvider(provider);
    }

    /**
     * 由虚拟目标向真人玩家发送私聊。
     */
    public static boolean sendVirtualPrivateMessageToPlayer(String senderName,
                                                            String senderDisplayName,
                                                            UUID senderUuid,
                                                            Player target,
                                                            String message) {
        return plugin.getPrivateMessageManager().sendVirtualMessageToPlayer(
                senderName,
                senderDisplayName,
                senderUuid,
                target,
                message
        );
    }

    /**
     * 检查玩家是否被禁言
     */
    public static boolean isMuted(Player player) {
        return plugin.getMuteManager().isMuted(player);
    }

    /**
     * 发送本地化消息给玩家
     */
    public static void sendMessage(Player player, String key) {
        plugin.getMessageManager().send(player, key);
    }

    /**
     * 发送本地化消息给玩家（带占位符）
     */
    public static void sendMessage(Player player, String key, java.util.Map<String, String> placeholders) {
        plugin.getMessageManager().send(player, key, placeholders);
    }
}

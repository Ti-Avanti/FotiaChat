package gg.fotia.chat.listener;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.channel.Channel;
import gg.fotia.chat.channel.ChannelManager;
import gg.fotia.chat.format.ChatFormatter;
import gg.fotia.chat.util.MessageUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Set;

/**
 * 聊天监听器
 */
public class ChatListener implements Listener {

    private final FotiaChat plugin;
    private final ChannelManager channelManager;
    private final ChatFormatter chatFormatter;

    public ChatListener(FotiaChat plugin) {
        this.plugin = plugin;
        this.channelManager = plugin.getChannelManager();
        this.chatFormatter = plugin.getChatFormatter();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // 取消原始事件，避免[Not Secure]标记
        event.setCancelled(true);

        Player player = event.getPlayer();
        String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.message());

        // 检查禁言状态
        if (plugin.getMuteManager().isMuted(player)) {
            plugin.getMessageManager().send(player, "chat.muted");
            return;
        }

        // 检查快捷方式
        Channel channel = checkShortcut(message);
        if (channel != null) {
            // 移除快捷方式前缀
            message = message.substring(channel.getShortcut().length()).trim();
            if (message.isEmpty()) {
                plugin.getMessageManager().send(player, "chat.empty-message");
                return;
            }
        } else {
            // 使用玩家当前频道
            channel = channelManager.getPlayerChannel(player);
        }

        // 检查频道权限
        if (!channelManager.hasChannelPermission(player, channel)) {
            plugin.getMessageManager().send(player, "channel.no-permission");
            return;
        }

        // 屏蔽词过滤
        if (!player.hasPermission("fotiachat.filter.bypass")) {
            String filtered = plugin.getFilterManager().filter(message);
            if (filtered == null) {
                plugin.getMessageManager().send(player, "filter.blocked");
                return;
            }
            message = filtered;
        }

        // 应用玩家颜色
        message = plugin.getColorManager().applyPlayerColor(player, message);

        // 格式化消息
        final String finalMessage = message;
        final Channel finalChannel = channel;
        Component formattedMessage = chatFormatter.format(player, finalChannel, finalMessage);

        // 获取接收者
        Set<Player> recipients;
        if (channel.isLocalChannel()) {
            recipients = getLocalRecipients(player, channel.getRadius());
        } else {
            recipients = new HashSet<>(Bukkit.getOnlinePlayers());
        }

        // 发送消息给所有接收者
        for (Player recipient : recipients) {
            recipient.sendMessage(formattedMessage);
        }

        // 发送到控制台
        Bukkit.getConsoleSender().sendMessage(formattedMessage);

        // 发送跨服消息（传输已格式化的消息）
        if (plugin.getCrossServerManager().isEnabled()) {
            // 为跨服消息单独格式化（物品展示转为纯文本）
            String crossServerMessage = finalMessage;
            if (plugin.getItemDisplayManager() != null && plugin.getItemDisplayManager().containsPlaceholder(finalMessage)) {
                crossServerMessage = plugin.getItemDisplayManager().processMessageForCrossServer(player, finalMessage);
            }
            Component crossServerFormatted = chatFormatter.format(player, finalChannel, crossServerMessage);
            String formattedString = MessageUtil.toMiniMessage(crossServerFormatted);
            plugin.getCrossServerManager().sendFormattedChatMessage(finalChannel, formattedString);
        }
    }

    /**
     * 检查消息是否使用了快捷方式
     */
    private Channel checkShortcut(String message) {
        for (Channel channel : channelManager.getAllChannels()) {
            String shortcut = channel.getShortcut();
            if (shortcut != null && !shortcut.isEmpty() && message.startsWith(shortcut)) {
                return channel;
            }
        }
        return null;
    }

    /**
     * 获取范围内的玩家
     */
    private Set<Player> getLocalRecipients(Player sender, int radius) {
        Set<Player> recipients = new HashSet<>();
        Location senderLoc = sender.getLocation();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(senderLoc.getWorld())) {
                if (player.getLocation().distance(senderLoc) <= radius) {
                    recipients.add(player);
                }
            }
        }

        return recipients;
    }
}

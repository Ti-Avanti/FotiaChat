package gg.fotia.chat.api;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.channel.Channel;
import gg.fotia.chat.ignore.IgnoreManager;
import gg.fotia.chat.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * 为其他插件提供统一的虚拟发言者聊天分发入口。
 */
public class VirtualChatDispatcher {

    private final FotiaChat plugin;
    private final IgnoreManager ignoreManager;

    public VirtualChatDispatcher(FotiaChat plugin) {
        this.plugin = plugin;
        this.ignoreManager = plugin.getIgnoreManager();
    }

    public boolean dispatchPublicChat(VirtualChatSender sender, String message) {
        return dispatchPublicChat(sender, null, message);
    }

    public boolean dispatchPublicChat(VirtualChatSender sender, String channelId, String message) {
        if (sender == null) {
            return false;
        }

        String finalMessage = message == null ? "" : message.trim();
        if (finalMessage.isEmpty()) {
            return false;
        }

        Channel channel = resolveChannel(channelId);
        if (!sender.isBypassFilter()) {
            String filtered = plugin.getFilterManager().filter(finalMessage);
            if (filtered == null || filtered.trim().isEmpty()) {
                return false;
            }
            finalMessage = filtered;
        }

        Component formattedMessage = plugin.getChatFormatter().format(sender, channel, finalMessage);
        for (Player recipient : collectRecipients(sender, channel)) {
            if (!ignoreManager.isIgnoring(recipient.getUniqueId(), sender.getUniqueId())) {
                recipient.sendMessage(formattedMessage);
            }
        }

        Bukkit.getConsoleSender().sendMessage(formattedMessage);

        if (plugin.getCrossServerManager().isEnabled()) {
            plugin.getCrossServerManager().sendFormattedChatMessage(channel, MessageUtil.toMiniMessage(formattedMessage));
        }
        return true;
    }

    private Channel resolveChannel(String channelId) {
        if (channelId != null && !channelId.isBlank()) {
            Channel configured = plugin.getChannelManager().getChannel(channelId.trim());
            if (configured != null) {
                return configured;
            }
        }
        return plugin.getChannelManager().getDefaultChannel();
    }

    private Set<Player> collectRecipients(VirtualChatSender sender, Channel channel) {
        if (!channel.isLocalChannel()) {
            return new HashSet<>(Bukkit.getOnlinePlayers());
        }

        Location source = sender.getLocation();
        if (source == null) {
            return new HashSet<>(Bukkit.getOnlinePlayers());
        }

        Set<Player> recipients = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(source.getWorld()) && player.getLocation().distance(source) <= channel.getRadius()) {
                recipients.add(player);
            }
        }
        return recipients;
    }
}

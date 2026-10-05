package gg.fotia.chat.listener;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.channel.Channel;
import gg.fotia.chat.channel.ChannelManager;
import gg.fotia.chat.color.ChatColor;
import gg.fotia.chat.format.ChatFormatter;
import gg.fotia.chat.format.CraftEngineHandler;
import gg.fotia.chat.ignore.IgnoreManager;
import gg.fotia.chat.mention.MentionManager;
import gg.fotia.chat.mention.MentionPlan;
import gg.fotia.chat.util.LegacyColorConverter;
import gg.fotia.chat.util.ComponentTextTransformer;
import io.papermc.paper.event.player.AsyncChatDecorateEvent;
import io.papermc.paper.event.player.ChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chat listener.
 *
 * 完整聊天管线会访问 Bukkit 玩家状态和第三方插件 API，因此统一在服务器主线程执行。
 */
public class ChatListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final String INLINE_COLOR_PERMISSION = "fotiachat.color.format";
    private static final String IMAGE_PERMISSION = "fotiachat.image";

    private final FotiaChat plugin;
    private final ChannelManager channelManager;
    private final ChatFormatter chatFormatter;
    private final IgnoreManager ignoreManager;
    private final MentionManager mentionManager;

    public ChatListener(FotiaChat plugin) {
        this.plugin = plugin;
        this.channelManager = plugin.getChannelManager();
        this.chatFormatter = plugin.getChatFormatter();
        this.ignoreManager = plugin.getIgnoreManager();
        this.mentionManager = new MentionManager(plugin);
    }

    /**
     * 图片权限在装饰阶段执行：无权限玩家的 <image:...> 标签在 CraftEngine
     * （MONITOR 优先级装饰监听）转换为字形之前被移除。
     */
    @SuppressWarnings("UnstableApiUsage")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatDecorate(AsyncChatDecorateEvent event) {
        Player player = event.player();
        if (player == null || player.hasPermission(IMAGE_PERMISSION)) {
            return;
        }
        Component result = event.result();
        if (!CraftEngineHandler.containsImageTag(PLAIN_TEXT.serialize(result))) {
            return;
        }
        event.result(CraftEngineHandler.stripImageTags(result));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(ChatEvent event) {
        // cancel vanilla chat to avoid [Not Secure] marker
        event.setCancelled(true);

        Player player = event.getPlayer();
        Component originalMessageComponent = event.message();
        Component workingMessageComponent = originalMessageComponent;
        String message = PLAIN_TEXT.serialize(originalMessageComponent);

        if (plugin.getMuteManager().isMuted(player)) {
            plugin.getMessageManager().send(player, "chat.muted");
            return;
        }

        Channel channel = checkShortcut(message);
        if (channel != null) {
            int contentStart = channel.getShortcut().length();
            while (contentStart < message.length() && Character.isWhitespace(message.charAt(contentStart))) {
                contentStart++;
            }
            int contentEnd = message.length();
            while (contentEnd > contentStart && Character.isWhitespace(message.charAt(contentEnd - 1))) {
                contentEnd--;
            }
            workingMessageComponent = ComponentTextTransformer.slice(
                    workingMessageComponent,
                    contentStart,
                    contentEnd
            );
            message = message.substring(contentStart, contentEnd);
            if (message.isEmpty()) {
                plugin.getMessageManager().send(player, "chat.empty-message");
                return;
            }
        } else {
            channel = channelManager.getPlayerChannel(player);
        }

        if (channel == null) {
            // 未配置任何频道时直接放弃处理，避免 NPE
            return;
        }

        if (!channelManager.hasChannelPermission(player, channel)) {
            plugin.getMessageManager().send(player, "channel.no-permission");
            return;
        }

        if (!player.hasPermission("fotiachat.filter.bypass")) {
            if (ComponentTextTransformer.containsFont(workingMessageComponent)) {
                Component filtered = plugin.getFilterManager().filter(workingMessageComponent);
                if (filtered == null) {
                    plugin.getMessageManager().send(player, "filter.blocked");
                    return;
                }
                workingMessageComponent = filtered;
                message = PLAIN_TEXT.serialize(filtered);
            } else {
                String filtered = plugin.getFilterManager().filter(message);
                if (filtered == null) {
                    plugin.getMessageManager().send(player, "filter.blocked");
                    return;
                }
                message = filtered;
            }
        }

        if (plugin.interceptPublicChat(player, channel, message)) {
            return;
        }

        final Channel finalChannel = channel;
        final String observedMessage = message;
        final boolean preserveIncomingComponent = shouldPreserveIncomingComponent(workingMessageComponent);

        final Component preparedMessage;
        final Component defaultFormattedMessage;

        ChatColor chatColor = plugin.getColorManager().getPlayerColor(player);
        if (preserveIncomingComponent) {
            preparedMessage = chatFormatter.prepareMessage(player, workingMessageComponent, chatColor);
        } else {
            boolean allowInlineColors = plugin.getConfigManager().isAllowColorCodes()
                    && player.hasPermission(INLINE_COLOR_PERMISSION);
            if (!allowInlineColors) {
                message = escapeMiniMessageTags(message);
            }
            if (allowInlineColors) {
                message = LegacyColorConverter.convertToMiniMessage(message);
            }
            preparedMessage = chatFormatter.prepareMessage(player, message, chatColor);
        }
        if (plugin.interceptRoutedChat(player, finalChannel, observedMessage, preparedMessage)) {
            return;
        }
        List<Player> onlinePlayers = List.copyOf(Bukkit.getOnlinePlayers());
        MentionPlan mentions = mentionManager.prepare(player, preparedMessage, onlinePlayers);
        MentionManager.Result mentionResult = mentionManager.render(player, mentions, null);
        defaultFormattedMessage = chatFormatter.formatPreparedMessage(
                player,
                finalChannel,
                mentionResult.component(),
                null
        );

        Set<Player> recipients;
        if (channel.isLocalChannel()) {
            recipients = getLocalRecipients(player, channel.getRadius());
        } else {
            recipients = new HashSet<>(onlinePlayers);
        }

        // 同一条消息的发送者上下文相同，仅按接收者语言渲染一次。
        Map<String, Component> localizedMessages = new HashMap<>();
        localizedMessages.put(plugin.getMessageManager().getLocale(null), defaultFormattedMessage);
        for (Player recipient : recipients) {
            if (channelManager.hasChannelPermission(recipient, finalChannel)
                    && gg.fotia.chat.api.PlayerPreferences.allows(recipient, "fotiachat", "chat.public", player.getUniqueId())
                    && !ignoreManager.isIgnoring(recipient.getUniqueId(), player.getUniqueId())) {
                String locale = plugin.getMessageManager().getLocale(recipient);
                Component localizedMessage = localizedMessages.computeIfAbsent(locale, ignored ->
                        chatFormatter.formatPreparedMessage(player, finalChannel,
                                mentionManager.render(player, mentions, recipient).component(), recipient));
                recipient.sendMessage(gg.fotia.chat.api.PlayerPreferences.display(recipient, localizedMessage));
                if (mentionResult.mentionedPlayers().contains(recipient.getUniqueId())
                        && gg.fotia.chat.api.PlayerPreferences.allows(recipient, "fotiachat", "chat.mention", player.getUniqueId())) {
                    mentionManager.notify(recipient);
                }
            }
        }

        Bukkit.getConsoleSender().sendMessage(defaultFormattedMessage);
        plugin.notifyPublicChatObservers(player, finalChannel, observedMessage, defaultFormattedMessage);

        // 本地/范围频道不参与跨服转发，避免"附近聊天"泄漏到其他服务器
        if (plugin.getCrossServerManager().isEnabled() && finalChannel.isCrossServerEnabled()) {
            plugin.getCrossServerManager().sendPreparedChatMessage(player, finalChannel, preparedMessage);
        }
    }

    private boolean shouldPreserveIncomingComponent(Component messageComponent) {
        return ComponentTextTransformer.containsFont(messageComponent);
    }

    private String escapeMiniMessageTags(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        return message.replace("\\", "\\\\").replace("<", "\\<");
    }

    private Channel checkShortcut(String message) {
        for (Channel channel : channelManager.getAllChannels()) {
            String shortcut = channel.getShortcut();
            if (shortcut != null && !shortcut.isEmpty() && message.startsWith(shortcut)) {
                return channel;
            }
        }
        return null;
    }

    private Set<Player> getLocalRecipients(Player sender, int radius) {
        Set<Player> recipients = new HashSet<>();
        Location senderLoc = sender.getLocation();
        double radiusSquared = (double) radius * radius;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(senderLoc.getWorld())) {
                if (player.getLocation().distanceSquared(senderLoc) <= radiusSquared) {
                    recipients.add(player);
                }
            }
        }

        return recipients;
    }
}

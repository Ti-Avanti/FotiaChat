package gg.fotia.chat.listener;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.channel.Channel;
import gg.fotia.chat.channel.ChannelManager;
import gg.fotia.chat.format.ChatFormatter;
import gg.fotia.chat.ignore.IgnoreManager;
import gg.fotia.chat.util.LegacyColorConverter;
import gg.fotia.chat.util.MessageUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Set;

/**
 * Chat listener.
 */
public class ChatListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final FotiaChat plugin;
    private final ChannelManager channelManager;
    private final ChatFormatter chatFormatter;
    private final IgnoreManager ignoreManager;

    public ChatListener(FotiaChat plugin) {
        this.plugin = plugin;
        this.channelManager = plugin.getChannelManager();
        this.chatFormatter = plugin.getChatFormatter();
        this.ignoreManager = plugin.getIgnoreManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // cancel vanilla chat to avoid [Not Secure] marker
        event.setCancelled(true);

        Player player = event.getPlayer();
        Component originalMessageComponent = event.message();
        String message = PLAIN_TEXT.serialize(originalMessageComponent);

        boolean messageTextChanged = false;
        boolean shortcutUsed = false;

        if (plugin.getMuteManager().isMuted(player)) {
            plugin.getMessageManager().send(player, "chat.muted");
            return;
        }

        Channel channel = checkShortcut(message);
        if (channel != null) {
            message = message.substring(channel.getShortcut().length()).trim();
            shortcutUsed = true;
            messageTextChanged = true;
            if (message.isEmpty()) {
                plugin.getMessageManager().send(player, "chat.empty-message");
                return;
            }
        } else {
            channel = channelManager.getPlayerChannel(player);
        }

        if (!channelManager.hasChannelPermission(player, channel)) {
            plugin.getMessageManager().send(player, "channel.no-permission");
            return;
        }

        if (!player.hasPermission("fotiachat.filter.bypass")) {
            String beforeFilter = message;
            String filtered = plugin.getFilterManager().filter(message);
            if (filtered == null) {
                plugin.getMessageManager().send(player, "filter.blocked");
                return;
            }
            message = filtered;
            if (!message.equals(beforeFilter)) {
                messageTextChanged = true;
            }
        }

        final Channel finalChannel = channel;
        final String observedMessage = message;
        final boolean preserveIncomingComponent = shouldPreserveIncomingComponent(originalMessageComponent)
                && !shortcutUsed
                && !messageTextChanged;

        final String finalMessage;
        final Component formattedMessage;

        if (preserveIncomingComponent) {
            finalMessage = message;
            formattedMessage = chatFormatter.format(player, finalChannel, originalMessageComponent);
        } else {
            message = plugin.getColorManager().applyPlayerColor(player, message);
            message = LegacyColorConverter.convertToMiniMessage(message);
            finalMessage = message;
            formattedMessage = chatFormatter.format(player, finalChannel, finalMessage);
        }

        Set<Player> recipients;
        if (channel.isLocalChannel()) {
            recipients = getLocalRecipients(player, channel.getRadius());
        } else {
            recipients = new HashSet<>(Bukkit.getOnlinePlayers());
        }

        for (Player recipient : recipients) {
            if (!ignoreManager.isIgnoring(recipient.getUniqueId(), player.getUniqueId())) {
                recipient.sendMessage(formattedMessage);
            }
        }

        Bukkit.getConsoleSender().sendMessage(formattedMessage);
        plugin.notifyPublicChatObservers(player, finalChannel, observedMessage, formattedMessage);

        if (plugin.getCrossServerManager().isEnabled()) {
            String crossServerMessage = preserveIncomingComponent ? PLAIN_TEXT.serialize(originalMessageComponent) : finalMessage;
            if (plugin.getItemDisplayManager() != null && plugin.getItemDisplayManager().containsPlaceholder(finalMessage)) {
                crossServerMessage = plugin.getItemDisplayManager().processMessageForCrossServer(player, finalMessage);
            }
            Component crossServerFormatted = chatFormatter.format(player, finalChannel, crossServerMessage);
            String formattedString = MessageUtil.toMiniMessage(crossServerFormatted);
            plugin.getCrossServerManager().sendFormattedChatMessage(finalChannel, formattedString);
        }
    }

    private boolean shouldPreserveIncomingComponent(Component messageComponent) {
        return messageComponent != null && containsFontStyle(messageComponent);
    }

    private boolean containsFontStyle(Component component) {
        if (component.style().font() != null) {
            return true;
        }
        for (Component child : component.children()) {
            if (containsFontStyle(child)) {
                return true;
            }
        }
        return false;
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

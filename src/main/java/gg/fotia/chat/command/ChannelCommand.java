package gg.fotia.chat.command;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.channel.Channel;
import gg.fotia.chat.channel.ChannelManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 频道命令
 */
public class ChannelCommand implements CommandExecutor, TabCompleter {

    private final FotiaChat plugin;
    private final ChannelManager channelManager;

    public ChannelCommand(FotiaChat plugin) {
        this.plugin = plugin;
        this.channelManager = plugin.getChannelManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        if (args.length == 0) {
            // 显示当前频道
            Channel current = channelManager.getPlayerChannel(player);
            plugin.getMessageManager().send(player, "channel.current",
                    Map.of("channel", current.getName()));
            return true;
        }

        String channelId = args[0].toLowerCase();

        // 检查频道是否存在
        if (!channelManager.channelExists(channelId)) {
            plugin.getMessageManager().send(player, "channel.not-found",
                    Map.of("channel", channelId));
            return true;
        }

        Channel channel = channelManager.getChannel(channelId);

        // 检查权限
        if (!channelManager.hasChannelPermission(player, channel)) {
            plugin.getMessageManager().send(player, "channel.no-permission");
            return true;
        }

        // 切换频道
        channelManager.setPlayerChannel(player, channelId);
        plugin.getMessageManager().send(player, "channel.switched",
                Map.of("channel", channel.getName()));

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            String input = args[0].toLowerCase();
            return channelManager.getAllChannels().stream()
                    .filter(channel -> channelManager.hasChannelPermission(player, channel))
                    .map(Channel::getId)
                    .filter(id -> id.startsWith(input))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}

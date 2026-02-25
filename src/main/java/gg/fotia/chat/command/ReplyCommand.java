package gg.fotia.chat.command;

import gg.fotia.chat.FotiaChat;
import gg.fotia.chat.privatemsg.PrivateMessageManager;
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

/**
 * 回复命令
 * /reply <消息>
 */
public class ReplyCommand implements CommandExecutor, TabCompleter {

    private final FotiaChat plugin;
    private final PrivateMessageManager privateMessageManager;

    public ReplyCommand(FotiaChat plugin) {
        this.plugin = plugin;
        this.privateMessageManager = plugin.getPrivateMessageManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        if (!player.hasPermission("fotiachat.command.reply")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return true;
        }

        if (args.length < 1) {
            plugin.getMessageManager().send(player, "general.invalid-args",
                    Map.of("usage", "/reply <消息>"));
            return true;
        }

        // 拼接消息
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) messageBuilder.append(" ");
            messageBuilder.append(args[i]);
        }
        String message = messageBuilder.toString();

        privateMessageManager.reply(player, message);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        return new ArrayList<>();
    }
}
